package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerHealthText;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** HTTP {@code /metrics} and {@code /health} when {@code EXPORT_REPLAY_METRICS_PORT} is set. */
final class ExportReplayMetricsHttpServer implements AutoCloseable {

    @FunctionalInterface
    interface HealthProbe {
        boolean ready();
    }

    private final HttpServer server;

    private ExportReplayMetricsHttpServer(HttpServer server) {
        this.server = server;
    }

    static ExportReplayMetricsHttpServer start(int port, HealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static ExportReplayMetricsHttpServer start(int port, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        ExportReplayMetrics.registerBuildInfoOnce();
        var server = HttpServer.create(new InetSocketAddress(port), 3);
        server.createContext("/metrics", new HTTPServer.HTTPMetricHandler(CollectorRegistry.defaultRegistry));
        server.createContext("/health", exchange -> handleHealth(exchange, probe, messages));
        server.setExecutor(Executors.newFixedThreadPool(3, runnable -> {
            var t = new Thread(runnable, "export-replay-metrics-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return new ExportReplayMetricsHttpServer(server);
    }

    private static void handleHealth(HttpExchange exchange, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        var ready = probe.ready();
        var status = ready ? 200 : 503;
        var body = ready ? WorkerHealthText.ok(messages) : WorkerHealthText.notReady(messages);
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
