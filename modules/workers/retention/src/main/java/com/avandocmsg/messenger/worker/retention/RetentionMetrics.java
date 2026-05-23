package com.avandocmsg.messenger.worker.retention;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prometheus counters/histograms/gauges/info for {@link RetentionWorker} / {@link RetentionHotBodyJanitor}.
 * Registered with the default registry; HTTP scrape starts when {@code RETENTION_METRICS_PORT} is a positive port (see {@link RetentionWorker#main}).
 * {@link #registerBuildInfoOnce()} runs from {@link RetentionMetricsHttpServer#start} so {@code retention_worker_build_info} is present on {@code /metrics}.
 */
final class RetentionMetrics {

    private static final String RETENTION_WORKER_NAME = "retention-worker";

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    /**
     * Static build labels ({@code version}, {@code name}); registered on default registry when {@link #registerBuildInfoOnce()} runs (metrics HTTP startup).
     */
    private static final Info BUILD_INFO = Info.build()
        .name("retention_worker_build_info")
        .help("Build information for the retention worker (labels: version, name).")
        .register();

    private static final Counter DB_PING_FAILURES = Counter.build()
        .name("retention_worker_db_ping_failures_total")
        .help("Failed Hot DB ping attempts (SELECT 1)")
        .register();

    private static final Counter HOT_BODY_CLEARED = Counter.build()
        .name("retention_worker_hot_body_cleared_total")
        .help("Messages whose Hot DB body was cleared after MinIO snapshot (or debug path) and NATS publish")
        .register();

    private static final Counter HOT_BODY_PROCESSING_ERRORS = Counter.build()
        .name("retention_worker_hot_body_processing_errors_total")
        .help("Exceptions while processing a single retention candidate message")
        .register();

    private static final Counter HOT_BODY_ROW_NOT_UPDATED = Counter.build()
        .name("retention_worker_hot_body_row_not_updated_total")
        .help("UPDATE messages cleared content matched zero rows (race or already cleared)")
        .register();

    private static final Counter MINIO_SNAPSHOT_UPLOADS = Counter.build()
        .name("retention_worker_minio_snapshot_uploads_total")
        .help("Successful MinIO writes for retention body JSON snapshots (putObject or uploadObject)")
        .register();

    private static final Counter MINIO_MULTIPART_UPLOADS = Counter.build()
        .name("retention_worker_minio_multipart_uploads_total")
        .help("Successful MinIO uploadObject multipart uploads for large temp-file retention snapshots")
        .register();

    private static final Counter MINIO_SNAPSHOT_TEMPFILE = Counter.build()
        .name("retention_worker_minio_snapshot_tempfile_total")
        .help("Retention JSON snapshots materialized via temp file under java.io.tmpdir before putObject")
        .register();

    private static final Counter MINIO_SNAPSHOT_SKIPPED_EXISTING = Counter.build()
        .name("retention_worker_minio_snapshot_skipped_existing_total")
        .help("Skipped MinIO putObject because statObject found an existing snapshot (deep-archive key or retention key)")
        .labelNames("reason")
        .register();

    private static final Counter PASS_SKIPPED_MINIO_REQUIRED = Counter.build()
        .name("retention_worker_pass_skipped_minio_required_total")
        .help("Retention pass skipped because RETENTION_REQUIRE_MINIO=true and MinIO is not configured")
        .register();

    private static final Counter PASS_SKIPPED_ADVISORY_LOCK = Counter.build()
        .name("retention_worker_pass_skipped_advisory_lock_total")
        .help("Hot-body pass skipped because RETENTION_USE_ADVISORY_LOCK=true and pg_try_advisory_lock returned false (another session holds the lock)")
        .register();

    private static final Counter EXPORT_SUGGESTED_PUBLISHED = Counter.build()
        .name("retention_worker_export_suggested_published_total")
        .help("msg.export.suggested NATS publishes (RETENTION_PUBLISH_EXPORT_SUGGESTED=true)")
        .register();

    private static final Counter DRY_RUN_PASSES = Counter.build()
        .name("retention_worker_dry_run_passes_total")
        .help("Hot-body passes completed in RETENTION_DRY_RUN=true mode (no Hot DB / MinIO / NATS / audit mutations)")
        .register();

    private static final Counter AUDIT_INSERT_FAILURES = Counter.build()
        .name("retention_worker_audit_insert_failures_total")
        .help("Failed inserts into audit_events after successful body clear (non-fatal)")
        .register();

    private static final Counter CHUNK_WRITES = Counter.build()
        .name("retention_worker_chunk_writes_total")
        .help("Chunked MinIO writes for large retention snapshots")
        .register();

    private static final Counter FILE_REF_SKIPPED = Counter.build()
        .name("retention_worker_file_ref_skipped_total")
        .help("Messages skipped by hot-body pass because content is a file reference (file://...)")
        .register();

    private static final Histogram PASS_DURATION_SECONDS = Histogram.build()
        .name("retention_worker_hot_body_pass_duration_seconds")
        .help("Wall time for one RetentionHotBodyJanitor.runOnce pass (query + per-message processing)")
        .register();

    private static final Histogram PASS_CANDIDATES = Histogram.build()
        .name("retention_worker_hot_body_pass_candidates")
        .help("Number of candidate messages loaded from DB per pass")
        .buckets(0, 1, 5, 10, 25, 50, 100, 250, 500)
        .register();

    private static final Histogram MINIO_SNAPSHOT_BYTES = Histogram.build()
        .name("retention_worker_minio_snapshot_bytes")
        .help("JSON snapshot payload size in bytes written to MinIO")
        .buckets(256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304)
        .register();

    /**
     * Unix epoch seconds when a hot-body pass last finished after loading candidates from Hot DB (including empty batch and dry-run).
     * Not updated on throws before completion, {@code RETENTION_REQUIRE_MINIO} skip, or advisory-lock skip (no candidate SELECT).
     */
    private static final Gauge LAST_HOT_BODY_PASS_EPOCH_SECONDS = Gauge.build()
        .name("retention_worker_last_hot_body_pass_epoch_seconds")
        .help("Unix epoch seconds when the last hot-body pass completed after candidate SELECT (dry-run or live; not lock/minio skips)")
        .register();

    /**
     * Cleared message count from the last completed hot-body pass after candidate SELECT. {@code RETENTION_DRY_RUN=true}: always {@code 0} (no clears).
     */
    private static final Gauge LAST_PASS_CLEARED_COUNT = Gauge.build()
        .name("retention_worker_last_pass_cleared_count")
        .help("Last hot-body pass cleared count after candidate SELECT; 0 for dry-run (no DB clears) and for empty batch")
        .register();

    private RetentionMetrics() {
    }

    /**
     * Sets {@link #BUILD_INFO} labels once (from {@link RetentionWorker} package implementation version or {@code "unknown"}).
     * Safe to call from {@link RetentionMetricsHttpServer#start}; idempotent.
     */
    static void registerBuildInfoOnce() {
        if (!BUILD_INFO_LABELED.compareAndSet(false, true)) {
            return;
        }
        var pkg = RetentionWorker.class.getPackage();
        var v = pkg != null ? pkg.getImplementationVersion() : null;
        if (v == null || v.isBlank()) {
            v = "unknown";
        }
        BUILD_INFO.info("version", v, "name", RETENTION_WORKER_NAME);
    }

    static void dbPingFailed() {
        DB_PING_FAILURES.inc();
    }

    static void hotBodyCleared() {
        HOT_BODY_CLEARED.inc();
    }

    static void processingError() {
        HOT_BODY_PROCESSING_ERRORS.inc();
    }

    static void rowNotUpdated() {
        HOT_BODY_ROW_NOT_UPDATED.inc();
    }

    static void minioSnapshotUploaded(int payloadBytes) {
        MINIO_SNAPSHOT_UPLOADS.inc();
        if (payloadBytes >= 0) {
            MINIO_SNAPSHOT_BYTES.observe(payloadBytes);
        }
    }

    static void minioSnapshotTempfileUsed() {
        MINIO_SNAPSHOT_TEMPFILE.inc();
    }

    static void minioMultipartUploadSucceeded() {
        MINIO_MULTIPART_UPLOADS.inc();
    }

    /** @param reason {@code deep} — existing {@code messages/{id}.json}; {@code retention} — existing retention prefix snapshot */
    static void minioSnapshotSkippedExisting(String reason) {
        var r = (reason != null && !reason.isBlank()) ? reason : "unknown";
        MINIO_SNAPSHOT_SKIPPED_EXISTING.labels(r).inc();
    }

    static void passSkippedMinioRequired() {
        PASS_SKIPPED_MINIO_REQUIRED.inc();
    }

    static void passSkippedAdvisoryLock() {
        PASS_SKIPPED_ADVISORY_LOCK.inc();
    }

    static void dryRunPassCompleted() {
        DRY_RUN_PASSES.inc();
    }

    static void exportSuggestedPublished() {
        EXPORT_SUGGESTED_PUBLISHED.inc();
    }

    static void auditInsertFailed() {
        AUDIT_INSERT_FAILURES.inc();
    }

    static void chunkWrite() {
        CHUNK_WRITES.inc();
    }

    static void fileRefSkipped() {
        FILE_REF_SKIPPED.inc();
    }

    static void observePassDurationSeconds(double seconds) {
        if (seconds >= 0) {
            PASS_DURATION_SECONDS.observe(seconds);
        }
    }

    static void observePassCandidates(int count) {
        if (count >= 0) {
            PASS_CANDIDATES.observe(count);
        }
    }

    /**
     * Operational gauges: call only after a pass that ran the candidate SELECT (see {@link RetentionHotBodyJanitor#runOnce}).
     *
     * @param clearedCount live pass: rows cleared this pass; dry-run: {@code 0} (not {@code would_clear})
     */
    static void recordHotBodyPassCompletionGauges(long epochSeconds, int clearedCount) {
        LAST_HOT_BODY_PASS_EPOCH_SECONDS.set(epochSeconds);
        LAST_PASS_CLEARED_COUNT.set(Math.max(0, clearedCount));
    }
}
