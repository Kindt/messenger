package com.avandocmsg.messenger.worker.preview;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** In-memory TTL cache for MVP preview results (optional Redis later). */
final class TtlStringCache {

    private static final int MAX_ENTRIES = 10_000;

    private record Entry(String value, long expiryEpochMs) {}

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> fifoKeys = new ConcurrentLinkedQueue<>();
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
            fifoKeys.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    void put(String key, String value) {
        Objects.requireNonNull(key);
        evictExpired();
        enforceCapacity();
        var exp = System.currentTimeMillis() + ttlMs;
        map.put(key, new Entry(value, exp));
        fifoKeys.offer(key);
    }

    int size() {
        evictExpired();
        return map.size();
    }

    private void evictExpired() {
        var now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> {
            if (entry.getValue().expiryEpochMs <= now) {
                fifoKeys.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void enforceCapacity() {
        while (map.size() >= MAX_ENTRIES) {
            var evictKey = fifoKeys.poll();
            if (evictKey == null) {
                break;
            }
            map.remove(evictKey);
        }
    }
}
