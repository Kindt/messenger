package com.avandocmsg.messenger.common.nats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FanoutDedupTest {

    @Test
    void isDuplicate_blocksSecondDeliverWithinTtl() {
        var dedup = new FanoutDedup(60);
        assertFalse(dedup.isDuplicate("msg-1", "user:u1"));
        assertTrue(dedup.isDuplicate("msg-1", "user:u1"));
    }

    @Test
    void isDuplicate_allowsDifferentRecipients() {
        var dedup = new FanoutDedup(60);
        assertFalse(dedup.isDuplicate("msg-1", "user:u1"));
        assertFalse(dedup.isDuplicate("msg-1", "user:u2"));
    }

    @Test
    void disabledWhenTtlZero() {
        var dedup = new FanoutDedup(0);
        assertFalse(dedup.isDuplicate("msg-1", "user:u1"));
        assertFalse(dedup.isDuplicate("msg-1", "user:u1"));
    }
}
