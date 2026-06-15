package com.avandocmsg.messenger.api.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotServiceTest {

    @Test
    void normalizeListenMode_defaultsToMentionsOnly() {
        assertEquals("MENTIONS_ONLY", BotService.normalizeListenMode(null));
        assertEquals("MENTIONS_ONLY", BotService.normalizeListenMode(""));
    }

    @Test
    void normalizeListenMode_acceptsReadAll() {
        assertEquals("READ_ALL", BotService.normalizeListenMode("read_all"));
    }

    @Test
    void normalizeListenMode_rejectsUnknown() {
        assertNull(BotService.normalizeListenMode("ALL"));
    }

    @Test
    void isHttpsUrl_requiresHttpsAndHost() {
        assertTrue(BotService.isHttpsUrl("https://hooks.example.com/bot"));
        assertFalse(BotService.isHttpsUrl("http://hooks.example.com/bot"));
        assertFalse(BotService.isHttpsUrl("not-a-url"));
    }
}
