package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.metrics.ExportMetrics;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/** Publishes {@link NatsSubjects#MSG_EXPORT_REPLAY} and inserts {@code export_jobs} (shared by REST and auto-queue). */
public final class ExportJobEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(ExportJobEnqueuer.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final ExportJobPort exportJobPort;
    private final AuditPort auditPort;
    private final NatsOutboundPort natsOutbound;
    private final UuidGenerator uuidGenerator;

    public ExportJobEnqueuer(
        ExportJobPort exportJobPort,
        AuditPort auditPort,
        NatsOutboundPort natsOutbound,
        UuidGenerator uuidGenerator
    ) {
        this.exportJobPort = exportJobPort;
        this.auditPort = auditPort;
        this.natsOutbound = natsOutbound;
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * @param trigger audit detail, e.g. {@code api} or {@code retention_suggested}
     * @return new job id
     */
    public UUID enqueue(UUID chatId, UUID requestedBy, String trigger, ExportSuggestedEvent suggestion)
        throws ExportEnqueueException {
        var jobId = uuidGenerator.randomUuid();
        var job = new ExportReplayJob(jobId.toString(), chatId.toString(), requestedBy.toString());
        exportJobPort.insertQueued(jobId, chatId, requestedBy);
        try {
            natsOutbound.publish(NatsSubjects.MSG_EXPORT_REPLAY, MAPPER.writeValueAsBytes(job));
            natsOutbound.flush(Duration.ofSeconds(2));
        } catch (Exception e) {
            log.error("Failed to publish export job {}", jobId, e);
            exportJobPort.markTerminal(jobId, "export_failed", null);
            throw new ExportEnqueueException("nats publish failed", e);
        }
        auditPort.record(
            requestedBy,
            "export.requested",
            "export_job",
            jobId.toString(),
            auditDetails(chatId, trigger, suggestion)
        );
        ExportMetrics.jobEnqueued(trigger);
        return jobId;
    }

    private static String auditDetails(UUID chatId, String trigger, ExportSuggestedEvent suggestion)
        throws ExportEnqueueException {
        try {
            ObjectNode node = MAPPER.createObjectNode()
                .put("chat_id", chatId.toString())
                .put("trigger", trigger);
            if (suggestion != null) {
                node.put("suggestion_reason", suggestion.reason());
                node.put("candidate_message_count", suggestion.candidateMessageCount());
                node.put("suggested_at_epoch_ms", suggestion.suggestedAtEpochMs());
            }
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ExportEnqueueException("audit json failed", e);
        }
    }

    public static final class ExportEnqueueException extends Exception {
        public ExportEnqueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
