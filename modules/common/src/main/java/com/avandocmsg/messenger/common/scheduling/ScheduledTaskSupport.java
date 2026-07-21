package com.avandocmsg.messenger.common.scheduling;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Jittered scheduling to avoid aligned periodic load spikes (spec 025 FR-099). */
public final class ScheduledTaskSupport {

    private ScheduledTaskSupport() {}

    public static long jitterMs(long baseMs, long maxJitterMs) {
        if (baseMs <= 0) {
            return baseMs;
        }
        if (maxJitterMs <= 0) {
            return baseMs;
        }
        return baseMs + ThreadLocalRandom.current().nextLong(maxJitterMs + 1);
    }

    public static ScheduledFuture<?> scheduleWithFixedDelayAndJitter( // NOSONAR java:S1452 -- mirrors ScheduledExecutorService return
            ScheduledExecutorService executor,
            Runnable task,
            long initialDelayMs,
            long intervalMs,
            long maxJitterMs) {
        return executor.scheduleWithFixedDelay(
            task,
            jitterMs(initialDelayMs, maxJitterMs),
            jitterMs(intervalMs, maxJitterMs),
            TimeUnit.MILLISECONDS);
    }

    public static ScheduledFuture<?> scheduleAtFixedRateWithJitter( // NOSONAR java:S1452 -- mirrors ScheduledExecutorService return
            ScheduledExecutorService executor,
            Runnable task,
            long initialDelay,
            long period,
            long maxJitterMs,
            TimeUnit unit) {
        long initialMs = unit.toMillis(initialDelay);
        long periodMs = unit.toMillis(period);
        return executor.scheduleAtFixedRate(
            task,
            jitterMs(initialMs, maxJitterMs),
            jitterMs(periodMs, maxJitterMs),
            TimeUnit.MILLISECONDS);
    }
}
