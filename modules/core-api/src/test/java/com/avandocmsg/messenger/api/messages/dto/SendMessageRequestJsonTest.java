package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SendMessageRequestJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesVisibilityTtlFromLegacyAlias() throws Exception {
        var req = MAPPER.readValue("""
            {"type":"text","content":"hi","ttl_seconds":120}
            """, SendMessageRequest.class);
        assertEquals(120, req.visibilityTtlSeconds());
        assertNull(req.archiveTtlSeconds());
    }

    @Test
    void deserializesVisibilityTtlFromCanonicalField() throws Exception {
        var req = MAPPER.readValue("""
            {"type":"text","content":"hi","visibility_ttl_seconds":3600}
            """, SendMessageRequest.class);
        assertEquals(3600, req.visibilityTtlSeconds());
    }
}
