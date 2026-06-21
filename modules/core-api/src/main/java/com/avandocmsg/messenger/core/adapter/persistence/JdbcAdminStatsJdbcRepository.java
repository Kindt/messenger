package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.DatabaseHealthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

/** JDBC reads for admin server stats, purge estimates, and MLS migration counters. */
public final class JdbcAdminStatsJdbcRepository implements DatabaseHealthPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAdminStatsJdbcRepository.class);

    private final DataSource dataSource;

    public JdbcAdminStatsJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean ping() {
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement("SELECT 1");
             var rs = st.executeQuery()) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    public TableCounts countMessagingTables() {
        try (var conn = dataSource.getConnection()) {
            long users = count(conn, "SELECT COUNT(*) FROM users");
            long chats = count(conn, "SELECT COUNT(*) FROM chats");
            long messages = count(conn, "SELECT COUNT(*) FROM messages");
            return new TableCounts(users, chats, messages, true);
        } catch (Exception e) {
            log.warn("countMessagingTables failed: {}", e.getMessage());
            return new TableCounts(0, 0, 0, false);
        }
    }

    public ExportJobStatusScan scanExportJobStatuses() {
        long total = 0;
        long queued = 0;
        long processing = 0;
        long completed = 0;
        long failed = 0;
        long cancelled = 0;
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement("SELECT status, COUNT(*) FROM export_jobs GROUP BY status");
             var rs = st.executeQuery()) {
            while (rs.next()) {
                var status = rs.getString(1);
                var c = rs.getLong(2);
                total += c;
                switch (status) {
                    case "queued" -> queued += c;
                    case "processing" -> processing += c;
                    case "export_v1", "stub_written" -> completed += c;
                    case "export_failed" -> failed += c;
                    case "export_cancelled" -> cancelled += c;
                    default -> { /* ignore unknown status values */ }
                }
            }
            return new ExportJobStatusScan(total, queued, processing, completed, failed, cancelled, true);
        } catch (Exception e) {
            log.warn("scanExportJobStatuses failed: {}", e.getMessage());
            return ExportJobStatusScan.unavailable();
        }
    }

    public long countAuditExportSince(Instant since) {
        var auditSince = Timestamp.from(since);
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                 "SELECT COUNT(*) FROM audit_events WHERE action LIKE 'export.%' AND occurred_at >= ?")) {
            st.setTimestamp(1, auditSince);
            try (var rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            log.warn("countAuditExportSince failed: {}", e.getMessage());
            return 0L;
        }
    }

    public long countAuditExportCancelledSince(Instant since) {
        var auditSince = Timestamp.from(since);
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                 """
                 SELECT COUNT(*) FROM audit_events
                 WHERE action IN ('export.cancelled', 'export.admin_cancelled') AND occurred_at >= ?
                 """)) {
            st.setTimestamp(1, auditSince);
            try (var rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            log.warn("countAuditExportCancelledSince failed: {}", e.getMessage());
            return 0L;
        }
    }

    public long countPendingHotRowCandidates() {
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
            log.warn("countPendingHotRowCandidates failed: {}", e.getMessage());
        }
        return 0L;
    }

    public long countPendingMlsMigrations() {
        var sql = """
            SELECT COUNT(DISTINCT s.chat_id) AS c
            FROM e2ee_sessions s
            LEFT JOIN mls_group_state g ON g.chat_id = s.chat_id
            WHERE g.chat_id IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("c");
            }
        } catch (Exception e) {
            log.warn("countPendingMlsMigrations failed: {}", e.getMessage());
        }
        return 0L;
    }

    private static long count(java.sql.Connection conn, String sql) throws Exception {
        try (var st = conn.prepareStatement(sql);
             var rs = st.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }
    }

    public record TableCounts(long users, long chats, long messages, boolean ok) {}

    public record ExportJobStatusScan(
        long total,
        long queued,
        long processing,
        long completed,
        long failed,
        long cancelled,
        boolean ok
    ) {
        static ExportJobStatusScan unavailable() {
            return new ExportJobStatusScan(0, 0, 0, 0, 0, 0, false);
        }
    }
}
