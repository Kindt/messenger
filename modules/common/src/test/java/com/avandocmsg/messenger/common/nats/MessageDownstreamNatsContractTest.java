package com.avandocmsg.messenger.common.nats;

import com.avandocmsg.messenger.common.dto.MessageDownstreamEvent;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-162 contract tests for {@code msg.event.downstream} envelope (spec 025).
 */
class MessageDownstreamNatsContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MESSAGE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String CHAT_ID = "660e8400-e29b-41d4-a716-446655440001";

    @Test
    void goldenFixture_deserializesRequiredFields() throws Exception {
        var envelope = readFixture("nats/downstream-golden-envelope.json");

        assertNotNull(envelope.route());
        assertFalse(envelope.route().isEmpty());
        assertEquals(MESSAGE_ID, envelope.messageId());
        assertEquals(CHAT_ID, envelope.chatId());
        assertNotNull(envelope.payload());
    }

    @Test
    void goldenFixture_payload_deserializesLegacyMessageWorkerEvent() throws Exception {
        var envelope = readFixture("nats/downstream-golden-envelope.json");
        var worker = envelope.payload();

        assertEquals(MESSAGE_ID, worker.messageId());
        assertEquals(CHAT_ID, worker.chatId());
        assertEquals("770e8400-e29b-41d4-a716-446655440002", worker.senderId());
        assertEquals("client-1", worker.clientMsgId());
        assertEquals(1_700_000_000_000L, worker.createdAtEpochMs());
        assertEquals("text", worker.type());
        assertFalse(worker.encrypted());
        assertEquals(5, worker.storageByteLength());
        assertEquals("hello", worker.searchText());
        assertEquals(null, worker.indexOp());
    }

    @Test
    void unknownRouteTokens_doNotFailDeserialization() throws Exception {
        assertDoesNotThrow(() -> readFixture("nats/downstream-unknown-route-envelope.json"));
    }

    @Test
    void unknownRouteTokens_doNotBreakKnownConsumerRouteCheck() throws Exception {
        var envelope = readFixture("nats/downstream-unknown-route-envelope.json");

        assertTrue(MessageDownstreamRouting.routeTargetsConsumer(envelope.route(), MessageDownstreamRouting.ROUTE_INDEX));
        assertTrue(MessageDownstreamRouting.routeTargetsConsumer(envelope.route(), MessageDownstreamRouting.ROUTE_PUSH));
        assertTrue(MessageDownstreamRouting.routeTargetsConsumer(envelope.route(), MessageDownstreamRouting.ROUTE_BOT));
        assertFalse(MessageDownstreamRouting.routeTargetsConsumer(envelope.route(), "archiver"));
    }

    @Test
    void pipelinePublishedEnvelope_matchesContractShape() throws Exception {
        var workerEvent = MessageWorkerEvent.fromSendEvent(new MessageSendEvent(
            MESSAGE_ID,
            CHAT_ID,
            "770e8400-e29b-41d4-a716-446655440002",
            "text",
            "hello",
            "client-1",
            1_700_000_000_000L,
            null,
            null,
            null,
            null));
        var published = new MessageDownstreamEvent(
            List.of("index", "push", "bot"),
            MESSAGE_ID,
            CHAT_ID,
            workerEvent);

        var json = MAPPER.writeValueAsString(published);
        var parsed = MAPPER.readValue(json, MessageDownstreamEvent.class);

        assertEquals(List.of("index", "push", "bot"), parsed.route());
        assertEquals(MESSAGE_ID, parsed.messageId());
        assertEquals(CHAT_ID, parsed.chatId());
        assertEquals("hello", parsed.payload().searchText());
        assertTrue(json.contains("\"message_id\""));
        assertTrue(json.contains("\"chat_id\""));
    }

    @Test
    void legacyIndexDeletePayload_roundTripsInsideEnvelope() throws Exception {
        var workerEvent = MessageWorkerEvent.forIndexDelete(MESSAGE_ID);
        var envelope = new MessageDownstreamEvent(
            List.of("index"),
            MESSAGE_ID,
            CHAT_ID,
            workerEvent);

        var parsed = MAPPER.readValue(MAPPER.writeValueAsBytes(envelope), MessageDownstreamEvent.class);

        assertEquals("delete", parsed.payload().indexOp());
        assertEquals(MESSAGE_ID, parsed.payload().messageId());
    }

    @Test
    void pushBotPayload_truncatesSearchTextFromEnvelope() throws Exception {
        var longText = "x".repeat(200);
        var workerEvent = MessageWorkerEvent.fromSendEvent(new MessageSendEvent(
            MESSAGE_ID, CHAT_ID, "770e8400-e29b-41d4-a716-446655440002", "text", longText,
            "client-1", 1_700_000_000_000L, null, null, null, null));
        var envelope = new MessageDownstreamEvent(
            List.of(MessageDownstreamRouting.ROUTE_PUSH), MESSAGE_ID, CHAT_ID, workerEvent);

        var pushPayload = MessageDownstreamRouting.payloadForRoute(envelope, MessageDownstreamRouting.ROUTE_PUSH);
        assertEquals(MessageWorkerEvent.PUSH_BOT_SEARCH_TEXT_MAX, pushPayload.searchText().length());
    }

    private static MessageDownstreamEvent readFixture(String resourcePath) throws Exception {
        try (InputStream in = MessageDownstreamNatsContractTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing fixture: " + resourcePath);
            var json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, MessageDownstreamEvent.class);
        }
    }
}
