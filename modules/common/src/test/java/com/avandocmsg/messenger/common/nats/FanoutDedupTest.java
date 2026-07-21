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

    @Test
    void isDuplicate_allowsRedeliveryAfterTtlExpires() throws InterruptedException {
        var dedup = new FanoutDedup(1, 100);
        assertFalse(dedup.isDuplicate("msg-1", "user:u1"));
        assertTrue(dedup.isDuplicate("msg-1", "user:u1"));
        Thread.sleep(1_100); // NOSONAR java:S2925 -- wait for TTL expiry in unit test
        assertFalse(dedup.isDuplicate("msg-1", "user:u1"));
    }

    @Test
    void isDuplicate_evictsWhenOverMaxSize() {
        var maxSize = 2;
        var dedup = new FanoutDedup(3600, maxSize);
        for (int i = 0; i < maxSize + 5; i++) {
            assertFalse(dedup.isDuplicate("msg-" + i, "user:u1"));
        }
        dedup.cleanUp();
        assertTrue(dedup.estimatedSize() <= maxSize);
    }
}
