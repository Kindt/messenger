package com.avandocmsg.messenger.api.cache;

import com.avandocmsg.messenger.common.dto.ReadCacheInvalidateEvent;
import com.avandocmsg.messenger.core.port.ReadCacheKeys;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadCacheInvalidationSubscriberTest {

    @Test
    void apply_invalidatesUnreadAndChatList() {
        var cache = new RecordingCache(true);
        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        when(connection.createDispatcher(any())).thenReturn(dispatcher);
        var subscriber = new ReadCacheInvalidationSubscriber(connection, cache);
        var userId = UUID.randomUUID();
        subscriber.apply(new ReadCacheInvalidateEvent(List.of(userId.toString()), true, true));

        assertTrue(cache.invalidated.contains(ReadCacheKeys.chatUnread(userId)));
        assertTrue(cache.invalidated.contains(ReadCacheKeys.chatList(userId)));
    }

    @Test
    void onMessage_countsMalformedEventAndKeepsConsumerRunning() {
        var cache = new RecordingCache(true);
        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        var message = mock(Message.class);
        when(connection.createDispatcher(any())).thenReturn(dispatcher);
        when(message.getData()).thenReturn("{not-json".getBytes(StandardCharsets.UTF_8));
        var subscriber = new ReadCacheInvalidationSubscriber(connection, cache);
        Double before = CollectorRegistry.defaultRegistry.getSampleValue(
            "read_cache_invalidation_failure_total", new String[] {"reason"}, new String[] {"malformed_event"});

        subscriber.onMessage(message);

        assertEquals((before == null ? 0.0 : before) + 1.0,
            CollectorRegistry.defaultRegistry.getSampleValue(
                "read_cache_invalidation_failure_total", new String[] {"reason"}, new String[] {"malformed_event"}));
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
        // intentional no-op: test stub
        }

        @Override
        public void invalidate(String key) {
            invalidated.add(key);
        }
    }
}
