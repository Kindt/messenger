package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.port.ReadCacheKeys;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadCacheCoordinatorTest {

    @Test
    void invalidateChatUnread_skipsWhenDisabled() {
        var cache = new RecordingCache(false);
        var userId = UUID.randomUUID();
        ReadCacheCoordinator.invalidateChatUnread(cache, userId);
        assertTrue(cache.invalidated.isEmpty());
    }

    @Test
    void invalidateAfterChatMutation_invalidatesListAndUnread() {
        var cache = new RecordingCache(true);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        ReadCacheCoordinator.invalidateAfterChatMutation(cache, a, b);
        assertTrue(cache.invalidated.contains(ReadCacheKeys.chatList(a)));
        assertTrue(cache.invalidated.contains(ReadCacheKeys.chatUnread(a)));
        assertTrue(cache.invalidated.contains(ReadCacheKeys.chatList(b)));
        assertTrue(cache.invalidated.contains(ReadCacheKeys.chatUnread(b)));
    }

    private static final class RecordingCache implements ReadCachePort {
        final List<String> invalidated = new ArrayList<>();
        private final boolean enabled;

        RecordingCache(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.empty();
        }

        @Override
        public void put(String key, String jsonValue, int ttlSeconds) {
            // no-op stub: invalidate-only assertions
        }

        @Override
        public void invalidate(String key) {
            invalidated.add(key);
        }
    }
}
