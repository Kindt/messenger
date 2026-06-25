package com.avandocmsg.messenger.common.json;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessengerJsonTest {

    @Test
    void mapper_serializesJavaTimeTypes() throws Exception {
        var json = MessengerJson.mapper().writeValueAsString(Instant.parse("2026-01-01T00:00:00Z"));
        assertTrue(json.contains("2026"));
    }
}
