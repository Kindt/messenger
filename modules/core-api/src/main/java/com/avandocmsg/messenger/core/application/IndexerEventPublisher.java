package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/** Hot-plug aware publisher for Solr indexer NATS events ({@link NatsSubjects#MSG_EVENT_INDEX}). */
public final class IndexerEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(IndexerEventPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PENDING_INDEX_EVENTS = 2048;

    private final NatsOutboundPort natsOutbound;
    private final BooleanSupplier indexerAvailable;
    private final Deque<MessageWorkerEvent> pendingIndexEvents = new ArrayDeque<>();

    public IndexerEventPublisher(NatsOutboundPort natsOutbound) {
        this(natsOutbound, () -> true);
    }

    public IndexerEventPublisher(NatsOutboundPort natsOutbound, BooleanSupplier indexerAvailable) {
        this.natsOutbound = natsOutbound;
        this.indexerAvailable = indexerAvailable != null ? indexerAvailable : () -> true;
    }

    public void publish(MessageWorkerEvent event) {
        if (natsOutbound == null || event == null) {
            return;
        }
        if (!indexerAvailable.getAsBoolean()) {
            enqueuePendingIndexEvent(event);
            log.info("Indexer service unavailable; queued {}", NatsSubjects.MSG_EVENT_INDEX);
            return;
        }
        flushPendingIndexEvents();
        publishNow(event);
    }

    private void publishNow(MessageWorkerEvent event) {
        try {
            natsOutbound.publish(NatsSubjects.MSG_EVENT_INDEX, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish {} for message {}", NatsSubjects.MSG_EVENT_INDEX, event.messageId(), e);
        }
    }

    private void flushPendingIndexEvents() {
        while (true) {
            MessageWorkerEvent pending;
            synchronized (pendingIndexEvents) {
                pending = pendingIndexEvents.pollFirst();
            }
            if (pending == null) {
                return;
            }
            try {
                natsOutbound.publish(NatsSubjects.MSG_EVENT_INDEX, MAPPER.writeValueAsBytes(pending));
            } catch (Exception e) {
                log.warn("Failed to publish queued {} for message {}", NatsSubjects.MSG_EVENT_INDEX, pending.messageId(), e);
                enqueuePendingIndexEvent(pending);
                return;
            }
        }
    }

    private void enqueuePendingIndexEvent(MessageWorkerEvent event) {
        synchronized (pendingIndexEvents) {
            if (pendingIndexEvents.size() >= MAX_PENDING_INDEX_EVENTS) {
                pendingIndexEvents.pollFirst();
            }
            pendingIndexEvents.addLast(event);
        }
    }
}
