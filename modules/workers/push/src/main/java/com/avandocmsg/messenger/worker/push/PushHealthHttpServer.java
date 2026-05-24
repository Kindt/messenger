package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerHealthText;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Readiness HTTP endpoint {@code GET /health} for orchestrator probes.
 */
final class PushHealthHttpServer implements AutoCloseable {

    private final HttpServer server;

    private PushHealthHttpServer(HttpServer server) {
        this.server = server;
    }

    static PushHealthHttpServer start(int port, PushReadinessCheck probe) throws IOException {
        return start(port, probe, null);
    }

    static PushHealthHttpServer start(int port, PushReadinessCheck probe, UserMessageSource messages)
            throws IOException {
        var bindPort = port > 0 ? port : 0;
        var server = HttpServer.create(new InetSocketAddress(bindPort), 3);
        server.createContext("/health", exchange -> handleHealth(exchange, probe, messages));
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            var t = new Thread(runnable, "push-health-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return new PushHealthHttpServer(server);
    }

    private static void handleHealth(HttpExchange exchange, PushReadinessCheck probe, UserMessageSource messages)
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
