package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.port.ReadCacheKeys;
import com.avandocmsg.messenger.core.port.ReadCachePort;

import java.util.UUID;

/** Targeted read-cache invalidation (spec 006 FR-OPT-03). */
public final class ReadCacheCoordinator {

    private ReadCacheCoordinator() {
    }

    public static void invalidateChatList(ReadCachePort cache, UUID userId) {
        if (!cache.enabled() || userId == null) {
            return;
        }
        cache.invalidate(ReadCacheKeys.chatList(userId));
    }

    public static void invalidateChatUnread(ReadCachePort cache, UUID userId) {
        if (!cache.enabled() || userId == null) {
            return;
        }
        cache.invalidate(ReadCacheKeys.chatUnread(userId));
    }

    public static void invalidateUserProfile(ReadCachePort cache, UUID userId) {
        if (!cache.enabled() || userId == null) {
            return;
        }
        cache.invalidate(ReadCacheKeys.userProfile(userId));
    }

    public static void invalidateUserPresence(ReadCachePort cache, UUID userId) {
        if (!cache.enabled() || userId == null) {
            return;
        }
        cache.invalidate(ReadCacheKeys.userPresence(userId));
    }

    public static void invalidateAfterChatMutation(ReadCachePort cache, UUID... userIds) {
        if (!cache.enabled() || userIds == null) {
            return;
        }
        for (var userId : userIds) {
            if (userId != null) {
                invalidateChatList(cache, userId);
                invalidateChatUnread(cache, userId);
            }
        }
    }
}
