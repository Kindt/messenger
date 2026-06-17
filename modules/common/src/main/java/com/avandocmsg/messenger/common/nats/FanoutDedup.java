package com.avandocmsg.messenger.common.nats;

import java.util.concurrent.ConcurrentHashMap;

/** In-memory dedup for fan-out delivers (PS-2.2 JetStream redelivery). */
public final class FanoutDedup {

    private final long ttlMs;
    private final ConcurrentHashMap<String, Long> expiryByKey = new ConcurrentHashMap<>();

    public FanoutDedup(int ttlSeconds) {
        this.ttlMs = ttlSeconds <= 0 ? 0L : ttlSeconds * 1000L;
    }

    public static FanoutDedup fromEnv() {
        var raw = System.getenv("PIPELINE_FANOUT_DEDUP_TTL_SECONDS");
        var ttl = 60;
        if (raw != null && !raw.isBlank()) {
            try {
                ttl = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                ttl = 60;
            }
        }
        return new FanoutDedup(ttl);
    }

    public boolean enabled() {
        return ttlMs > 0;
    }

    /** @return true when duplicate deliver should be skipped */
    public boolean isDuplicate(String messageId, String recipientKey) {
        if (ttlMs <= 0 || messageId == null || messageId.isBlank()
            || recipientKey == null || recipientKey.isBlank()) {
            return false;
        }
        pruneExpired();
        var key = messageId + "|" + recipientKey;
        var now = System.currentTimeMillis();
        var candidateExpiry = now + ttlMs;
        var prev = expiryByKey.putIfAbsent(key, candidateExpiry);
        if (prev == null) {
            return false;
        }
        if (prev > now) {
            return true;
        }
        expiryByKey.put(key, candidateExpiry);
        return false;
    }

    private void pruneExpired() {
        var now = System.currentTimeMillis();
        expiryByKey.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
