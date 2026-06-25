package com.avandocmsg.messenger.common.nats;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/** In-memory dedup for fan-out delivers (PS-2.2 JetStream redelivery). */
public final class FanoutDedup {

    public static final int DEFAULT_MAX_SIZE = 10_000;

    private final long ttlMs;
    private final Cache<String, Boolean> cache;

    public FanoutDedup(int ttlSeconds) {
        this(ttlSeconds, DEFAULT_MAX_SIZE);
    }

    public FanoutDedup(int ttlSeconds, int maxSize) {
        this.ttlMs = ttlSeconds <= 0 ? 0L : ttlSeconds * 1000L;
        if (ttlMs <= 0) {
            this.cache = null;
        } else {
            this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxSize))
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .build();
        }
    }

    public static FanoutDedup fromEnv() {
        var ttl = parsePositiveInt(System.getenv("PIPELINE_FANOUT_DEDUP_TTL_SECONDS"), 60);
        var maxSize = parsePositiveInt(System.getenv("PIPELINE_FANOUT_DEDUP_MAX_SIZE"), DEFAULT_MAX_SIZE);
        return new FanoutDedup(ttl, maxSize);
    }

    public boolean enabled() {
        return ttlMs > 0;
    }

    /** @return true when duplicate deliver should be skipped */
    public boolean isDuplicate(String messageId, String recipientKey) {
        if (cache == null || messageId == null || messageId.isBlank()
            || recipientKey == null || recipientKey.isBlank()) {
            return false;
        }
        var key = messageId + "|" + recipientKey;
        return cache.asMap().putIfAbsent(key, Boolean.TRUE) != null;
    }

    long estimatedSize() {
        return cache == null ? 0L : cache.estimatedSize();
    }

    void cleanUp() {
        if (cache != null) {
            cache.cleanUp();
        }
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
