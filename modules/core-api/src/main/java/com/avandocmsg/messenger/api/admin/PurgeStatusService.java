package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.core.port.AuditPort;

import javax.sql.DataSource;

/** Aggregates purge stats from {@code audit_events} and optional pending-row estimate. */
public final class PurgeStatusService {

    private static final String PURGED_ACTION = "message.retention.hot_row_purged";
    private static final String PURGE_ERROR_ACTION = "message.retention.purge_error";

    private final DataSource dataSource;
    private final AuditPort auditPort;

    public PurgeStatusService(DataSource dataSource, AuditPort auditPort) {
        this.dataSource = dataSource;
        this.auditPort = auditPort;
    }

    public com.avandocmsg.messenger.api.admin.dto.PurgeStatusResponse status() {
        var total = auditPort.countByAction(PURGED_ACTION);
        var last = auditPort.latestOccurredAtByAction(PURGED_ACTION).orElse(null);
        var errors = auditPort.countByAction(PURGE_ERROR_ACTION);
        var pending = countPendingHotRowCandidates();
        return new com.avandocmsg.messenger.api.admin.dto.PurgeStatusResponse(total, last, errors, pending);
    }

    private long countPendingHotRowCandidates() {
        if (dataSource == null) {
            return 0L;
        }
        var sql = """
            SELECT COUNT(*) AS c
            FROM messages m
            INNER JOIN retention_hot_body_applied r ON r.message_id = m.id
            WHERE m.deleted = false AND m.content IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("c");
            }
        } catch (Exception e) {
            return 0L;
        }
        return 0L;
    }
}
