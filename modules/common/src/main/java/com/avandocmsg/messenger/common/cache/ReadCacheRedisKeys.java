package com.avandocmsg.messenger.common.cache;

/**
 * Redis key builders for read-cache invalidation from workers (prefix must match {@code ReadCacheKeys} in core-port).
 */
public final class ReadCacheRedisKeys {
    private static final String PREFIX = "korus:rc:";

    private ReadCacheRedisKeys() {
    }

    public static String chatList(String userId) {
        return PREFIX + "chat:list:" + userId;
    }

    public static String chatUnread(String userId) {
        return PREFIX + "chat:unread:" + userId;
    }
}
