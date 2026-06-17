package com.avandocmsg.messenger.worker.preview;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory TTL cache for MVP preview results (optional Redis later). */
final class TtlStringCache {

    private static final int MAX_ENTRIES = 10_000;

    private record Entry(String value, long expiryEpochMs, long lastAccessNanos) {
        Entry touch() {
            return new Entry(value, expiryEpochMs, System.nanoTime());
        }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlMs;

    TtlStringCache(Duration ttl) {
        this.ttlMs = Math.max(1_000L, ttl.toMillis());
    }

    Optional<String> get(String key) {
        Objects.requireNonNull(key);
        evictExpired();
        var e = map.get(key);
        if (e == null) {
            return Optional.empty();
        }
        var now = System.currentTimeMillis();
        if (e.expiryEpochMs <= now) {
            map.remove(key, e);
            return Optional.empty();
        }
        map.put(key, e.touch());
        return Optional.of(e.value());
    }

    void put(String key, String value) {
        Objects.requireNonNull(key);
        evictExpired();
        enforceCapacity();
        var exp = System.currentTimeMillis() + ttlMs;
        map.put(key, new Entry(value, exp, System.nanoTime()));
    }

    int size() {
        evictExpired();
        return map.size();
    }

    private void evictExpired() {
        var now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> entry.getValue().expiryEpochMs <= now);
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
