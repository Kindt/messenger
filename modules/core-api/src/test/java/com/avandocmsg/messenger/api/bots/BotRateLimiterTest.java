package com.avandocmsg.messenger.api.bots;

import org.junit.jupiter.api.Test;

import java.util.UUID;

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
    void isolatesBots() {
        var limiter = new BotRateLimiter(1);
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
    }
}
