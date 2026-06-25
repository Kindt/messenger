package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.concurrent.InterruptibleWait;
import com.avandocmsg.messenger.common.scheduling.ScheduledTaskSupport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Воркер пакетной ретенции Hot DB: ping, затем (PostgreSQL + политика) вынос тела в MinIO (бакет {@code RETENTION_MINIO_BUCKET}
 * или {@code MINIO_BUCKET}, префикс {@code RETENTION_MINIO_OBJECT_PREFIX}; опционально создание бакета — {@code RETENTION_ENSURE_MINIO_BUCKET})
 * и очистка {@code messages.content},
 * публикация {@code msg.event.index} с {@code index_op=update}. См. {@code docs/RETENTION_AND_DEEP_ARCHIVE.md} §10 этап 3.
 */
public final class RetentionWorker {
    private static final Logger log = LoggerFactory.getLogger(RetentionWorker.class);

    private final boolean enabled;
    private final int scanIntervalSeconds;
    private final DataSource dataSource;
    private final String jdbcUrl;
    private final Connection nats;
    private final MinioClient minioClient;
    private final boolean minioEnabled;
    /** Бакет для записи снимков ретенции (может отличаться от {@code MINIO_BUCKET}). */
    private final String retentionWriteBucket;
    private final String retentionObjectPrefix;
    private final RetentionPlatformDefaults platformDefaults;
    private final int batchLimit;
    private final boolean requireMinio;
    private final boolean useAppliedLog;
    private final boolean auditEnabled;
    private final int bulkAuditMinCleared;
    private final int initialDelaySeconds;
    private final boolean skipSnapshotIfDeepExists;
    /** Значение {@code MINIO_BUCKET} (дефолт deep-archiver), для сравнения с бакетом записи ретенции. */
    private final String minioDefaultBucket;
    /** {@code 0} — без {@link java.sql.Statement#setQueryTimeout(int)} на SELECT/UPDATE hot-body. */
    private final int jdbcQueryTimeoutSeconds;
    /** {@code 0} — без паузы между сообщениями в одном проходе hot-body. */
    private final int interMessageDelayMs;
    /** {@code 0} — снимок MinIO только в памяти ({@code writeValueAsBytes}); иначе см. {@code RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES}. */
    private final long snapshotTempfileThresholdBytes;
    /** См. {@code RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES}; {@link Long#MAX_VALUE} — только {@code putObject} для temp-file. */
    private final long minioMultipartThresholdBytes;
    private final boolean dryRun;
    /** When {@code true} and JDBC URL is PostgreSQL, hot-body pass uses one connection and {@code pg_try_advisory_lock}. */
    private final boolean useAdvisoryLock;
    /** Cooperative stop between scan passes; also set on interrupt in the main wait loop. */
    private final AtomicBoolean shutdownRequested;
    /** Single-thread scheduler for hot-body passes; {@code null} until {@link #run()} when enabled. */
    private volatile ScheduledExecutorService scanExecutor;
    private final UserMessageSource workerMessages;

    RetentionWorker(
        boolean enabled,
        int scanIntervalSeconds,
        DataSource dataSource,
        String jdbcUrl,
        Connection nats,
        MinioClient minioClient,
        boolean minioEnabled,
        String retentionWriteBucket,
        String retentionObjectPrefix,
        RetentionPlatformDefaults platformDefaults,
        int batchLimit,
        boolean requireMinio,
        boolean useAppliedLog,
        boolean auditEnabled,
        int bulkAuditMinCleared,
        int initialDelaySeconds,
        boolean skipSnapshotIfDeepExists,
        String minioDefaultBucket,
        int jdbcQueryTimeoutSeconds,
        int interMessageDelayMs,
        long snapshotTempfileThresholdBytes,
        long minioMultipartThresholdBytes,
        boolean dryRun,
        boolean useAdvisoryLock,
        AtomicBoolean shutdownRequested,
        UserMessageSource workerMessages
    ) {
        this.enabled = enabled;
        this.scanIntervalSeconds = Math.max(5, scanIntervalSeconds);
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl != null ? jdbcUrl : "";
        this.nats = nats;
        this.minioClient = minioClient;
        this.minioEnabled = minioEnabled;
        this.retentionWriteBucket = retentionWriteBucket;
        this.retentionObjectPrefix = retentionObjectPrefix;
        this.platformDefaults = platformDefaults;
        this.batchLimit = batchLimit;
        this.requireMinio = requireMinio;
        this.useAppliedLog = useAppliedLog;
        this.auditEnabled = auditEnabled;
        this.bulkAuditMinCleared = Math.max(0, bulkAuditMinCleared);
        this.initialDelaySeconds = Math.max(0, initialDelaySeconds);
        this.skipSnapshotIfDeepExists = skipSnapshotIfDeepExists;
        this.minioDefaultBucket = minioDefaultBucket != null ? minioDefaultBucket : "";
        this.jdbcQueryTimeoutSeconds = Math.max(0, jdbcQueryTimeoutSeconds);
        this.interMessageDelayMs = Math.max(0, interMessageDelayMs);
        this.snapshotTempfileThresholdBytes = Math.max(0, snapshotTempfileThresholdBytes);
        this.minioMultipartThresholdBytes = minioMultipartThresholdBytes;
        this.dryRun = dryRun;
        this.useAdvisoryLock = useAdvisoryLock;
        this.shutdownRequested = shutdownRequested;
        this.workerMessages = workerMessages;
    }

