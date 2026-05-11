package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionInterMessageSleepTest {

    @Test
    void sleepQuiet_zeroOrNegative_noSleepNotInterrupted() {
        assertFalse(RetentionInterMessageSleep.sleepQuiet(0));
        assertFalse(RetentionInterMessageSleep.sleepQuiet(-1));
    }

    @Test
    void sleepQuiet_interruptReturnsTrueAndLeavesInterruptSet() throws Exception {
        var box = new boolean[1];
        var t = new Thread(() -> {
            box[0] = RetentionInterMessageSleep.sleepQuiet(120_000);
            assertTrue(Thread.currentThread().isInterrupted());
        });
        t.start();
        int spins = 0;
        while (t.getState() != Thread.State.TIMED_WAITING && spins++ < 500) {
            Thread.sleep(2);
        }
        t.interrupt();
        t.join(10_000);
        assertFalse(t.isAlive());
        assertTrue(box[0]);
    }
}
