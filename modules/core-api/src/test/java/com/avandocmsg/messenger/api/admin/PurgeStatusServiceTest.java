package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.core.port.AdminMetricsQueryPort;
import com.avandocmsg.messenger.core.port.AuditPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurgeStatusServiceTest {

    @Test
    void status_aggregatesAuditAndPending() {
        var metrics = new AdminMetricsQueryPort() {
            @Override
            public boolean ping() {
                return true;
            }

            @Override
            public TableCounts countMessagingTables() {
                return new TableCounts(0, 0, 0, true);
            }

            @Override
            public ExportJobStatusScan scanExportJobStatuses() {
                return ExportJobStatusScan.unavailable();
            }

            @Override
            public long countAuditExportSince(Instant since) {
                return 0;
            }

            @Override
            public long countAuditExportCancelledSince(Instant since) {
                return 0;
            }

            @Override
            public long countPendingMlsMigrations() {
                return 0;
            }

            @Override
            public List<UUID> listPendingMlsMigrationChatIds(int limit) {
                return List.of();
            }

            @Override
            public long countProcessingStaleExportJobs(int staleMinutes) {
                return 0;
            }

            @Override
            public long countPendingHotRowCandidates() {
                return 7;
            }
        };
        var audit = new AuditPort() {
            @Override
            @SuppressWarnings("java:S6213")
            public void record(UUID actorUserId, String action, String resourceType, String resourceId, // NOSONAR
                               String detailsJson) {
                // no-op stub: this test aggregates via countByAction / latestOccurredAtByAction only
            }

            @Override
            public List<AuditRow> listRecent(int limit) {
                return List.of();
            }

            @Override
            public List<AuditRow> listRecent(int limit, String actionEquals) {
                return List.of();
            }

            @Override
            public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
                return List.of();
            }

            @Override
            public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals,
                                             String resourceIdEquals) {
                return List.of();
            }

            @Override
            public long countByAction(String action) {
                return "message.retention.hot_row_purged".equals(action) ? 3 : 1;
            }

            @Override
            public Optional<Instant> latestOccurredAtByAction(String action) {
                return Optional.of(Instant.parse("2026-06-01T00:00:00Z"));
            }
        };
        var svc = new PurgeStatusService(metrics, audit);
        var res = svc.status();
        assertEquals(3, res.totalPurged());
        assertEquals(1, res.errorsCount());
        assertEquals(7, res.pendingCount());
    }
}
