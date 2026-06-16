package com.avandocmsg.messenger.api.security;

import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/** Pads sensitive-path execution to a minimum duration (timing normalization). */
public final class TimingNormalization {
    private TimingNormalization() {
    }

    public static <T> T runWithMinimumDuration(long minimumNanos, Supplier<T> action) {
        var start = System.nanoTime();
        try {
            return action.get();
        } finally {
            parkRemaining(minimumNanos - (System.nanoTime() - start));
        }
    }

    /** Extra delay on not-found paths when normalization is enabled (serialization / payload gap). */
    public static void padNotFoundExtra(long extraNanos) {
        parkRemaining(extraNanos);
    }

    private static void parkRemaining(long nanos) {
        if (nanos <= 0) {
            return;
        }
        var deadline = System.nanoTime() + nanos;
        while (true) {
            var left = deadline - System.nanoTime();
            if (left <= 0) {
                return;
            }
            LockSupport.parkNanos(left);
            if (Thread.interrupted()) {
                return;
            }
        }
    }
}
