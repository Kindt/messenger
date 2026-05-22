package com.avandocmsg.messenger.worker.push;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushHealthHttpServerTest {

    @Test
    void health_returnsOkWhenProbeReady() throws Exception {
        try (var server = PushHealthHttpServer.start(0, (PushReadinessCheck) () -> true)) {
            var port = server.getPort();
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode());
            assertEquals("ok", resp.body());
        }
    }

    @Test
    void health_returns503WhenProbeNotReady() throws Exception {
        try (var server = PushHealthHttpServer.start(0, (PushReadinessCheck) () -> false)) {
            var port = server.getPort();
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, resp.statusCode());
        }
    }
}
