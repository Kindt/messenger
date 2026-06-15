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
}