    void run() throws Exception {
        if (!enabled) {
            log.info(workerMessages.get("worker.retention.disabled"));
            Thread.currentThread().join();
            return;
        }
        if (dataSource == null || nats == null) {
            log.error(workerMessages.get("worker.retention.requires_db_nats"));
            System.exit(1);
            return;
        }
        log.info(workerMessages.format("worker.retention.enabled_config",
            scanIntervalSeconds,
            initialDelaySeconds,
            batchLimit,
            requireMinio,
            useAppliedLog,
            auditEnabled,
            bulkAuditMinCleared,
            retentionWriteBucket,
            retentionObjectPrefix,
            skipSnapshotIfDeepExists,
            minioDefaultBucket,
            RetentionPlatformDefaults.jdbcLooksLikePostgres(jdbcUrl),
            jdbcQueryTimeoutSeconds,
            interMessageDelayMs,
            snapshotTempfileThresholdBytes,
            minioMultipartThresholdBytes,
            dryRun,
            useAdvisoryLock
        ));
        if (initialDelaySeconds > 0) {
            log.info(workerMessages.format("worker.retention.first_scan_delay", initialDelaySeconds));
        }
        scanExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "retention-worker-scan");
            t.setDaemon(false);
            return t;
        });
        Runnable pass = () -> {
            if (shutdownRequested.get()) {
                return;
            }
            try {
                pingHotDb();
                if (RetentionPlatformDefaults.jdbcLooksLikePostgres(jdbcUrl)) {
                    RetentionHotBodyJanitor.runOnce(
                        dataSource,
                        nats,
                        minioClient,
                        minioEnabled,
                        retentionWriteBucket,
                        retentionObjectPrefix,
                        platformDefaults,
                        batchLimit,
                        requireMinio,
                        useAppliedLog,
                        auditEnabled,
                        bulkAuditMinCleared,
                        skipSnapshotIfDeepExists,
                        minioDefaultBucket,
                        jdbcQueryTimeoutSeconds,
                        interMessageDelayMs,
                        snapshotTempfileThresholdBytes,
                        minioMultipartThresholdBytes,
                        dryRun,
                        jdbcUrl,
                        useAdvisoryLock,
                        workerMessages
                    );
                    RetentionHotRowPurger.purgeHotRows(
                        dataSource,
                        nats,
                        platformDefaults,
                        RetentionPlatformDefaults.purgeBatchLimitFromEnv(),
                        RetentionPlatformDefaults.exportRequiredBeforePurgeFromEnv(),
                        auditEnabled,
                        jdbcQueryTimeoutSeconds,
                        dryRun,
                        workerMessages
                    );
                    ReadReceiptRetentionJanitor.purgeOldReceipts(
                        dataSource, RetentionPlatformDefaults.readReceiptRetentionDaysFromEnv(), workerMessages);
                    FileRetentionJanitor.process(
                        dataSource,
                        minioClient,
                        minioEnabled,
                        minioDefaultBucket,
                        RetentionPlatformDefaults.fileMetadataMinAgeDaysFromEnv(),
                        RetentionPlatformDefaults.fileCleanupBatchLimitFromEnv(),
                        auditEnabled,
                        dryRun,
                        workerMessages
                    );
                } else {
                    log.warn(workerMessages.get("worker.retention.postgres_skip"));
                }
            } catch (Exception e) {
                log.error(workerMessages.get("worker.retention.scan_failed"), e);
            }
        };
        ScheduledTaskSupport.scheduleWithFixedDelayAndJitter(
            scanExecutor,
            pass,
            TimeUnit.SECONDS.toMillis(initialDelaySeconds),
            TimeUnit.SECONDS.toMillis(scanIntervalSeconds),
            TimeUnit.SECONDS.toMillis(Math.min(60L, scanIntervalSeconds)));
        while (!shutdownRequested.get()) {
            if (InterruptibleWait.sleepMillis(500)) {
                shutdownRequested.set(true);
            }
        }
    }

    private void pingHotDb() {
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement("SELECT 1");
             var rs = st.executeQuery()) {
            if (rs.next()) {
                log.debug(workerMessages.get("worker.retention.db_ping_ok"));
            }
        } catch (SQLException e) {
            RetentionMetrics.dbPingFailed();
            log.warn(workerMessages.format("worker.retention.db_ping_failed", e.getMessage()));
        }
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
        var enabled = Boolean.parseBoolean(System.getenv().getOrDefault("RETENTION_WORKER_ENABLED", "false"));
        var interval = parsePositiveInt(System.getenv("RETENTION_SCAN_INTERVAL_SECONDS"), 3600);
        var jdbcUrl = System.getenv("DB_JDBC_URL");
        DataSource ds = null;
        Connection nats = null;
        MinioClient minioClient = null;
        var minioOk = false;
        if (enabled) {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                log.error(workerMessages.get("worker.retention.requires_db"));
                System.exit(1);
            }
            var user = System.getenv().getOrDefault("DB_USER", "avandocmsg");
            var password = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
            var cfg = new HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(user);
            cfg.setPassword(password);
            cfg.setMaximumPoolSize(3);
            cfg.setPoolName("retention-worker");
            ds = new HikariDataSource(cfg);

            var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
            try {
                var options = NatsConnectionOptions.clientBuilder(natsUrl, "retention-worker").build();
                nats = Nats.connect(options);
                log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
            } catch (Exception e) {
                log.error(workerMessages.get("worker.common.nats_connect_failed"), e);
                System.exit(1);
            }

            var endpoint = System.getenv("MINIO_ENDPOINT");
            var access = System.getenv("MINIO_ACCESS_KEY");
            var secret = System.getenv("MINIO_SECRET_KEY");
            minioOk = endpoint != null && !endpoint.isBlank()
                && access != null && !access.isBlank()
                && secret != null && !secret.isBlank();
            if (minioOk) {
                var region = System.getenv("MINIO_REGION");
                var builder = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(access, secret);
                if (region != null && !region.isBlank()) {
                    builder.region(region);
                }
                minioClient = builder.build();
            }
        }

        var platform = RetentionPlatformDefaults.fromEnv();
        var batch = RetentionPlatformDefaults.batchLimitFromEnv();
        var requireMinio = RetentionPlatformDefaults.requireMinioFromEnv();
        var defaultBucket = RetentionPlatformDefaults.minioBucketFromEnv();
        var retentionBucket = RetentionPlatformDefaults.retentionWriteBucketFromEnv(defaultBucket);
        var objectPrefix = RetentionPlatformDefaults.retentionObjectPrefixFromEnv();
        var useAppliedLog = RetentionPlatformDefaults.useAppliedLogFromEnv();
        var auditEnabled = RetentionPlatformDefaults.auditEnabledFromEnv();
        var bulkAuditMinCleared = RetentionPlatformDefaults.bulkAuditMinClearedFromEnv();
        var initialDelay = RetentionPlatformDefaults.initialDelaySecondsFromEnv();
        var skipSnapshotIfDeepExists = RetentionPlatformDefaults.skipSnapshotIfDeepExistsFromEnv();
        var dryRun = RetentionPlatformDefaults.dryRunFromEnv();
        var useAdvisoryLock = RetentionPlatformDefaults.useAdvisoryLockFromEnv();
        var jdbcQueryTimeoutSeconds = RetentionPlatformDefaults.jdbcQueryTimeoutSecondsFromEnv();
        var interMessageDelayMs = RetentionPlatformDefaults.interMessageDelayMsFromEnv();
        var snapshotTempfileThresholdBytes = RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromEnv();
        var minioMultipartThresholdBytes = RetentionPlatformDefaults.minioMultipartThresholdBytesFromEnv();
        if (dryRun) {
            log.warn(workerMessages.get("worker.retention.dry_run_warn"));
        }

        if (enabled && minioOk && minioClient != null && RetentionPlatformDefaults.ensureMinioBucketFromEnv()) {
            RetentionMinioBootstrap.ensureBucketExists(minioClient, retentionBucket, workerMessages);
        }

        final var healthDs = ds;
        final var healthNats = nats;
        final var healthMinioClient = minioClient;
        final var healthMinioOk = minioOk;
        RetentionHealthProbe healthProbe = () -> {
            if (!enabled) {
                return true;
            }
            if (healthDs == null || healthNats == null) {
                return false;
            }
            try (var conn = healthDs.getConnection();
                 var st = conn.prepareStatement("SELECT 1");
                 var rs = st.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
            } catch (SQLException e) {
                return false;
            }
            if (healthNats.getStatus() != Connection.Status.CONNECTED) {
                return false;
            }
            if (requireMinio) {
                if (!healthMinioOk || healthMinioClient == null) {
                    return false;
                }
                try {
                    if (!healthMinioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(retentionBucket).build()
                    )) {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }
            return true;
        };


        var metricsPort = RetentionPlatformDefaults.metricsPortFromEnv();
        RetentionMetricsHttpServer metricsServer = null;
        if (metricsPort > 0) {
            DefaultExports.initialize();
            try {
                metricsServer = RetentionMetricsHttpServer.start(metricsPort, healthProbe, workerMessages);
                log.info(workerMessages.format("worker.retention.metrics_url", metricsServer.getPort()));
            } catch (IOException e) {
                log.error(workerMessages.format("worker.retention.metrics_start_failed", metricsPort), e);
                System.exit(1);
            }
        }

        var shutdownRequested = new AtomicBoolean(false);
        var hookStarted = new AtomicBoolean(false);
        var worker = new RetentionWorker(
            enabled,
            interval,
            ds,
            jdbcUrl != null ? jdbcUrl : "",
            nats,
            minioClient,
            minioOk,
            retentionBucket,
            objectPrefix,
            platform,
            batch,
            requireMinio,
            useAppliedLog,
            auditEnabled,
            bulkAuditMinCleared,
            initialDelay,
            skipSnapshotIfDeepExists,
            defaultBucket,
            jdbcQueryTimeoutSeconds,
            interMessageDelayMs,
            snapshotTempfileThresholdBytes,
            minioMultipartThresholdBytes,
            dryRun,
            useAdvisoryLock,
            shutdownRequested,
            workerMessages
        );
        var metricsServerFinal = metricsServer;
        final Connection natsShutdown = nats;
        final DataSource dsShutdown = ds;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!hookStarted.compareAndSet(false, true)) {
                return;
            }
            log.info(workerMessages.get("worker.retention.shutdown_started"));
            shutdownRequested.set(true);
            RetentionShutdown.shutdownScanExecutorQuietly(worker.scanExecutor, RetentionShutdown.DEFAULT_EXECUTOR_AWAIT_SECONDS, workerMessages);
            List<AutoCloseable> closeInOrder = new ArrayList<>(3);
            if (metricsServerFinal != null) {
                closeInOrder.add(metricsServerFinal);
            }
            if (natsShutdown != null) {
                closeInOrder.add(natsShutdown::close);
            }
            if (dsShutdown instanceof HikariDataSource h) {
                closeInOrder.add(h);
            }
            RetentionShutdown.runCloseables(closeInOrder, workerMessages);
            log.info(workerMessages.get("worker.retention.shutdown_complete"));
        }, "retention-worker-shutdown"));
        try {
            worker.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info(workerMessages.get("worker.common.interrupted"));
        } catch (Exception e) {
            log.error(workerMessages.get("worker.retention.failed"), e);
            System.exit(1);
        }
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
