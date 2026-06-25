package com.avandocmsg.messenger.worker.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypingFanoutDebouncerTest {

    @Test
    void shouldFanout_allowsFirstThenDebouncesWithinWindow() {
        var debouncer = new TypingFanoutDebouncer(2_000L);
        assertTrue(debouncer.shouldFanout("chat", "user", 10_000L));
        assertFalse(debouncer.shouldFanout("chat", "user", 10_500L));
        assertTrue(debouncer.shouldFanout("chat", "user", 12_100L));
    }

    @Test
    void shouldFanout_isolatedPerChatUserPair() {
        var debouncer = new TypingFanoutDebouncer(1_000L);
        assertTrue(debouncer.shouldFanout("c1", "u1", 1L));
        assertFalse(debouncer.shouldFanout("c1", "u1", 2L));
        assertTrue(debouncer.shouldFanout("c2", "u1", 2L));
    }
}
