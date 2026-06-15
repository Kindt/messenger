package com.avandocmsg.messenger.api.cache;

import com.avandocmsg.messenger.common.dto.ReadCacheInvalidateEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.application.ReadCacheCoordinator;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Applies distributed read-cache invalidation from message-pipeline fan-out (spec 006 T302).
 */
public final class ReadCacheInvalidationSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReadCacheInvalidationSubscriber.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String QUEUE_GROUP = "core-api-cache-invalidate";

    private final ReadCachePort readCache;
    private final Dispatcher dispatcher;

    public ReadCacheInvalidationSubscriber(Connection connection, ReadCachePort readCache) {
        this.readCache = readCache;
        this.dispatcher = connection.createDispatcher(this::onMessage);
    }

    public void start() {
        if (!readCache.enabled()) {
            log.info("Read-cache invalidation subscriber skipped (cache disabled)");
            return;
        }
        dispatcher.subscribe(NatsSubjects.MSG_CACHE_INVALIDATE, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {})", NatsSubjects.MSG_CACHE_INVALIDATE, QUEUE_GROUP);
    }

    void onMessage(io.nats.client.Message msg) {
        if (!readCache.enabled()) {
            return;
        }
        try {
            apply(MAPPER.readValue(msg.getData(), ReadCacheInvalidateEvent.class));
        } catch (Exception e) {
            log.debug("read-cache invalidate event failed: {}", e.getMessage());
        }
    }

    void apply(ReadCacheInvalidateEvent event) {
        if (!readCache.enabled() || event.userIds() == null) {
            return;
        }
        for (var raw : event.userIds()) {
            var userId = parseUuid(raw);
            if (userId == null) {
                continue;
            }
            if (event.invalidateUnread()) {
                ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
            }
            if (event.invalidateChatList()) {
                ReadCacheCoordinator.invalidateChatList(readCache, userId);
            }
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            dispatcher.unsubscribe(NatsSubjects.MSG_CACHE_INVALIDATE);
        } catch (Exception e) {
            log.debug("read-cache subscriber close: {}", e.getMessage());
        }
    }
}
