package com.avandocmsg.messenger.api.bots;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRateLimiterTest {

    @Test
    void allowsUpToLimitPerMinute() {
        var limiter = new BotRateLimiter(3);
        var botId = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(botId));
        assertTrue(limiter.tryAcquire(botId));
        assertTrue(limiter.tryAcquire(botId));
        assertFalse(limiter.tryAcquire(botId));
    }

    @Test
    void evictIdleEntries_removesBotsIdleOverTenMinutes() {
        var limiter = new BotRateLimiter(3);
        var botId = UUID.randomUUID();
        limiter.seedTimestampForTest(botId, java.time.Instant.now().minusSeconds(601).toEpochMilli());
        assertEquals(1, limiter.trackedBotCount());
        limiter.evictIdleEntries();
        assertEquals(0, limiter.trackedBotCount());
    }

    @Test
    void scheduledEviction_prunesStaleBotsWithoutTryAcquire() {
        var limiter = new BotRateLimiter(3, true, 50L);
        try {
            var botId = UUID.randomUUID();
            limiter.seedTimestampForTest(botId, java.time.Instant.now().minusSeconds(601).toEpochMilli());
            assertEquals(1, limiter.trackedBotCount());
            var deadline = System.nanoTime() + 10_000_000_000L;
            while (limiter.trackedBotCount() > 0 && System.nanoTime() < deadline) {
                LockSupport.parkNanos(50_000_000L);
                Thread.yield();
            }
            assertEquals(0, limiter.trackedBotCount(), "scheduled eviction should prune idle bot windows");
        } finally {
            limiter.close();
        }
    }

    @Test
    void fromEnv_isCloseableAndAppliesDefaultLimit() {
        try (var limiter = BotRateLimiter.fromEnv()) {
            var botId = UUID.randomUUID();
            for (int i = 0; i < 30; i++) {
                assertTrue(limiter.tryAcquire(botId));
            }
            assertFalse(limiter.tryAcquire(botId));
        }
    }

    @Test
    void isolatesBots() {
        var limiter = new BotRateLimiter(1);
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
    }
}
