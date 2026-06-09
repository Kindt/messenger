#!/usr/bin/env python3
"""Patch ExportReplayWorker and related export-replay classes for i18n."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORKER = ROOT / "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportReplayWorker.java"


def patch_export_replay_worker() -> None:
    text = WORKER.read_text(encoding="utf-8")
    if "UserMessageSource workerMessages" not in text:
        text = text.replace(
            "import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;",
            "import com.avandocmsg.messenger.common.i18n.UserMessageSource;\nimport com.avandocmsg.messenger.common.i18n.WorkerMessageSources;",
        )
        text = text.replace(
            "    private final long debugDelayMs;\n",
            "    private final long debugDelayMs;\n    private final UserMessageSource workerMessages;\n",
        )
        text = text.replace(
            "        ExportFileBodyFetcher fileBodyFetcher\n    ) throws Exception {",
            "        ExportFileBodyFetcher fileBodyFetcher,\n        UserMessageSource workerMessages\n    ) throws Exception {",
        )
        text = text.replace(
            "        this.fileBodyFetcher = fileBodyFetcher;\n        this.cancelCheckEveryRows",
            "        this.fileBodyFetcher = fileBodyFetcher;\n        this.workerMessages = workerMessages;\n        this.cancelCheckEveryRows",
        )
        text = text.replace(
            "this.jobStore = dataSource != null ? new ExportJobStore(dataSource) : null;",
            "this.jobStore = dataSource != null ? new ExportJobStore(dataSource, workerMessages) : null;",
        )
        text = text.replace(
            "this.auditWriter = dataSource != null ? new ExportAuditWriter(dataSource) : null;",
            "this.auditWriter = dataSource != null ? new ExportAuditWriter(dataSource, workerMessages) : null;",
        )
        text = text.replace(
            "ExportFileBundleBuilder.build(root, zip, fileBodyFetcher, maxFileBodies, maxFileBodyBytes);",
            "ExportFileBundleBuilder.build(root, zip, fileBodyFetcher, maxFileBodies, maxFileBodyBytes, workerMessages);",
        )

    replacements = [
        ('log.warn("EXPORT_REPLAY_DEBUG_DELAY_MS={} — dev/smoke only; export will pause after processing", this.debugDelayMs);',
         'log.warn(workerMessages.format("worker.export_replay.debug_delay_warn", this.debugDelayMs));'),
        ('log.info("Connected to NATS at {}", natsUrl);',
         'log.info(workerMessages.format("worker.common.connected_nats", natsUrl));'),
        ('log.warn("Invalid export job payload: {}", payload);',
         'log.warn(workerMessages.format("worker.export_replay.invalid_payload", payload));'),
        ('log.info("Export replay stub written jobId={} path={}", job.jobId(), out.toAbsolutePath());',
         'log.info(workerMessages.format("worker.export_replay.stub_written", job.jobId(), out.toAbsolutePath()));'),
        ('log.info("Export job {} skipped (not queued — cancelled or duplicate)", job.jobId());',
         'log.info(workerMessages.format("worker.export_replay.job_skipped", job.jobId()));'),
        ('log.warn("Export zip bundle failed jobId={}, keeping JSON: {}", job.jobId(), zipErr.getMessage());',
         'log.warn(workerMessages.format("worker.export_replay.zip_failed", job.jobId(), zipErr.getMessage()));'),
        ('log.warn("Export job invalid chat UUID jobId={} chatId={}", job.jobId(), job.chatId());',
         'log.warn(workerMessages.format("worker.export_replay.invalid_chat_uuid", job.jobId(), job.chatId()));'),
        ('log.error("Export DB query failed jobId={}", job.jobId(), e);',
         'log.error(workerMessages.format("worker.export_replay.db_query_failed", job.jobId()), e);'),
        ('log.error("Failed to handle export-replay message", e);',
         'log.error(workerMessages.get("worker.export_replay.handle_failed"), e);'),
        ('log.info("Export job {} debug delay {} ms (EXPORT_REPLAY_DEBUG_DELAY_MS)", jobId, debugDelayMs);',
         'log.info(workerMessages.format("worker.export_replay.debug_delay", jobId, debugDelayMs));'),
        ('log.warn("Export debug delay interrupted jobId={}", jobId);',
         'log.warn(workerMessages.format("worker.export_replay.debug_delay_interrupted", jobId));'),
        ('log.debug("Export cancel hint jobId={} chatId={}", event.jobId(), event.chatId());',
         'log.debug(workerMessages.format("worker.export_replay.cancel_hint", event.jobId(), event.chatId()));'),
        ('log.info("Export job {} aborted — status export_cancelled", jobUuid);',
         'log.info(workerMessages.format("worker.export_replay.job_aborted", jobUuid));'),
        ('log.info("Export job {} cancelled, not applying terminal status {}", job.jobId(), status);',
         'log.info(workerMessages.format("worker.export_replay.job_cancelled", job.jobId(), status));'),
        ('log.warn("MinIO export upload failed jobId={}, using local path: {}", job.jobId(), e.getMessage());',
         'log.warn(workerMessages.format("worker.export_replay.minio_upload_failed", job.jobId(), e.getMessage()));'),
        ('log.warn("Export job: no chats row for chatId={} jobId={}", job.chatId(), job.jobId());',
         'log.warn(workerMessages.format("worker.export_replay.no_chats_row", job.chatId(), job.jobId()));'),
        ('log.warn("Solr index export failed chatId={}: {}", chatId, e.getMessage());',
         'log.warn(workerMessages.format("worker.export_replay.solr_failed", chatId, e.getMessage()));'),
        ('log.warn("Export completeness validation failed jobId={} strict={}", job.jobId(), strict);',
         'log.warn(workerMessages.format("worker.export_replay.completeness_failed", job.jobId(), strict));'),
        ('log.debug("Published {} status={}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, status);',
         'log.debug(workerMessages.format("worker.export_replay.published_complete", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, status));'),
        ('log.warn("Failed to publish {}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, e);',
         'log.warn(workerMessages.format("worker.common.publish_failed_simple", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE), e);'),
        ('log.warn("Error closing NATS connection", e);',
         'log.warn(workerMessages.get("worker.common.nats_close_error"), e);'),
        ('log.warn("EXPORT_REPLAY_MINIO_UPLOAD=true but MinIO env incomplete (MINIO_ENDPOINT, keys)");',
         'log.warn(workerMessages.get("worker.export_replay.minio_upload_enabled"));'),
        ('log.warn("EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE=true but MinIO env incomplete");',
         'log.warn(workerMessages.get("worker.export_replay.deep_archive_minio_incomplete"));'),
        ('log.warn("EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS=true but MinIO env incomplete");',
         'log.warn(workerMessages.get("worker.export_replay.retention_snapshots_minio_incomplete"));'),
        ('log.warn("EXPORT_REPLAY_INCLUDE_SOLR_INDEX=true but SOLR_URL/SOLR_ZK not set");',
         'log.warn(workerMessages.get("worker.export_replay.solr_not_set"));'),
        ('log.warn("EXPORT_REPLAY_INCLUDE_FILE_BODIES=true but MinIO env incomplete");',
         'log.warn(workerMessages.get("worker.export_replay.file_bodies_minio_incomplete"));'),
        ('log.warn("DB_JDBC_URL not set: export-replay writes stub JSON only");',
         'log.warn(workerMessages.get("worker.export_replay.db_stub_only"));'),
        ('log.error("Fatal error", e);', 'log.error(workerMessages.get("worker.common.fatal_error"), e);'),
    ]
    for old, new in replacements:
        if old in text:
            text = text.replace(old, new)
        else:
            print("MISSING:", old[:70])

        text = text.replace(
            "        var metricsPort = ExportPlatformDefaults.metricsPortFromEnv();\n        ExportReplayMetricsHttpServer metricsServer = null;\n\n        try {\n            var worker = new ExportReplayWorker(",
            "        var workerMessages = WorkerMessageSources.forWorker(\n            ExportReplayWorker.class, \"com.avandocmsg.messenger.i18n.messages_worker_export_replay\");\n        log.info(workerMessages.format(\"worker.common.locale\", workerMessages.locale()));\n        var minioUploader = minioUpload ? ExportMinioUploader.fromEnv(workerMessages) : null;\n        var retentionSnapshotReader = includeRetentionSnapshots ? ExportRetentionSnapshotReader.fromEnv(workerMessages) : null;\n        var metricsPort = ExportPlatformDefaults.metricsPortFromEnv();\n        ExportReplayMetricsHttpServer metricsServer = null;\n\n        try {\n            var worker = new ExportReplayWorker(",
        )
        # remove duplicate declarations in main if present
        text = text.replace(
            "        var minioUploader = minioUpload ? ExportMinioUploader.fromEnv() : null;\n",
            "",
        )
        text = text.replace(
            "        var retentionSnapshotReader = includeRetentionSnapshots ? ExportRetentionSnapshotReader.fromEnv() : null;\n",
            "",
        )
        text = text.replace(
            "                fileBodyFetcher\n            );\n            var workerMessages = WorkerMessageSources.forWorker(\n                ExportReplayWorker.class, \"com.avandocmsg.messenger.i18n.messages_worker_export_replay\");",
            "                fileBodyFetcher,\n                workerMessages\n            );",
        )

    WORKER.write_text(text, encoding="utf-8")
    print("ExportReplayWorker patched")


def patch_minio_uploader_from_env() -> None:
    p = ROOT / "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportMinioUploader.java"
    text = p.read_text(encoding="utf-8")
    text = text.replace(
        "    static ExportMinioUploader fromEnv() {",
        "    static ExportMinioUploader fromEnv(UserMessageSource workerMessages) {",
    )
    text = text.replace(
        "        return new ExportMinioUploader(client, bucket);",
        "        return new ExportMinioUploader(client, bucket, workerMessages);",
    )
    p.write_text(text, encoding="utf-8")


def patch_retention_snapshot_reader() -> None:
    p = ROOT / "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportRetentionSnapshotReader.java"
    text = p.read_text(encoding="utf-8")
    if "UserMessageSource" not in text:
        text = text.replace(
            "import io.minio.MinioClient;",
            "import com.avandocmsg.messenger.common.i18n.UserMessageSource;\nimport io.minio.MinioClient;",
        )
        text = text.replace(
            "    private final boolean tryDefaultKeyWhenNotInLog;\n",
            "    private final boolean tryDefaultKeyWhenNotInLog;\n    private final UserMessageSource workerMessages;\n",
        )
        text = text.replace(
            "        boolean tryDefaultKeyWhenNotInLog\n    ) {\n        this.client = client;\n        this.bucket = bucket;\n        this.objectPrefix = normalizePrefix(objectPrefix);\n        this.tryDefaultKeyWhenNotInLog = tryDefaultKeyWhenNotInLog;\n    }",
            "        boolean tryDefaultKeyWhenNotInLog,\n        UserMessageSource workerMessages\n    ) {\n        this.client = client;\n        this.bucket = bucket;\n        this.objectPrefix = normalizePrefix(objectPrefix);\n        this.tryDefaultKeyWhenNotInLog = tryDefaultKeyWhenNotInLog;\n        this.workerMessages = workerMessages;\n    }",
        )
        text = text.replace(
            "    static ExportRetentionSnapshotReader fromEnv() {",
            "    static ExportRetentionSnapshotReader fromEnv(UserMessageSource workerMessages) {",
        )
        text = text.replace(
            "        return new ExportRetentionSnapshotReader(client, bucket.trim(), prefix, tryDefault);",
            "        return new ExportRetentionSnapshotReader(client, bucket.trim(), prefix, tryDefault, workerMessages);",
        )
        text = text.replace(
            "            var snap = ExportMinioJsonFetcher.fetchSnapshot(client, bucket, key, messageId, SOURCE);",
            "            var snap = ExportMinioJsonFetcher.fetchSnapshot(client, bucket, key, messageId, SOURCE, workerMessages);",
        )
    p.write_text(text, encoding="utf-8")


def patch_tests() -> None:
    job_store_test = ROOT / "modules/workers/export-replay/src/test/java/com/avandocmsg/messenger/worker/exportreplay/ExportJobStoreH2Test.java"
    text = job_store_test.read_text(encoding="utf-8")
    if "WorkerMessageSources" not in text:
        text = text.replace(
            "import org.junit.jupiter.api.Test;",
            "import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;\nimport org.junit.jupiter.api.Test;",
        )
        text = text.replace(
            "        store = new ExportJobStore(ds);",
            "        store = new ExportJobStore(ds, WorkerMessageSources.forWorker(\n            ExportReplayWorker.class, \"com.avandocmsg.messenger.i18n.messages_worker_export_replay\"));",
        )
        job_store_test.write_text(text, encoding="utf-8")

    bundle_test = ROOT / "modules/workers/export-replay/src/test/java/com/avandocmsg/messenger/worker/exportreplay/ExportFileBundleBuilderTest.java"
    text = bundle_test.read_text(encoding="utf-8")
    if "WorkerMessageSources" not in text:
        text = text.replace(
            "import org.junit.jupiter.api.Test;",
            "import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;\nimport org.junit.jupiter.api.Test;",
        )
        wm = 'WorkerMessageSources.forWorker(ExportReplayWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_export_replay")'
        text = text.replace(
            "ExportFileBundleBuilder.build(root, zip, fetcher, 10, 1_000_000);",
            f"ExportFileBundleBuilder.build(root, zip, fetcher, 10, 1_000_000, {wm});",
        )
        bundle_test.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_minio_uploader_from_env()
    patch_retention_snapshot_reader()
    patch_export_replay_worker()
    patch_tests()
    print("done")
