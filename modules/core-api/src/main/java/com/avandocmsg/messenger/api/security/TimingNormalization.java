package com.avandocmsg.messenger.api.security;

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
            var elapsed = System.nanoTime() - start;
            if (elapsed < minimumNanos) {
                var remainingMs = Math.max(1L, (minimumNanos - elapsed) / 1_000_000L);
                try {
                    Thread.sleep(remainingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Extra delay on not-found paths when normalization is enabled (serialization / payload gap). */
    public static void padNotFoundExtra(long extraNanos) {
        if (extraNanos <= 0) {
            return;
        }
        var remainingMs = Math.max(1L, extraNanos / 1_000_000L);
        try {
            Thread.sleep(remainingMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
