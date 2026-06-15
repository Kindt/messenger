package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotEventFilterTest {

    @Test
    void readAll_deliversEvenWithoutMention() {
        var event = new MessageWorkerEvent("m1", "c1", "u1", null, 1L, "text", 0, false, 5, "hello", null);
        assertTrue(BotEventFilter.shouldDeliver(event, "bot1", "READ_ALL"));
    }

    @Test
    void mentionsOnly_requiresAtBotName() {
        var event = new MessageWorkerEvent("m1", "c1", "u1", null, 1L, "text", 0, false, 5, "hi @helpdesk_bot", null);
        assertTrue(BotEventFilter.shouldDeliver(event, "helpdesk_bot", "MENTIONS_ONLY"));
        assertFalse(BotEventFilter.shouldDeliver(event, "other_bot", "MENTIONS_ONLY"));
    }

    @Test
    void mentionsOnly_skipsEncrypted() {
        var event = new MessageWorkerEvent("m1", "c1", "u1", null, 1L, "e2ee-text", 0, true, 5, null, null);
        assertFalse(BotEventFilter.shouldDeliver(event, "bot1", "MENTIONS_ONLY"));
    }
}
