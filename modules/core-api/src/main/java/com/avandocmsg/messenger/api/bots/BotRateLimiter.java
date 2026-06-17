package com.avandocmsg.messenger.api.bots;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limit per bot (default 30 req/min, env BOT_RATE_LIMIT_PER_MIN).
 */
public class BotRateLimiter {

    private static final long IDLE_EVICT_MS = 600_000L;

    private final int limitPerMinute;
    private final Map<UUID, Deque<Long>> windows = new ConcurrentHashMap<>();

    public BotRateLimiter(int limitPerMinute) {
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    public static BotRateLimiter fromEnv() {
        var raw = System.getenv("BOT_RATE_LIMIT_PER_MIN");
        var limit = 30;
        if (raw != null && !raw.isBlank()) {
            try {
                limit = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return new BotRateLimiter(limit);
    }

    public boolean tryAcquire(UUID botId) {
        evictIdleEntries();
        var now = Instant.now().toEpochMilli();
        var windowMs = 60_000L;
        var q = windows.computeIfAbsent(botId, id -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() >= limitPerMinute) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** Drop bot windows idle longer than 10 minutes (PS-2.1). */
    void evictIdleEntries() {
        var cutoff = Instant.now().toEpochMilli() - IDLE_EVICT_MS;
        windows.entrySet().removeIf(entry -> {
            var q = entry.getValue();
            synchronized (q) {
                while (!q.isEmpty() && q.peekFirst() < cutoff) {
                    q.pollFirst();
                }
                return q.isEmpty();
            }
        });
    }

    int trackedBotCount() {
        return windows.size();
    }

    void seedTimestampForTest(UUID botId, long epochMs) {
        var q = new ArrayDeque<Long>();
        q.addLast(epochMs);
        windows.put(botId, q);
    }
}
