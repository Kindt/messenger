package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAdminStatsJdbcRepository;
import com.avandocmsg.messenger.core.port.AuditPort;

import javax.sql.DataSource;

/** Aggregates purge stats from {@code audit_events} and optional pending-row estimate. */
public final class PurgeStatusService {

    private static final String PURGED_ACTION = "message.retention.hot_row_purged";
    private static final String PURGE_ERROR_ACTION = "message.retention.purge_error";

    private final JdbcAdminStatsJdbcRepository adminStatsJdbc;
    private final AuditPort auditPort;

    public PurgeStatusService(DataSource dataSource, AuditPort auditPort) {
        this.adminStatsJdbc = dataSource != null ? new JdbcAdminStatsJdbcRepository(dataSource) : null;
        this.auditPort = auditPort;
    }

    public com.avandocmsg.messenger.api.admin.dto.PurgeStatusResponse status() {
        var total = auditPort.countByAction(PURGED_ACTION);
        var last = auditPort.latestOccurredAtByAction(PURGED_ACTION).orElse(null);
        var errors = auditPort.countByAction(PURGE_ERROR_ACTION);
        var pending = adminStatsJdbc != null ? adminStatsJdbc.countPendingHotRowCandidates() : 0L;
        return new com.avandocmsg.messenger.api.admin.dto.PurgeStatusResponse(total, last, errors, pending);
    }
}
