package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/** Records {@code export.suggested} audit and optionally auto-queues export (shared by NATS subscriber and admin API). */
public final class ExportSuggestedHandler {

    private static final Logger log = LoggerFactory.getLogger(ExportSuggestedHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String AUDIT_ACTION = "export.suggested";
    static final String RESOURCE_TYPE_CHAT = "chat";

    private final AuditRepository auditRepository;
    private final Optional<ExportAutoQueueOnSuggested> autoQueue;

    public ExportSuggestedHandler(AuditRepository auditRepository) {
        this(auditRepository, Optional.empty());
    }

    public ExportSuggestedHandler(AuditRepository auditRepository, Optional<ExportAutoQueueOnSuggested> autoQueue) {
        this.auditRepository = auditRepository;
        this.autoQueue = autoQueue != null ? autoQueue : Optional.empty();
    }

    /**
     * @return job id when auto-queue created an export, empty when skipped or disabled
     */
    public Optional<UUID> handle(ExportSuggestedEvent event) throws JsonProcessingException {
        if (event == null || event.chatId() == null || event.chatId().isBlank()) {
            log.warn("Export suggested event missing chatId");
            return Optional.empty();
        }
        var details = MAPPER.createObjectNode();
        details.put("reason", event.reason() != null ? event.reason() : "");
        details.put("candidate_message_count", event.candidateMessageCount());
        details.put("suggested_at_epoch_ms", event.suggestedAtEpochMs());
        auditRepository.record(
            null,
            AUDIT_ACTION,
            RESOURCE_TYPE_CHAT,
            event.chatId(),
            MAPPER.writeValueAsString(details)
        );
        log.debug(
            "Recorded {} chatId={} candidates={}",
            AUDIT_ACTION,
            event.chatId(),
            event.candidateMessageCount()
        );
        return autoQueue.flatMap(q -> q.tryQueue(event));
    }
}
