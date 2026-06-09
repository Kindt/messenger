package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HOT_ROW_PURGED: delete hot {@code messages} rows after hot-body pass when policy allows.
 */
final class RetentionHotRowPurger {

    private static final Logger log = LoggerFactory.getLogger(RetentionHotRowPurger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AUDIT_ACTION = "message.retention.hot_row_purged";
    private static final String AUDIT_EXPORT_SKIP = "export.required_before_purge_skipped";

    private static final String SELECT_CANDIDATES = """
        SELECT m.id, m.chat_id
        FROM messages m
        INNER JOIN retention_hot_body_applied r ON r.message_id = m.id
        LEFT JOIN chat_retention_policy crp ON crp.chat_id = m.chat_id
        LEFT JOIN org_retention_policy orp ON orp.org_id = COALESCE(
            (SELECT u.org_id FROM chats ch2 JOIN users u ON u.id = ch2.owner_id
             WHERE ch2.id = m.chat_id AND u.org_id IS NOT NULL LIMIT 1),
            (SELECT u.org_id FROM chat_members cm JOIN users u ON u.id = cm.user_id
             WHERE cm.chat_id = m.chat_id AND u.org_id IS NOT NULL
             ORDER BY CASE cm.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, cm.user_id
             LIMIT 1)
        )
        CROSS JOIN LATERAL (
            SELECT
                CASE WHEN crp.chat_id IS NOT NULL THEN crp.legal_hold
                     ELSE COALESCE(orp.legal_hold, ?)
                END AS eff_legal
        ) pol
        WHERE m.deleted = false
          AND m.content IS NULL
          AND pol.eff_legal = false
        ORDER BY m.created_at ASC
        LIMIT ?
        """;

    private static final String DELETE_MESSAGE = """
        DELETE FROM messages WHERE id = ? AND deleted = false AND content IS NULL
        """;

    private static final String EXISTS_EXPORT = """
        SELECT 1 FROM export_jobs
        WHERE chat_id = ? AND status = 'export_v1'
        LIMIT 1
        """;

    private static final String INSERT_AUDIT = """
        INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, details_json)
        VALUES (NULL, ?, ?, ?, ?)
        """;

    private RetentionHotRowPurger() {
    }

    static String purgeCandidateSelectSql() {
        return SELECT_CANDIDATES;
    }

    static int purgeHotRows(
        DataSource dataSource,
        Connection nats,
        RetentionPlatformDefaults platform,
        int batchLimit,
        boolean exportRequiredBeforePurge,
        boolean auditEnabled,
        int jdbcQueryTimeoutSeconds,
        boolean dryRun,
        UserMessageSource workerMessages
    ) throws Exception {
        if (!RetentionPlatformDefaults.hotRowPurgeEnabledFromEnv()) {
            return 0;
        }
        var candidates = loadCandidates(dataSource, platform, batchLimit, jdbcQueryTimeoutSeconds);
        if (candidates.isEmpty()) {
            return 0;
        }
        if (dryRun) {
            log.info(workerMessages.format("worker.retention.hot_row.dry_run", candidates.size()));
            return 0;
        }
        var skippedChats = new HashSet<UUID>();
        int purged = 0;
        for (var c : candidates) {
            if (exportRequiredBeforePurge && !skippedChats.contains(c.chatId())) {
                if (!hasCompletedExport(dataSource, c.chatId(), jdbcQueryTimeoutSeconds)) {
                    skippedChats.add(c.chatId());
                    if (auditEnabled) {
                        insertAudit(dataSource, AUDIT_EXPORT_SKIP, "chat", c.chatId().toString(),
                            "{\"reason\":\"no_completed_export\"}");
                    }
                    continue;
                }
            }
            if (skippedChats.contains(c.chatId())) {
                continue;
            }
            if (purgeOne(dataSource, nats, c, auditEnabled, jdbcQueryTimeoutSeconds)) {
                purged++;
                RetentionMetrics.hotRowPurged();
            }
        }
        if (purged > 0) {
            log.info(workerMessages.format("worker.retention.hot_row.purged", purged));
        }
        return purged;
    }

    private static List<Candidate> loadCandidates(
        DataSource dataSource,
        RetentionPlatformDefaults platform,
        int batchLimit,
        int jdbcQueryTimeoutSeconds
    ) throws SQLException {
        var list = new ArrayList<Candidate>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(SELECT_CANDIDATES)) {
            if (jdbcQueryTimeoutSeconds > 0) {
                ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
            }
            ps.setBoolean(1, platform.defaultLegalHold());
            ps.setInt(2, batchLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Candidate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("chat_id", UUID.class)));
                }
            }
        }
        return list;
    }

    private static boolean hasCompletedExport(DataSource ds, UUID chatId, int queryTimeout) throws SQLException {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(EXISTS_EXPORT)) {
            if (queryTimeout > 0) {
                ps.setQueryTimeout(queryTimeout);
            }
            ps.setObject(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean purgeOne(
        DataSource dataSource,
        Connection nats,
        Candidate c,
        boolean auditEnabled,
        int jdbcQueryTimeoutSeconds
    ) throws Exception {
        int deleted;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(DELETE_MESSAGE)) {
            if (jdbcQueryTimeoutSeconds > 0) {
                ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
            }
            ps.setObject(1, c.messageId());
            deleted = ps.executeUpdate();
        }
        if (deleted <= 0) {
            return false;
        }
        publishIndexDelete(nats, c);
        if (auditEnabled) {
            insertAudit(dataSource, AUDIT_ACTION, "message", c.messageId().toString(),
                "{\"chat_id\":\"" + c.chatId() + "\"}");
        }
        return true;
    }

    private static void publishIndexDelete(Connection nats, Candidate c) throws Exception {
        if (nats == null) {
            return;
        }
        var evt = MessageWorkerEvent.forIndexDelete(c.messageId().toString());
        nats.publish(NatsSubjects.MSG_EVENT_INDEX, MAPPER.writeValueAsBytes(evt));
    }

    private static void insertAudit(
        DataSource dataSource,
        String action,
        String resourceType,
        String resourceId,
        String detailsJson
    ) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(INSERT_AUDIT)) {
            ps.setString(1, action);
            ps.setString(2, resourceType);
            ps.setString(3, resourceId);
            ps.setString(4, detailsJson);
            ps.executeUpdate();
        }
    }

    record Candidate(UUID messageId, UUID chatId) {
    }
}
