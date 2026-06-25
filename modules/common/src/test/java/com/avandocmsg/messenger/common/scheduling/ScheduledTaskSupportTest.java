package com.avandocmsg.messenger.common.scheduling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledTaskSupportTest {

    @Test
    void jitterMs_returnsBaseWhenNoJitter() {
        assertEquals(1000L, ScheduledTaskSupport.jitterMs(1000L, 0));
    }

    @Test
    void jitterMs_withinRange() {
        long base = 5000L;
        long max = 1000L;
        for (int i = 0; i < 20; i++) {
            long v = ScheduledTaskSupport.jitterMs(base, max);
            assertTrue(v >= base && v <= base + max);
        }
    }
}
