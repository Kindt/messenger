package com.avandocmsg.messenger.worker.botdelivery;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory dedup for bot webhook deliveries (messageId + webhook URL). */
final class BotDeliveryDedupCache {

    static final int MAX_ENTRIES = 10_000;

    private record Entry(long expiryEpochMs, long lastAccessNanos) {}

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlMs;

    BotDeliveryDedupCache(Duration ttl) {
        this.ttlMs = Math.max(1L, ttl.toMillis());
    }

    static BotDeliveryDedupCache fromEnv() {
        var raw = System.getenv("BOT_WEBHOOK_DEDUP_TTL_SECONDS");
        var ttlSec = 3600;
        if (raw != null && !raw.isBlank()) {
            try {
                ttlSec = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return new BotDeliveryDedupCache(Duration.ofSeconds(Math.max(1, ttlSec)));
    }

    /** @return true when this is the first delivery within TTL for the key */
    boolean markIfFirst(String key) {
        Objects.requireNonNull(key);
        evictExpired();
        var now = System.currentTimeMillis();
        var candidate = new Entry(now + ttlMs, System.nanoTime());
        var prev = map.putIfAbsent(key, candidate);
        if (prev == null) {
            enforceCapacity();
            return true;
        }
        if (prev.expiryEpochMs() > now) {
            return false;
        }
        enforceCapacity();
        map.put(key, candidate);
        return true;
    }

    int size() {
        evictExpired();
        return map.size();
    }

    private void evictExpired() {
        var now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> entry.getValue().expiryEpochMs() <= now);
    }

    private void enforceCapacity() {
        if (map.size() < MAX_ENTRIES) {
            return;
        }
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (var entry : map.entrySet()) {
            if (entry.getValue().lastAccessNanos() < oldestAccess) {
                oldestAccess = entry.getValue().lastAccessNanos();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            map.remove(oldestKey);
        }
    }
}
