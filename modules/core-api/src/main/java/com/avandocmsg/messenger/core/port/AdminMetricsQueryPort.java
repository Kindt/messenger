package com.avandocmsg.messenger.core.port;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

/** Admin/metrics SQL reads (MLS migration backlog, export job stale counts). */
public interface AdminMetricsQueryPort {

    boolean ping();

    TableCounts countMessagingTables();

    ExportJobStatusScan scanExportJobStatuses();

    long countAuditExportSince(Instant since);

    long countAuditExportCancelledSince(Instant since);

    long countPendingMlsMigrations();

    List<UUID> listPendingMlsMigrationChatIds(int limit);

    long countProcessingStaleExportJobs(int staleMinutes);

    long countPendingHotRowCandidates();

    record TableCounts(long users, long chats, long messages, boolean ok) {}

    record ExportJobStatusScan(
        long total,
        long queued,
        long processing,
        long completed,
        long failed,
        long cancelled,
        boolean ok
    ) {
        public static ExportJobStatusScan unavailable() {
            return new ExportJobStatusScan(0, 0, 0, 0, 0, 0, false);
        }
    }
}
