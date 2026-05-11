package com.avandocmsg.messenger.worker.retention;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Single {@link HttpServer} for Prometheus {@code /metrics} and readiness {@code /health}.
 */
final class RetentionMetricsHttpServer implements AutoCloseable {

    private final HttpServer server;

    private RetentionMetricsHttpServer(HttpServer server) {
        this.server = server;
    }

    static RetentionMetricsHttpServer start(int port, RetentionHealthProbe probe) throws IOException {
        RetentionMetrics.registerBuildInfoOnce();
        var server = HttpServer.create(new InetSocketAddress(port), 3);
        server.createContext("/metrics", new HTTPServer.HTTPMetricHandler(CollectorRegistry.defaultRegistry));
        server.createContext("/health", exchange -> handleHealth(exchange, probe));
        server.setExecutor(Executors.newFixedThreadPool(5, runnable -> {
            var t = new Thread(runnable, "retention-metrics-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return new RetentionMetricsHttpServer(server);
    }

    private static void handleHealth(HttpExchange exchange, RetentionHealthProbe probe) throws IOException {
        var ready = probe.ready();
        var status = ready ? 200 : 503;
        var body = ready ? "ok" : "not ready";
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
