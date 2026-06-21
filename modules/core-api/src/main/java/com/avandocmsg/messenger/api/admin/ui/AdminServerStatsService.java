package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.core.port.AdminMetricsQueryPort;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Сводная статистика для встроенной админ-панели «Статистика сервера».
 */
public final class AdminServerStatsService implements AdminStatsPort {

    private static final Logger log = LoggerFactory.getLogger(AdminServerStatsService.class);

    private final AdminMetricsQueryPort adminMetricsQueryPort;
    private final AppConfig appConfig;
    private final NatsConnectionStatus natsConnectionStatus;
    private final RedisProbe redisProbe;

    public AdminServerStatsService(
        AdminMetricsQueryPort adminMetricsQueryPort,
        AppConfig appConfig,
        NatsConnectionStatus natsConnectionStatus,
        RedisProbe redisProbe
    ) {
        this.adminMetricsQueryPort = adminMetricsQueryPort;
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

        boolean dbOk = adminMetricsQueryPort.ping();
        boolean redisOk = redisProbe.ping();
        boolean natsOk = natsConnectionStatus.natsClientConnected();

        var counts = adminMetricsQueryPort.countMessagingTables();
        var exportCompliance = scanExportCompliance();

        return new AdminServerStatsResponse(
            appConfig.version(),
            new AdminServerStatsResponse.JvmStats(heapUsed, heapCommitted, heapMax,
                rt.availableProcessors(), uptime),
            new AdminServerStatsResponse.DependencyHealth(dbOk, redisOk, natsOk),
            new AdminServerStatsResponse.TableCounts(counts.users(), counts.chats(), counts.messages(), counts.ok()),
            exportCompliance
        );
    }

    private AdminServerStatsResponse.ExportCompliance scanExportCompliance() {
        var jobScan = adminMetricsQueryPort.scanExportJobStatuses();
        if (!jobScan.ok()) {
            return AdminServerStatsResponse.ExportCompliance.unavailable();
        }
        var auditSince = Instant.now().minus(7, ChronoUnit.DAYS);
        long audit7d = adminMetricsQueryPort.countAuditExportSince(auditSince);
        long auditCancelled7d = adminMetricsQueryPort.countAuditExportCancelledSince(auditSince);
        int staleMinutes = appConfig.exportProcessingStaleMinutes();
        long processingStale = adminMetricsQueryPort.countProcessingStaleExportJobs(staleMinutes);
        return new AdminServerStatsResponse.ExportCompliance(
            true,
            jobScan.total(),
            jobScan.queued(),
            jobScan.processing(),
            processingStale,
            staleMinutes,
            jobScan.completed(),
            jobScan.failed(),
            jobScan.cancelled(),
            audit7d,
            auditCancelled7d);
    }
}
