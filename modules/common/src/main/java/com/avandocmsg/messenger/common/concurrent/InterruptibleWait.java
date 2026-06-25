package com.avandocmsg.messenger.common.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Interruptible waits without scattering {@link Thread#sleep} (spec 025 FR-129). */
public final class InterruptibleWait {

    private InterruptibleWait() {}

  /**
   * @return {@code true} if the wait was interrupted (interrupt flag restored)
   */
    public static boolean sleepMillis(long millis) {
        if (millis <= 0) {
            return false;
        }
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (true) {
            var remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) {
                return true;
            }
        }
    }

    /**
     * Sleep in slices until {@code deadlineEpochMs} or interrupted.
     *
     * @return {@code true} if interrupted
     */
    public static boolean awaitDeadline(long deadlineEpochMs, long sliceMs) {
        var slice = Math.max(1L, sliceMs);
        while (System.currentTimeMillis() < deadlineEpochMs) {
            var remaining = deadlineEpochMs - System.currentTimeMillis();
            if (InterruptibleWait.sleepMillis(Math.min(slice, remaining))) {
                return true;
            }
        }
        return false;
    }
}
