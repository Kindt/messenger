package com.avandocmsg.messenger.worker.exportreplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.UUID;

/** Best-effort {@code audit_events} row when an export job finishes. */
final class ExportAuditWriter {
    private static final Logger log = LoggerFactory.getLogger(ExportAuditWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final UserMessageSource workerMessages;

    ExportAuditWriter(DataSource dataSource, UserMessageSource workerMessages) {
        this.dataSource = dataSource;
        this.workerMessages = workerMessages;
    }

    void recordCompleted(UUID actorUserId, String jobId, String chatId, String status, String outputPath) {
        if (actorUserId == null) {
            return;
        }
        try {
            var details = MAPPER.createObjectNode()
                .put("status", status)
                .put("chat_id", chatId);
            if (outputPath != null) {
                details.put("output_path", outputPath);
            }
            var sql = """
                INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, details_json)
                VALUES (?, 'export.completed', 'export_job', ?, ?)
                """;
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, actorUserId);
                stmt.setString(2, jobId);
                stmt.setString(3, MAPPER.writeValueAsString(details));
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.export_replay.audit_insert_failed", jobId, e.getMessage()));
        }
    }
}
