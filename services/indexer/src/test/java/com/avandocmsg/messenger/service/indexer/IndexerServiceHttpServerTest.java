package com.avandocmsg.messenger.service.indexer;

import com.avandocmsg.messenger.common.hotplug.HotPlugMetrics;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexerServiceHttpServerTest {

    @Test
    void healthAndReadyEndpointsReflectState() throws Exception {
        var state = new AtomicReference<>(IndexerServiceApp.ServiceState.INIT);
        try (var server = startServer("indexer-test", state)) {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var health = get(client, server.port(), "/health");
            var readyInit = get(client, server.port(), "/ready");

            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"UP\""));
            assertEquals(503, readyInit.statusCode());

            state.set(IndexerServiceApp.ServiceState.ACTIVE);
            var readyActive = get(client, server.port(), "/ready");
            assertEquals(200, readyActive.statusCode());
            assertTrue(readyActive.body().contains("\"status\":\"READY\""));
        }
    }

    @Test
    void metricsEndpointExposesPrometheusFamilies() throws Exception {
        HotPlugMetrics.heartbeatPublished("indexer-test", true);
        var state = new AtomicReference<>(IndexerServiceApp.ServiceState.ACTIVE);
        try (var server = startServer("indexer-test", state)) {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var metrics = get(client, server.port(), "/metrics");
            assertEquals(200, metrics.statusCode());
            assertTrue(metrics.body().contains("# HELP"));
            assertTrue(metrics.body().contains("hotplug_heartbeat_publish_total"));
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static CloseableServer startServer(
        String serviceId,
        AtomicReference<IndexerServiceApp.ServiceState> state
    ) throws Exception {
        HttpServer server = IndexerServiceApp.createHttpServer(0, serviceId, state);
        server.start();
        return new CloseableServer(server);
    }

    private record CloseableServer(HttpServer delegate) implements AutoCloseable {
        @Override
        public void close() {
            delegate.stop(0);
        }

        int port() {
            return delegate.getAddress().getPort();
        }
    }
}
