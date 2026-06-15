package com.avandocmsg.messenger.core.port;

/** Named read-cache buckets with default TTL (spec 006 FR-OPT-03). */
public enum ReadCacheKind {
    CHAT_LIST(60),
    CHAT_UNREAD(30),
    USER_PROFILE(120),
    USER_PRESENCE(15);

    private final int defaultTtlSeconds;

    ReadCacheKind(int defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public int defaultTtlSeconds() {
        return defaultTtlSeconds;
    }
}
