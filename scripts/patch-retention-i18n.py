#!/usr/bin/env python3
"""Patch retention worker and janitor classes for i18n."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

WM_FIELD = "    private final UserMessageSource workerMessages;\n"
WM_IMPORT = "import com.avandocmsg.messenger.common.i18n.UserMessageSource;\n"


def patch_retention_worker() -> None:
    p = ROOT / "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionWorker.java"
    text = p.read_text(encoding="utf-8")
    if "UserMessageSource workerMessages" not in text:
        text = text.replace(
            "import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;",
            "import com.avandocmsg.messenger.common.i18n.UserMessageSource;\nimport com.avandocmsg.messenger.common.i18n.WorkerMessageSources;",
        )
        text = text.replace(
            "    private volatile ScheduledExecutorService scanExecutor;\n",
            "    private volatile ScheduledExecutorService scanExecutor;\n" + WM_FIELD,
        )
        text = text.replace(
            "        AtomicBoolean shutdownRequested\n    ) {",
            "        AtomicBoolean shutdownRequested,\n        UserMessageSource workerMessages\n    ) {",
        )
        text = text.replace(
            "        this.shutdownRequested = shutdownRequested;\n    }",
            "        this.shutdownRequested = shutdownRequested;\n        this.workerMessages = workerMessages;\n    }",
        )
    reps = [
        ('log.info("Retention worker disabled (RETENTION_WORKER_ENABLED=false); idle. See docs/RETENTION_AND_DEEP_ARCHIVE.md");',
         'log.info(workerMessages.get("worker.retention.disabled"));'),
        ('log.error("RETENTION_WORKER_ENABLED=true requires DB_JDBC_URL and NATS_URL");',
         'log.error(workerMessages.get("worker.retention.requires_db_nats"));'),
        ('log.info("Retention worker: first scan scheduled after {}s initial delay", initialDelaySeconds);',
         'log.info(workerMessages.format("worker.retention.first_scan_delay", initialDelaySeconds));'),
        ('log.error("Retention scan pass failed", e);',
         'log.error(workerMessages.get("worker.retention.scan_failed"), e);'),
        ('log.debug("Hot DB ping OK");', 'log.debug(workerMessages.get("worker.retention.db_ping_ok"));'),
        ('log.warn("Hot DB ping failed: {}", e.getMessage());',
         'log.warn(workerMessages.format("worker.retention.db_ping_failed", e.getMessage()));'),
        ('log.error("RETENTION_WORKER_ENABLED=true requires DB_JDBC_URL");',
         'log.error(workerMessages.get("worker.retention.requires_db"));'),
        ('log.info("Connected to NATS at {}", natsUrl);',
         'log.info(workerMessages.format("worker.common.connected_nats", natsUrl));'),
        ('log.error("Failed to connect NATS", e);',
         'log.error(workerMessages.get("worker.common.nats_connect_failed"), e);'),
        ('log.error("Failed to start Prometheus metrics HTTP server on port {}", metricsPort, e);',
         'log.error(workerMessages.format("worker.retention.metrics_start_failed", metricsPort), e);'),
        ('log.info("Retention worker: graceful shutdown started");',
         'log.info(workerMessages.get("worker.retention.shutdown_started"));'),
        ('log.info("Retention worker: graceful shutdown complete");',
         'log.info(workerMessages.get("worker.retention.shutdown_complete"));'),
        ('log.info("Interrupted");', 'log.info(workerMessages.get("worker.common.interrupted"));'),
        ('log.error("Retention worker failed", e);',
         'log.error(workerMessages.get("worker.retention.failed"), e);'),
    ]
    for o, n in reps:
        text = text.replace(o, n)
    # enabled config log
    text = text.replace(
        '        log.info(\n            "Retention worker enabled: interval={}s initialDelay={}s batchLimit={} requireMinio={} useAppliedLog={} auditEnabled={} bulkAuditMinCleared={} retentionMinioBucket={} retentionObjectPrefix={} skipSnapshotIfDeepExists={} minioDefaultBucket={} postgresOnlyHotBody={} jdbcQueryTimeoutSeconds={} interMessageDelayMs={} snapshotTempfileThresholdBytes={} minioMultipartThresholdBytes={} dryRun={} useAdvisoryLock={}",',
        '        log.info(workerMessages.format("worker.retention.enabled_config",',
    )
    text = text.replace(
        '            useAdvisoryLock\n        );',
        '            useAdvisoryLock\n        ));',
    )
    text = text.replace(
        '                    log.warn(\n                        "Hot-body retention SQL runs on PostgreSQL only; jdbcUrl does not look like jdbc:postgresql — skipping purge pass"\n                    );',
        '                    log.warn(workerMessages.get("worker.retention.postgres_skip"));',
    )
    text = text.replace(
        '            log.warn(\n                "RETENTION_DRY_RUN=true: hot-body passes are read-only (SELECT candidates only; no UPDATE messages, MinIO put/stat on mutation path, retention_hot_body_applied, audit_events, or NATS msg.event.index / msg.event.retention). See docs/RETENTION_AND_DEEP_ARCHIVE.md §9."\n            );',
        '            log.warn(workerMessages.get("worker.retention.dry_run_warn"));',
    )
    text = text.replace(
        '                log.info(\n                    "Prometheus metrics on http://0.0.0.0:{}/metrics; GET /health (same port) for readiness",\n                    metricsServer.getPort()\n                );',
        '                log.info(workerMessages.format("worker.retention.metrics_url", metricsServer.getPort()));',
    )
    # janitor calls - add workerMessages
    text = text.replace(
        "                        useAdvisoryLock\n                    );",
        "                        useAdvisoryLock,\n                        workerMessages\n                    );",
    )
    text = text.replace(
        "                        dryRun\n                    );",
        "                        dryRun,\n                        workerMessages\n                    );",
        1,
    )
    text = text.replace(
        "                        dataSource, RetentionPlatformDefaults.readReceiptRetentionDaysFromEnv());",
        "                        dataSource, RetentionPlatformDefaults.readReceiptRetentionDaysFromEnv(), workerMessages);",
    )
    text = text.replace(
        "                        dryRun\n                    );\n                } else {",
        "                        dryRun,\n                        workerMessages\n                    );\n                } else {",
    )
    text = text.replace(
        "            RetentionMinioBootstrap.ensureBucketExists(minioClient, retentionBucket);",
        "            RetentionMinioBootstrap.ensureBucketExists(minioClient, retentionBucket, workerMessages);",
    )
    text = text.replace(
        "        var workerMessages = WorkerMessageSources.forWorker(\n            RetentionWorker.class, \"com.avandocmsg.messenger.i18n.messages_worker_retention\");",
        "",
    )
    text = text.replace(
        "        var shutdownRequested = new AtomicBoolean(false);",
        "        var workerMessages = WorkerMessageSources.forWorker(\n            RetentionWorker.class, \"com.avandocmsg.messenger.i18n.messages_worker_retention\");\n        log.info(workerMessages.format(\"worker.common.locale\", workerMessages.locale()));\n        var shutdownRequested = new AtomicBoolean(false);",
    )
    text = text.replace(
        "            shutdownRequested\n        );",
        "            shutdownRequested,\n            workerMessages\n        );",
    )
    text = text.replace(
        "            RetentionShutdown.shutdownScanExecutorQuietly(worker.scanExecutor, RetentionShutdown.DEFAULT_EXECUTOR_AWAIT_SECONDS);",
        "            RetentionShutdown.shutdownScanExecutorQuietly(worker.scanExecutor, RetentionShutdown.DEFAULT_EXECUTOR_AWAIT_SECONDS, workerMessages);",
    )
    text = text.replace(
        "            RetentionShutdown.runCloseables(closeInOrder);",
        "            RetentionShutdown.runCloseables(closeInOrder, workerMessages);",
    )
    p.write_text(text, encoding="utf-8")


def patch_janitor(rel: str, reps: list[tuple[str, str]], sig_old: str, sig_new: str) -> None:
    p = ROOT / rel
    text = p.read_text(encoding="utf-8")
    if WM_IMPORT.strip() not in text:
        text = text.replace("import org.slf4j.Logger;", WM_IMPORT + "import org.slf4j.Logger;")
    if sig_old in text:
        text = text.replace(sig_old, sig_new)
    for o, n in reps:
        text = text.replace(o, n)
    p.write_text(text, encoding="utf-8")


def main() -> None:
    patch_retention_worker()
    patch_janitor(
        "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/ReadReceiptRetentionJanitor.java",
        [
            ('log.info("Read receipt retention purge: deleted={} days={}", deleted, retentionDays);',
             'log.info(workerMessages.format("worker.retention.read_receipt.purged", deleted, retentionDays));'),
            ('log.warn("Read receipt retention purge failed: {}", e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.read_receipt.failed", e.getMessage()));'),
        ],
        "static int purgeOldReceipts(DataSource dataSource, int retentionDays) {",
        "static int purgeOldReceipts(DataSource dataSource, int retentionDays, UserMessageSource workerMessages) {",
    )
    patch_janitor(
        "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionHotRowPurger.java",
        [
            ('log.info("Retention hot-row purge dry-run: candidates={}", candidates.size());',
             'log.info(workerMessages.format("worker.retention.hot_row.dry_run", candidates.size()));'),
            ('log.info("Retention hot-row purge pass: purged={}", purged);',
             'log.info(workerMessages.format("worker.retention.hot_row.purged", purged));'),
        ],
        "        boolean dryRun\n    ) throws Exception {",
        "        boolean dryRun,\n        UserMessageSource workerMessages\n    ) throws Exception {",
    )
    patch_janitor(
        "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/FileRetentionJanitor.java",
        [
            ('log.info("File retention dry-run: candidates={}", candidates.size());',
             'log.info(workerMessages.format("worker.retention.file.dry_run", candidates.size()));'),
            ('log.info("File retention pass: deleted={}", deleted);',
             'log.info(workerMessages.format("worker.retention.file.deleted", deleted));'),
            ('log.warn("MinIO delete failed fileId={} key={}: {}", c.fileId(), objectName, e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.file.minio_delete_failed", c.fileId(), objectName, e.getMessage()));'),
        ],
        "        boolean dryRun\n    ) throws Exception {",
        "        boolean dryRun,\n        UserMessageSource workerMessages\n    ) throws Exception {",
    )
    # RetentionExportSuggester
    p = ROOT / "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionExportSuggester.java"
    t = p.read_text(encoding="utf-8")
    if "UserMessageSource" not in t:
        t = t.replace("import io.nats.client.Connection;", WM_IMPORT + "import io.nats.client.Connection;")
        t = t.replace(
            "    static void publishForChatCounts(Connection nats, Map<UUID, Integer> counts) {",
            "    static void publishForChatCounts(Connection nats, Map<UUID, Integer> counts, UserMessageSource workerMessages) {",
        )
        t = t.replace(
            'log.warn("Failed to publish {} chatId={}: {}", NatsSubjects.MSG_EXPORT_SUGGESTED, entry.getKey(), e.getMessage());',
            'log.warn(workerMessages.format("worker.retention.export_suggest_publish_failed", NatsSubjects.MSG_EXPORT_SUGGESTED, entry.getKey(), e.getMessage()));',
        )
        p.write_text(t, encoding="utf-8")
    # RetentionMinioBootstrap
    p = ROOT / "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionMinioBootstrap.java"
    t = p.read_text(encoding="utf-8")
    if "UserMessageSource" not in t:
        t = t.replace("import io.minio.MinioClient;", WM_IMPORT + "import io.minio.MinioClient;")
        t = t.replace(
            "    static void ensureBucketExists(MinioClient client, String bucket) {",
            "    static void ensureBucketExists(MinioClient client, String bucket, UserMessageSource workerMessages) {",
        )
        t = t.replace(
            'log.info("Created MinIO bucket {} for retention snapshots", bucket);',
            'log.info(workerMessages.format("worker.retention.minio_bucket_created", bucket));',
        )
        t = t.replace(
            'log.warn("Retention MinIO bucket ensure failed bucket={}: {}", bucket, e.getMessage());',
            'log.warn(workerMessages.format("worker.retention.minio_bucket_failed", bucket, e.getMessage()));',
        )
        p.write_text(t, encoding="utf-8")
    # RetentionShutdown
    p = ROOT / "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionShutdown.java"
    t = p.read_text(encoding="utf-8")
    if "UserMessageSource" not in t:
        t = t.replace("import org.slf4j.Logger;", WM_IMPORT + "import org.slf4j.Logger;")
        t = t.replace(
            "    static void runCloseables(Iterable<? extends AutoCloseable> closeables) {",
            "    static void runCloseables(Iterable<? extends AutoCloseable> closeables, UserMessageSource workerMessages) {",
        )
        t = t.replace(
            'log.warn("Retention shutdown: failed closing {}", c.getClass().getName(), e);',
            'log.warn(workerMessages.format("worker.retention.shutdown.close_failed", c.getClass().getName()), e);',
        )
        t = t.replace(
            "    static void shutdownScanExecutorQuietly(ScheduledExecutorService executor, int awaitSeconds) {",
            "    static void shutdownScanExecutorQuietly(ScheduledExecutorService executor, int awaitSeconds, UserMessageSource workerMessages) {",
        )
        t = t.replace(
            '                log.warn(\n                    "Retention shutdown: scan executor did not terminate within {}s (in-flight pass may still be running; closing resources best-effort)",\n                    awaitSeconds\n                );',
            '                log.warn(workerMessages.format("worker.retention.shutdown.executor_timeout", awaitSeconds));',
        )
        t = t.replace(
            'log.warn("Retention shutdown: interrupted while awaiting scan executor termination", e);',
            'log.warn(workerMessages.get("worker.retention.shutdown.interrupted"), e);',
        )
        p.write_text(t, encoding="utf-8")
    # RetentionHotBodyJanitor - large
    p = ROOT / "modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionHotBodyJanitor.java"
    t = p.read_text(encoding="utf-8")
    if "UserMessageSource workerMessages" not in t:
        t = t.replace("import io.nats.client.Connection;", WM_IMPORT + "import io.nats.client.Connection;")
        t = t.replace(
            "        boolean useAdvisoryLock\n    ) throws Exception {",
            "        boolean useAdvisoryLock,\n        UserMessageSource workerMessages\n    ) throws Exception {",
        )
        reps = [
            ('log.debug("Hot-body retention skipped: RETENTION_REQUIRE_MINIO=true and MinIO is not configured");',
             'log.debug(workerMessages.get("worker.retention.hot_body.minio_required_skip"));'),
            ('log.warn("Retention hot-body failed messageId={}: {}", c.id(), e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.hot_body.message_failed", c.id(), e.getMessage()));'),
            ('log.info("Retention hot-body pass: cleared {} message(s)", done);',
             'log.info(workerMessages.format("worker.retention.hot_body.pass_cleared", done));'),
            ('log.warn("Retention hot-body: pg_advisory_unlock failed: {}", e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.hot_body.advisory_unlock_failed", e.getMessage()));'),
            ('log.warn("Retention hot-body: failed closing pass JDBC connection: {}", e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.hot_body.jdbc_close_failed", e.getMessage()));'),
            ('log.debug("Retention hot-body: pg_advisory_unlock returned false (session did not hold the lock)");',
             'log.debug(workerMessages.get("worker.retention.hot_body.advisory_unlock_false"));'),
            ('log.debug("Retention skip messageId={}: row not updated (race or already cleared)", c.id());',
             'log.debug(workerMessages.format("worker.retention.hot_body.row_race_skip", c.id()));'),
            ('log.debug("Retention statObject unexpected response bucket={} key={}: {}", bucket, objectKey, e.getMessage());',
             'log.debug(workerMessages.format("worker.retention.hot_body.stat_unexpected", bucket, objectKey, e.getMessage()));'),
            ('log.debug("Retention statObject failed bucket={} key={}: {}", bucket, objectKey, e.getMessage());',
             'log.debug(workerMessages.format("worker.retention.hot_body.stat_failed", bucket, objectKey, e.getMessage()));'),
            ('log.warn("Retention bulk audit insert failed passId={}: {}", passId, e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.hot_body.bulk_audit_failed", passId, e.getMessage()));'),
            ('log.warn("Retention audit insert failed messageId={}: {}", messageId, e.getMessage());',
             'log.warn(workerMessages.format("worker.retention.hot_body.audit_failed", messageId, e.getMessage()));'),
            ('log.debug("Retention skip snapshot for message {}: content is file reference", candidate.id());',
             'log.debug(workerMessages.format("worker.retention.hot_body.skip_file_ref", candidate.id()));'),
            ('log.warn("Retention failed to delete temp snapshot file {}: {}", tmp, delEx.getMessage());',
             'log.warn(workerMessages.format("worker.retention.hot_body.temp_delete_failed", tmp, delEx.getMessage()));'),
        ]
        for o, n in reps:
            t = t.replace(o, n)
        t = t.replace(
            "RetentionExportSuggester.publishForChatCounts(nats, candidateCountByChatId(batch));",
            "RetentionExportSuggester.publishForChatCounts(nats, candidateCountByChatId(batch), workerMessages);",
        )
        p.write_text(t, encoding="utf-8")
    print("retention patched")


if __name__ == "__main__":
    main()
