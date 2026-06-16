package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BOT-6: webhook delivery posts JSON with {@code event_id} to mock HTTP server.
 */
class BotWebhookDeliveryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<String> receivedSignature = new AtomicReference<>();
    private String webhookUrl;

    @BeforeEach
    void startMockServer() throws Exception {
        receivedBody.set(null);
        receivedContentType.set(null);
        receivedSignature.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedSignature.set(exchange.getRequestHeaders().getFirst(BotWebhookSigner.SIGNATURE_HEADER));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
        webhookUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @AfterEach
    void stopMockServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postWebhook_deliversJsonWithEventId() throws Exception {
        var event = new MessageWorkerEvent(
            "msg-42", "chat-1", "user-9", "client-1", 1_700_000_000_000L,
            "text", 0, false, 12, "hello bot", null);
        var json = BotDeliveryWorker.buildPayload(event);

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var status = BotDeliveryWorker.postWebhook(client, webhookUrl, json, null);

        assertEquals(200, status);
        assertEquals("application/json", receivedContentType.get());
        assertNull(receivedSignature.get());
        assertNotNull(receivedBody.get());
        JsonNode node = MAPPER.readTree(receivedBody.get());
        assertEquals("msg-42", node.get("event_id").asText());
        assertEquals("msg-42", node.get("messageId").asText());
        assertEquals("chat-1", node.get("chatId").asText());
    }

    @Test
    void postWebhook_includesHmacWhenSecretSet() throws Exception {
        var event = new MessageWorkerEvent(
            "msg-hmac", "chat-2", "user-1", null, 1L,
            "text", 0, false, 3, "ping", null);
        var json = BotDeliveryWorker.buildPayload(event);
        var secret = "test-hmac-secret";

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var status = BotDeliveryWorker.postWebhook(client, webhookUrl, json, secret);

        assertEquals(200, status);
        assertEquals(BotWebhookSigner.signSha256Hex(secret, json), receivedSignature.get());
    }
}
