package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SrtcpReplayIndexTrackerTest {

    @Test
    void tracksReplayIndexesIndependentlyPerSenderSsrc() {
        var tracker = new SrtcpReplayIndexTracker();

        assertTrue(tracker.accept(0x0102_0304L, 0));
        assertTrue(tracker.accept(0x1112_1314L, 0));
        assertFalse(tracker.accept(0x0102_0304L, 0));
        assertTrue(tracker.accept(0x0102_0304L, 1));
        assertFalse(tracker.accept(0x1112_1314L, 0));
    }
}
