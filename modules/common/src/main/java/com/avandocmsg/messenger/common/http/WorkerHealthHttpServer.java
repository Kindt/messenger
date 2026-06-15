package com.avandocmsg.messenger.common.http;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/**
 * Minimal worker HTTP server: {@code /health} and optional Prometheus {@code /metrics}.
 */
public final class WorkerHealthHttpServer implements AutoCloseable {

    private final HttpServer server;

    private WorkerHealthHttpServer(HttpServer server) {
        this.server = server;
    }

    public static WorkerHealthHttpServer startHealthOnly(
            int port,
            String threadNamePrefix,
            BooleanSupplier ready,
            UserMessageSource messages) throws IOException {
        var bindPort = port > 0 ? port : 0;
        var server = HttpServer.create(new InetSocketAddress(bindPort), 3);
        server.createContext("/health", exchange -> WorkerHealthResponses.write(exchange, ready, messages));
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> daemonThread(threadNamePrefix, runnable)));
        server.start();
        return new WorkerHealthHttpServer(server);
    }

    public static WorkerHealthHttpServer startWithMetrics(
            int port,
            String threadNamePrefix,
            BooleanSupplier ready,
            UserMessageSource messages,
            Runnable registerBuildInfo) throws IOException {
        if (registerBuildInfo != null) {
            registerBuildInfo.run();
        }
        var server = HttpServer.create(new InetSocketAddress(port), 3);
        server.createContext("/metrics", new HTTPServer.HTTPMetricHandler(CollectorRegistry.defaultRegistry));
        server.createContext("/health", exchange -> WorkerHealthResponses.write(exchange, ready, messages));
        var poolSize = 3;
        server.setExecutor(Executors.newFixedThreadPool(poolSize, runnable -> daemonThread(threadNamePrefix, runnable)));
        server.start();
        return new WorkerHealthHttpServer(server);
    }

    private static Thread daemonThread(String prefix, Runnable runnable) {
        var t = new Thread(runnable, prefix);
        t.setDaemon(true);
        return t;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
