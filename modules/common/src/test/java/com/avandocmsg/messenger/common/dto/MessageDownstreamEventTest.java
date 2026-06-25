package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDownstreamEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roundTrip_preservesRouteAndPayload() throws Exception {
        var payload = MessageWorkerEvent.fromSendEvent(new MessageSendEvent(
            "msg-1", "chat-1", "user-1", "text", "hello", "client-1", 1_700_000_000_000L, null, null, null, null));
        var event = new MessageDownstreamEvent(List.of("index", "push", "bot"), "msg-1", "chat-1", payload);
        var json = MAPPER.writeValueAsBytes(event);
        var parsed = MAPPER.readValue(json, MessageDownstreamEvent.class);
        assertEquals(List.of("index", "push", "bot"), parsed.route());
        assertEquals("msg-1", parsed.messageId());
        assertEquals("chat-1", parsed.chatId());
        assertEquals("hello", parsed.payload().searchText());
        assertTrue(json.length > 0);
    }
}
