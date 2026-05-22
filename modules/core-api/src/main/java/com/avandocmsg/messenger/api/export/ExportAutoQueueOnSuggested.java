package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * When {@link AppConfig#exportAutoQueueOnSuggestedEnabled()}, queues export on
 * {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EXPORT_SUGGESTED} with deduplication.
 */
public final class ExportAutoQueueOnSuggested {

    private static final Logger log = LoggerFactory.getLogger(ExportAutoQueueOnSuggested.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String AUDIT_AUTO_QUEUED = "export.auto_queued";
    static final String AUDIT_SKIPPED = "export.auto_queue_skipped";

    private final ExportJobEnqueuer enqueuer;
    private final ExportJobRepository exportJobRepository;
    private final ChatRepository chatRepository;
    private final AuditRepository auditRepository;
    private final Optional<UUID> actorUserIdOverride;
    private final int cooldownMinutes;

    public ExportAutoQueueOnSuggested(
        AppConfig appConfig,
        ExportJobEnqueuer enqueuer,
        ExportJobRepository exportJobRepository,
        ChatRepository chatRepository,
        AuditRepository auditRepository
    ) {
        this(
            enqueuer,
            exportJobRepository,
            chatRepository,
            auditRepository,
            appConfig.exportAutoQueueActorUserId(),
            appConfig.exportAutoQueueCooldownMinutes()
        );
    }

    ExportAutoQueueOnSuggested(
        ExportJobEnqueuer enqueuer,
        ExportJobRepository exportJobRepository,
        ChatRepository chatRepository,
        AuditRepository auditRepository,
        Optional<UUID> actorUserIdOverride,
        int cooldownMinutes
    ) {
        this.enqueuer = enqueuer;
        this.exportJobRepository = exportJobRepository;
        this.chatRepository = chatRepository;
        this.auditRepository = auditRepository;
        this.actorUserIdOverride = actorUserIdOverride;
        this.cooldownMinutes = cooldownMinutes;
    }

    public Optional<UUID> tryQueue(ExportSuggestedEvent event) {
        var chatId = parseUuid(event.chatId());
        if (chatId == null) {
            return Optional.empty();
        }
        if (exportJobRepository.hasBlockingJobForChat(chatId, cooldownMinutes)) {
            recordSkip(chatId, "cooldown_or_pending", event);
            log.debug("Auto export skipped (blocking job) chatId={}", chatId);
            return Optional.empty();
        }
        var actor = resolveActor(chatId);
        if (actor.isEmpty()) {
            recordSkip(chatId, "no_actor", event);
            log.warn("Auto export skipped: no actor for chatId={}", chatId);
            return Optional.empty();
        }
        try {
            var jobId = enqueuer.enqueue(chatId, actor.get(), "retention_suggested", event);
            recordAutoQueued(chatId, jobId, actor.get(), event);
            log.info("Auto-queued export jobId={} chatId={} actor={}", jobId, chatId, actor.get());
            return Optional.of(jobId);
        } catch (ExportJobEnqueuer.ExportEnqueueException e) {
            recordSkip(chatId, "enqueue_failed", event);
            log.warn("Auto export enqueue failed chatId={}: {}", chatId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<UUID> resolveActor(UUID chatId) {
        if (actorUserIdOverride.isPresent()) {
            return actorUserIdOverride;
        }
        return chatRepository.findOwnerId(chatId);
    }

    private void recordAutoQueued(UUID chatId, UUID jobId, UUID actor, ExportSuggestedEvent event) {
        auditRepository.record(
            actor,
            AUDIT_AUTO_QUEUED,
            "export_job",
            jobId.toString(),
            skipOrQueueDetails(chatId, event, "queued", jobId)
        );
    }

    private void recordSkip(UUID chatId, String reason, ExportSuggestedEvent event) {
        auditRepository.record(
            null,
            AUDIT_SKIPPED,
            "chat",
            chatId.toString(),
            skipOrQueueDetails(chatId, event, reason, null)
        );
    }

    private static String skipOrQueueDetails(
        UUID chatId,
        ExportSuggestedEvent event,
        String outcome,
        UUID jobId
    ) {
        try {
            ObjectNode node = MAPPER.createObjectNode()
                .put("chat_id", chatId.toString())
                .put("outcome", outcome);
            if (jobId != null) {
                node.put("job_id", jobId.toString());
            }
            if (event != null) {
                node.put("suggestion_reason", event.reason());
                node.put("candidate_message_count", event.candidateMessageCount());
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"chat_id\":\"" + chatId + "\",\"outcome\":\"" + outcome + "\"}";
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
}
