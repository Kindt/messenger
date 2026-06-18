package com.avandocmsg.messenger.api.hotplug;

import com.avandocmsg.messenger.common.hotplug.HotPlugRegistry;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks indexer service presence via hot-plug heartbeat subjects.
 */
public final class IndexerHotPlugMonitor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IndexerHotPlugMonitor.class);

    private final Dispatcher dispatcher;
    private final HotPlugRegistry registry;
    private final String indexerServiceId;

    public IndexerHotPlugMonitor(Connection connection, long heartbeatTtlMs, String indexerServiceId) {
        this.registry = new HotPlugRegistry(heartbeatTtlMs);
        this.indexerServiceId = normalize(indexerServiceId);
        this.dispatcher = connection.createDispatcher(this::onMessage);
    }

    public void start() {
        dispatcher.subscribe(NatsSubjects.SVC_HEARTBEAT_WILDCARD);
        log.info("Subscribed to hot-plug heartbeats: {}", NatsSubjects.SVC_HEARTBEAT_WILDCARD);
    }

    public boolean isIndexerPresent() {
        var now = System.currentTimeMillis();
        registry.evictStale(now);
        return registry.isPresent(indexerServiceId, now);
    }

    /** Shared hot-plug registry (NATS heartbeats from workers). */
    public HotPlugRegistry registry() {
        return registry;
    }

    void onMessage(io.nats.client.Message msg) {
        try {
            registry.onHeartbeatPayload(msg.getData(), System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("Invalid heartbeat payload on {}: {}", msg.getSubject(), e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            dispatcher.unsubscribe(NatsSubjects.SVC_HEARTBEAT_WILDCARD);
        } catch (Exception e) {
            log.debug("Unsubscribe {}: {}", NatsSubjects.SVC_HEARTBEAT_WILDCARD, e.getMessage());
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "indexer-service";
        }
        return value.trim();
    }
}
