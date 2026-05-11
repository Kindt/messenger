package com.avandocmsg.messenger.worker.preview;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory TTL cache for MVP preview results (optional Redis later). */
final class TtlStringCache {
    private record Entry(String value, long expiryEpochMs) {
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlMs;

    TtlStringCache(Duration ttl) {
        this.ttlMs = Math.max(1_000L, ttl.toMillis());
    }

    Optional<String> get(String key) {
        Objects.requireNonNull(key);
        var now = System.currentTimeMillis();
        var e = map.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (e.expiryEpochMs <= now) {
            map.remove(key, e);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    void put(String key, String value) {
        Objects.requireNonNull(key);
        var exp = System.currentTimeMillis() + ttlMs;
        map.put(key, new Entry(value, exp));
    }
}
