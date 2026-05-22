package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Publishes {@link NatsSubjects#MSG_EXPORT_SUGGESTED} per chat before hot-body retention clears content. */
final class RetentionExportSuggester {

    private static final Logger log = LoggerFactory.getLogger(RetentionExportSuggester.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RetentionExportSuggester() {
    }

    static void publishForChatCounts(Connection nats, Map<UUID, Integer> candidateCountByChatId) {
        if (nats == null || candidateCountByChatId == null || candidateCountByChatId.isEmpty()) {
            return;
        }
        var now = Instant.now().toEpochMilli();
        for (var entry : candidateCountByChatId.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            try {
                var event = new ExportSuggestedEvent(
                    entry.getKey().toString(),
                    ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES,
                    entry.getValue(),
                    now
                );
                nats.publish(NatsSubjects.MSG_EXPORT_SUGGESTED, MAPPER.writeValueAsBytes(event));
                RetentionMetrics.exportSuggestedPublished();
                log.debug(
                    "Published {} chatId={} candidates={}",
                    NatsSubjects.MSG_EXPORT_SUGGESTED,
                    entry.getKey(),
                    entry.getValue()
                );
            } catch (Exception e) {
                log.warn("Failed to publish {} chatId={}: {}", NatsSubjects.MSG_EXPORT_SUGGESTED, entry.getKey(), e.getMessage());
            }
        }
    }
}
