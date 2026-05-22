package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ExportReplayCompleteEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Subscribes to {@link NatsSubjects#MSG_EXPORT_REPLAY_COMPLETE} and syncs {@code export_jobs} when the worker
 * finishes (redundant with worker JDBC, covers missed DB updates).
 */
public final class ExportReplayCompleteSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExportReplayCompleteSubscriber.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String QUEUE_GROUP = "core-api-export-complete";

    private final ExportJobRepository exportJobRepository;
    private final Dispatcher dispatcher;

    public ExportReplayCompleteSubscriber(Connection connection, ExportJobRepository exportJobRepository) {
        this.exportJobRepository = exportJobRepository;
        this.dispatcher = connection.createDispatcher(this::onMessage);
    }

    public void start() {
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {})", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, QUEUE_GROUP);
    }

    void onMessage(io.nats.client.Message msg) {
        try {
            var event = MAPPER.readValue(msg.getData(), ExportReplayCompleteEvent.class);
            if (event.jobId() == null || event.jobId().isBlank()) {
                log.warn("Export complete event missing jobId");
                return;
            }
            var jobId = parseUuid(event.jobId());
            if (jobId == null) {
                log.warn("Export complete event has invalid jobId: {}", event.jobId());
                return;
            }
            if (exportJobRepository.applyCompleteIfPending(
                jobId, event.status(), event.outputPath(), event.messageTtlFilterApplied())) {
                log.info("export_jobs synced from NATS jobId={} status={}", jobId, event.status());
            } else {
                log.debug("export_jobs complete sync skipped (already terminal or unknown job) jobId={}", jobId);
            }
        } catch (Exception e) {
            log.warn("Failed to handle {}: {}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, e.getMessage());
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            dispatcher.unsubscribe(NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE);
        } catch (Exception e) {
            log.debug("Unsubscribe {}: {}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, e.getMessage());
        }
    }
}
