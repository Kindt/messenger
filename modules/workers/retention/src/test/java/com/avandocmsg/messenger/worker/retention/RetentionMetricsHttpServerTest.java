package com.avandocmsg.messenger.worker.retention;

import io.prometheus.client.hotspot.DefaultExports;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetentionMetricsHttpServerTest {

    @Test
    void healthReturns200WhenProbeReady() throws Exception {
        try (var server = RetentionMetricsHttpServer.start(0, () -> true)) {
            var port = server.getPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());
            assertEquals("ok", res.body().trim());
        }
    }

    @Test
    void healthReturns503WhenProbeNotReady() throws Exception {
        try (var server = RetentionMetricsHttpServer.start(0, () -> false)) {
            var port = server.getPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
            assertEquals("not ready", res.body().trim());
        }
    }

    @Test
    void metricsEndpointReturnsPrometheusText() throws Exception {
        DefaultExports.initialize();
        try (var server = RetentionMetricsHttpServer.start(0, () -> true)) {
            var port = server.getPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());
            var body = res.body();
            assertEquals(true, body.contains("# HELP") || body.contains("jvm_"));
            assertEquals(true, body.contains("retention_worker_build_info"));
            assertEquals(true, body.contains("name=\"retention-worker\""));
        }
    }
}
