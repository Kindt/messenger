package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SendMessageRequestJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rejectsLegacyTtlAlias() {
        assertThrows(UnrecognizedPropertyException.class, () -> MAPPER.readValue("""
            {"type":"text","content":"hi","ttl_seconds":120}
            """, SendMessageRequest.class));
    }

    @Test
    void deserializesVisibilityTtlFromCanonicalField() throws Exception {
        var req = MAPPER.readValue("""
            {"type":"text","content":"hi","visibility_ttl_seconds":3600}
            """, SendMessageRequest.class);
        assertEquals(3600, req.visibilityTtlSeconds());
    }
}
