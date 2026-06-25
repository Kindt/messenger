package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageResponseSerializationTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void omitsNullOptionalFields() throws Exception {
        var message = new MessageResponse(
            "id",
            "chat",
            "sender",
            "text",
            "hello",
            null,
            false,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            null,
            null
        );
        var json = JSON.writeValueAsString(message);
        assertFalse(json.contains("thread_id"));
        assertFalse(json.contains("reply_preview"));
        assertFalse(json.contains("edited_at"));
    }
}
