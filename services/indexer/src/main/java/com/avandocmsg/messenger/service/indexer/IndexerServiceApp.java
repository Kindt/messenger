package com.avandocmsg.messenger.service.indexer;

import com.avandocmsg.messenger.common.hotplug.GracefulShutdown;
import com.avandocmsg.messenger.common.hotplug.HotPlugHeartbeat;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Indexer extracted-service skeleton:
 * - connects to existing NATS bus
 * - emits hot-plug heartbeat
 * - exposes /health, /ready, /metrics
 */
public final class IndexerServiceApp {

    enum ServiceState {
        INIT,
        ACTIVE,
        DRAINING,
        STOPPED
    }

    private static final String ENV_SERVICE_ID = "SERVICE_ID";
    private static final String ENV_SERVICE_HTTP_PORT = "SERVICE_HTTP_PORT";
    private static final String ENV_HEARTBEAT_INTERVAL_MS = "SERVICE_HEARTBEAT_INTERVAL_MS";
    private static final String ENV_DRAIN_TIMEOUT_MS = "SERVICE_DRAIN_TIMEOUT_MS";
    private static final String ENV_NATS_URL = "NATS_URL";

    private IndexerServiceApp() {
    }

    public static void main(String[] args) throws Exception {
        var serviceId = env(ENV_SERVICE_ID, "indexer-service");
        var httpPort = envInt(ENV_SERVICE_HTTP_PORT, 9090);
        var heartbeatIntervalMs = envLong(ENV_HEARTBEAT_INTERVAL_MS, 10_000L);
        var drainTimeoutMs = envLong(ENV_DRAIN_TIMEOUT_MS, 30_000L);
        var natsUrl = env(ENV_NATS_URL, "nats://127.0.0.1:4222");

        var state = new AtomicReference<>(ServiceState.INIT);
        var stopLatch = new CountDownLatch(1);

        var natsOptions = new Options.Builder().server(natsUrl).build();
        var nats = Nats.connect(natsOptions);
        var heartbeat = new HotPlugHeartbeat(nats, serviceId, heartbeatIntervalMs);
        heartbeat.start();

        var httpServer = createHttpServer(httpPort, serviceId, state);
        httpServer.start();

        // Placeholders for US3 wiring: queue-group consumer of msg.event.index.
        nats.subscribe(NatsSubjects.MSG_EVENT_INDEX);

        state.set(ServiceState.ACTIVE);
        heartbeat.publish("ACTIVE");

        GracefulShutdown.register(
            serviceId,
            nats,
            Duration.ofMillis(drainTimeoutMs),
            () -> {
                state.set(ServiceState.DRAINING);
                heartbeat.publish("DRAINING");
            },
            () -> {
                state.set(ServiceState.STOPPED);
                heartbeat.close();
                httpServer.stop(0);
                stopLatch.countDown();
            }
        );

        stopLatch.await();
    }

    static HttpServer createHttpServer(int port, String serviceId, AtomicReference<ServiceState> state) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/health", exchange -> {
            var payload = "{\"status\":\"UP\",\"serviceId\":\"" + serviceId + "\"}";
            writeJson(exchange, 200, payload);
        });
        server.createContext("/ready", exchange -> {
            var ready = state.get() == ServiceState.ACTIVE;
            var code = ready ? 200 : 503;
            var payload = ready
                ? "{\"status\":\"READY\",\"serviceId\":\"" + serviceId + "\"}"
                : "{\"status\":\"NOT_READY\",\"serviceId\":\"" + serviceId + "\"}";
            writeJson(exchange, code, payload);
        });
        server.createContext("/metrics", exchange -> {
            String payload = scrapeMetrics();
            writeText(exchange, 200, payload, TextFormat.CONTENT_TYPE_004);
        });
        return server;
    }

    static String scrapeMetrics() throws IOException {
        var writer = new StringWriter();
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        return writer.toString();
    }

    private static void writeJson(HttpExchange exchange, int code, String payload) throws IOException {
        writeText(exchange, code, payload, "application/json; charset=utf-8");
    }

    private static void writeText(HttpExchange exchange, int code, String payload, String contentType) throws IOException {
        var bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String env(String key, String defaultValue) {
        var value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static int envInt(String key, int defaultValue) {
        var raw = env(key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long envLong(String key, long defaultValue) {
        var raw = env(key, Long.toString(defaultValue));
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
