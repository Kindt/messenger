package com.avandocmsg.messenger.worker.pipeline;

import java.util.concurrent.ConcurrentHashMap;

/** Debounce typing fan-out to at most once per user/chat interval (spec 025 FR-010). */
public final class TypingFanoutDebouncer {
    static final long DEFAULT_DEBOUNCE_MS = 2_000L;

    private final long debounceMs;
    private final ConcurrentHashMap<String, Long> lastFanoutMs = new ConcurrentHashMap<>();

    public TypingFanoutDebouncer() {
        this(parseDebounceMs(System.getenv("PIPELINE_TYPING_DEBOUNCE_MS"), DEFAULT_DEBOUNCE_MS));
    }

    TypingFanoutDebouncer(long debounceMs) {
        this.debounceMs = Math.max(0L, debounceMs);
    }

    /**
     * @return true when fan-out should proceed
     */
    boolean shouldFanout(String chatId, String userId, long eventTs) {
        if (debounceMs <= 0L) {
            return true;
        }
        if (chatId == null || userId == null) {
            return false;
        }
        var key = chatId + "|" + userId;
        var now = eventTs > 0L ? eventTs : System.currentTimeMillis();
        var prev = lastFanoutMs.get(key);
        if (prev != null && now - prev < debounceMs) {
            return false;
        }
        lastFanoutMs.put(key, now);
        return true;
    }

    private static long parseDebounceMs(String raw, long defaultMs) {
        if (raw == null || raw.isBlank()) {
            return defaultMs;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultMs;
        }
    }
}
