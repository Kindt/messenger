package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.core.port.AdminMetricsQueryPort;
import com.avandocmsg.messenger.core.port.AuditPort;

/** Aggregates purge stats from {@code audit_events} and optional pending-row estimate. */
public final class PurgeStatusService {

    private static final String PURGED_ACTION = "message.retention.hot_row_purged";
    private static final String PURGE_ERROR_ACTION = "message.retention.purge_error";

    private final AdminMetricsQueryPort adminMetricsQueryPort;
    private final AuditPort auditPort;

    public PurgeStatusService(AdminMetricsQueryPort adminMetricsQueryPort, AuditPort auditPort) {
        this.adminMetricsQueryPort = adminMetricsQueryPort;
        this.auditPort = auditPort;
    }

    public com.avandocmsg.messenger.api.admin.dto.PurgeStatusResponse status() {
        var total = auditPort.countByAction(PURGED_ACTION);
        var last = auditPort.latestOccurredAtByAction(PURGED_ACTION).orElse(null);
        var errors = auditPort.countByAction(PURGE_ERROR_ACTION);
        var pending = adminMetricsQueryPort.countPendingHotRowCandidates();
        return new com.avandocmsg.messenger.api.admin.dto.PurgeStatusResponse(total, last, errors, pending);
    }
}
