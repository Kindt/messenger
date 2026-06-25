package com.avandocmsg.messenger.core.adapter.persistence;



import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;

import com.avandocmsg.messenger.core.port.AdminMetricsQueryPort;

import com.avandocmsg.messenger.core.port.DatabaseHealthPort;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import javax.sql.DataSource;

import java.sql.Timestamp;

import java.time.Instant;

import java.util.ArrayList;

import java.util.List;

import java.util.UUID;



/** JDBC reads for admin server stats, purge estimates, and MLS migration counters. */

public final class JdbcAdminStatsJdbcRepository implements DatabaseHealthPort, AdminMetricsQueryPort {



    private static final Logger log = LoggerFactory.getLogger(JdbcAdminStatsJdbcRepository.class);



    private final DataSource dataSource;



    public JdbcAdminStatsJdbcRepository(DataSource dataSource) {

        this.dataSource = dataSource;

    }



    @Override

    public boolean lightPing() {

        try (var conn = dataSource.getConnection()) {

            return conn.isValid(1);

        } catch (Exception e) {

            return false;

        }

    }



    @Override

    public boolean ping() {

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var st = conn.prepareStatement("SELECT 1")) {

                JdbcQuerySupport.applyDefaultTimeout(st);

                try (var rs = st.executeQuery()) {

                    return rs.next();

                }

            }

        } catch (Exception e) {

            return false;

        }

    }



    @Override

    public AdminMetricsQueryPort.TableCounts countMessagingTables() {

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            long users = count(conn, "SELECT COUNT(*) FROM users");

            long chats = count(conn, "SELECT COUNT(*) FROM chats");

            long messages = count(conn, "SELECT COUNT(*) FROM messages");

            return new AdminMetricsQueryPort.TableCounts(users, chats, messages, true);

        } catch (Exception e) {

            log.warn("countMessagingTables failed: {}", e.getMessage());

            return new AdminMetricsQueryPort.TableCounts(0, 0, 0, false);

        }

    }



    @Override

    public AdminMetricsQueryPort.ExportJobStatusScan scanExportJobStatuses() {

        long total = 0;

        long queued = 0;

        long processing = 0;

        long completed = 0;

        long failed = 0;

        long cancelled = 0;

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var st = conn.prepareStatement("SELECT status, COUNT(*) FROM export_jobs GROUP BY status")) {

                JdbcQuerySupport.applyDefaultTimeout(st);

                try (var rs = st.executeQuery()) {

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

                }

            }

            return new AdminMetricsQueryPort.ExportJobStatusScan(total, queued, processing, completed, failed, cancelled, true);

        } catch (Exception e) {

            log.warn("scanExportJobStatuses failed: {}", e.getMessage());

            return AdminMetricsQueryPort.ExportJobStatusScan.unavailable();

        }

    }



    @Override

    public long countAuditExportSince(Instant since) {

        var auditSince = Timestamp.from(since);

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var st = conn.prepareStatement(

                "SELECT COUNT(*) FROM audit_events WHERE action LIKE 'export.%' AND occurred_at >= ?")) {

                JdbcQuerySupport.applyDefaultTimeout(st);

                st.setTimestamp(1, auditSince);

                try (var rs = st.executeQuery()) {

                    return rs.next() ? rs.getLong(1) : 0L;

                }

            }

        } catch (Exception e) {

            log.warn("countAuditExportSince failed: {}", e.getMessage());

            return 0L;

        }

    }



    @Override

    public long countAuditExportCancelledSince(Instant since) {

        var auditSince = Timestamp.from(since);

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var st = conn.prepareStatement(

                """

                SELECT COUNT(*) FROM audit_events

                WHERE action IN ('export.cancelled', 'export.admin_cancelled') AND occurred_at >= ?

                """)) {

                JdbcQuerySupport.applyDefaultTimeout(st);

                st.setTimestamp(1, auditSince);

                try (var rs = st.executeQuery()) {

                    return rs.next() ? rs.getLong(1) : 0L;

                }

            }

        } catch (Exception e) {

            log.warn("countAuditExportCancelledSince failed: {}", e.getMessage());

            return 0L;

        }

    }



    @Override

    public long countPendingHotRowCandidates() {

        var sql = """

            SELECT COUNT(*) AS c

            FROM messages m

            INNER JOIN retention_hot_body_applied r ON r.message_id = m.id

            WHERE m.deleted = false AND m.content IS NULL

            """;

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var ps = conn.prepareStatement(sql)) {

                JdbcQuerySupport.applyDefaultTimeout(ps);

                try (var rs = ps.executeQuery()) {

                    if (rs.next()) {

                        return rs.getLong("c");

                    }

                }

            }

        } catch (Exception e) {

            log.warn("countPendingHotRowCandidates failed: {}", e.getMessage());

        }

        return 0L;

    }



    @Override

    public long countPendingMlsMigrations() {

        var sql = """

            SELECT COUNT(DISTINCT s.chat_id) AS c

            FROM e2ee_sessions s

            LEFT JOIN mls_group_state g ON g.chat_id = s.chat_id

            WHERE g.chat_id IS NULL

            """;

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var stmt = conn.prepareStatement(sql)) {

                JdbcQuerySupport.applyDefaultTimeout(stmt);

                try (var rs = stmt.executeQuery()) {

                    if (rs.next()) {

                        return rs.getLong("c");

                    }

                }

            }

        } catch (Exception e) {

            log.warn("countPendingMlsMigrations failed: {}", e.getMessage());

        }

        return 0L;

    }



    @Override

    public List<UUID> listPendingMlsMigrationChatIds(int limit) {

        if (limit <= 0) {

            return List.of();

        }

        var sql = """

            SELECT s.chat_id

            FROM e2ee_sessions s

            LEFT JOIN mls_group_state g ON g.chat_id = s.chat_id

            WHERE g.chat_id IS NULL

            GROUP BY s.chat_id

            ORDER BY MIN(s.updated_at) ASC

            LIMIT ?

            """;

        var out = new ArrayList<UUID>();

        try (var conn = dataSource.getConnection()) {

            JdbcConnectionSupport.prepareRead(conn);

            try (var stmt = conn.prepareStatement(sql)) {

                JdbcQuerySupport.applyDefaultTimeout(stmt);

                stmt.setInt(1, limit);

                try (var rs = stmt.executeQuery()) {

                    while (rs.next()) {

                        out.add(rs.getObject("chat_id", UUID.class));

                    }

                }

            }

        } catch (Exception e) {

            log.warn("listPendingMlsMigrationChatIds failed: {}", e.getMessage());

        }

        return out;

    }



    @Override

    public long countProcessingStaleExportJobs(int staleMinutes) {

        try {

            return new JdbcExportJobJdbcRepository(dataSource).countProcessingStale(staleMinutes);

        } catch (Exception e) {

            log.warn("countProcessingStaleExportJobs failed (staleMinutes={}): {}", staleMinutes, e.getMessage());

            return 0L;

        }

    }



    private static long count(java.sql.Connection conn, String sql) throws Exception {

        try (var st = conn.prepareStatement(sql)) {

            JdbcQuerySupport.applyDefaultTimeout(st);

            try (var rs = st.executeQuery()) {

                if (rs.next()) {

                    return rs.getLong(1);

                }

                return 0L;

            }

        }

    }

}
