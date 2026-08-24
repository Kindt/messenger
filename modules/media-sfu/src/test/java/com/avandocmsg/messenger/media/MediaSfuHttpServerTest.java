package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaSfuHttpServerTest {

    @Test
    void exposesStandaloneHealthWithoutAllocatingMediaRooms() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "test-node");
        try (var server = new MediaSfuHttpServer(0, "test-node", rooms)) {
            server.start();
            var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"UP\""));
            assertTrue(response.body().contains("\"node_id\":\"test-node\""));
            assertEquals(0, rooms.activeRoomCount());
        }
    }
}
