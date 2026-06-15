package com.avandocmsg.messenger.core.port;

import java.util.UUID;

/** Redis key builders for {@link ReadCachePort} (prefix {@code korus:rc:}). */
public final class ReadCacheKeys {
    private static final String PREFIX = "korus:rc:";

    private ReadCacheKeys() {
    }

    public static String chatList(UUID userId) {
        return PREFIX + "chat:list:" + userId;
    }

    public static String chatUnread(UUID userId) {
        return PREFIX + "chat:unread:" + userId;
    }

    public static String userProfile(UUID userId) {
        return PREFIX + "user:profile:" + userId;
    }

    public static String userPresence(UUID userId) {
        return PREFIX + "user:presence:" + userId;
    }
}
