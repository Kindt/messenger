package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.api.export.ExportJobStaleCounts;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Сводная статистика для встроенной админ-панели «Статистика сервера».
 */
public final class AdminServerStatsService implements AdminStatsPort {

    private static final Logger log = LoggerFactory.getLogger(AdminServerStatsService.class);

    private final DataSource dataSource;
    private final AppConfig appConfig;
    private final NatsConnectionStatus natsConnectionStatus;
    private final RedisProbe redisProbe;

    public AdminServerStatsService(
        DataSource dataSource,
        AppConfig appConfig,
        NatsConnectionStatus natsConnectionStatus,
        RedisProbe redisProbe
    ) {
        this.dataSource = dataSource;
        this.appConfig = appConfig;
        this.natsConnectionStatus = natsConnectionStatus;
        this.redisProbe = redisProbe;
    }

    @Override
    public AdminServerStatsResponse snapshot() {
        var rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapCommitted = rt.totalMemory();
        long heapMax = rt.maxMemory();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        boolean dbOk = pingDatabase();
        boolean redisOk = redisProbe.ping();
        boolean natsOk = natsConnectionStatus.natsClientConnected();

        TableScan counts = countTables();
        var exportCompliance = scanExportCompliance();

        return new AdminServerStatsResponse(
            appConfig.version(),
            new AdminServerStatsResponse.JvmStats(heapUsed, heapCommitted, heapMax,
                rt.availableProcessors(), uptime),
            new AdminServerStatsResponse.DependencyHealth(dbOk, redisOk, natsOk),
            new AdminServerStatsResponse.TableCounts(counts.users, counts.chats, counts.messages, counts.ok),
            exportCompliance
        );
    }

    private boolean pingDatabase() {
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement("SELECT 1");
             var rs = st.executeQuery()) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private TableScan countTables() {
        try (var conn = dataSource.getConnection()) {
            long users = count(conn, "SELECT COUNT(*) FROM users");
            long chats = count(conn, "SELECT COUNT(*) FROM chats");
            long messages = count(conn, "SELECT COUNT(*) FROM messages");
            return new TableScan(users, chats, messages, true);
        } catch (Exception e) {
            return new TableScan(0, 0, 0, false);
        }
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

    private AdminServerStatsResponse.ExportCompliance scanExportCompliance() {
        try (var conn = dataSource.getConnection()) {
            long total = 0;
            long queued = 0;
            long processing = 0;
            long completed = 0;
            long failed = 0;
            long cancelled = 0;
            try (var st = conn.prepareStatement("SELECT status, COUNT(*) FROM export_jobs GROUP BY status");
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
            }
            var auditSince = Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS));
            long audit7d;
            try (var st = conn.prepareStatement(
                "SELECT COUNT(*) FROM audit_events WHERE action LIKE 'export.%' AND occurred_at >= ?")) {
                st.setTimestamp(1, auditSince);
                try (var rs = st.executeQuery()) {
                    audit7d = rs.next() ? rs.getLong(1) : 0L;
                }
            }
            long auditCancelled7d;
            try (var st = conn.prepareStatement(
                """
                SELECT COUNT(*) FROM audit_events
                WHERE action IN ('export.cancelled', 'export.admin_cancelled') AND occurred_at >= ?
                """)) {
                st.setTimestamp(1, auditSince);
                try (var rs = st.executeQuery()) {
                    auditCancelled7d = rs.next() ? rs.getLong(1) : 0L;
                }
            }
            int staleMinutes = appConfig.exportProcessingStaleMinutes();
            long processingStale = 0;
            try {
                processingStale = ExportJobStaleCounts.countProcessingStale(dataSource, staleMinutes);
            } catch (Exception e) {
                log.warn("export processing stale count query failed (staleMinutes={}): {}", staleMinutes, e.getMessage());
            }
            return new AdminServerStatsResponse.ExportCompliance(
                true,
                total,
                queued,
                processing,
                processingStale,
                staleMinutes,
                completed,
                failed,
                cancelled,
                audit7d,
                auditCancelled7d);
        } catch (Exception e) {
            return AdminServerStatsResponse.ExportCompliance.unavailable();
        }
    }

    private record TableScan(long users, long chats, long messages, boolean ok) {}
}
