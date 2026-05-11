package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Внешний API: snake_case через {@link com.fasterxml.jackson.annotation.JsonProperty}. */
class ChatDtoJsonTest {

    private static ObjectMapper mapper() {
        var om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    @Test
    void chatResponse_serializesSnakeCase() throws Exception {
        var om = mapper();
        var json = om.writeValueAsString(new ChatResponse(
            "c1", "Title", "group", "u1", 3, false, null, Instant.parse("2026-01-01T00:00:00Z")));
        assertTrue(json.contains("\"owner_id\""));
        assertTrue(json.contains("\"member_count\""));
        assertTrue(json.contains("\"ttl_seconds\""));
        assertTrue(json.contains("\"created_at\""));
    }
}
