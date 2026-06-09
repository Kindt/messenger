#!/usr/bin/env python3
"""Patch export-replay and retention worker log i18n."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

EXPORT_EN = {
    "worker.module": "export-replay",
    "worker.export_replay.debug_delay_warn": "EXPORT_REPLAY_DEBUG_DELAY_MS={0} — dev/smoke only; export will pause after processing",
    "worker.export_replay.subscribed": "Subscribed to {0} (queue: {1}) exportDir={2} dbExport={3} maxMessages={4} includeVersions={5} maxVersionRows={6} includeReactions={7} maxReactionRows={8} includePins={9} maxPinnedRows={10} includeChat={11} includeChatMembers={12} maxChatMemberRows={13} includeReferencedUsers={14} maxReferencedUserRows={15} includeReferencedFiles={16} maxFileIdsFromContent={17} maxReferencedFileRows={18} messageTtlFilter={19}",
    "worker.export_replay.invalid_payload": "Invalid export job payload: {0}",
    "worker.export_replay.stub_written": "Export replay stub written jobId={0} path={1}",
    "worker.export_replay.job_skipped": "Export job {0} skipped (not queued — cancelled or duplicate)",
    "worker.export_replay.zip_failed": "Export zip bundle failed jobId={0}, keeping JSON: {1}",
    "worker.export_replay.export_written": "Export written jobId={0} path={1} messages={2}",
    "worker.export_replay.invalid_chat_uuid": "Export job invalid chat UUID jobId={0} chatId={1}",
    "worker.export_replay.db_query_failed": "Export DB query failed jobId={0}",
    "worker.export_replay.handle_failed": "Failed to handle export-replay message",
    "worker.export_replay.debug_delay": "Export job {0} debug delay {1} ms (EXPORT_REPLAY_DEBUG_DELAY_MS)",
    "worker.export_replay.debug_delay_interrupted": "Export debug delay interrupted jobId={0}",
    "worker.export_replay.cancel_hint": "Export cancel hint jobId={0} chatId={1}",
    "worker.export_replay.job_aborted": "Export job {0} aborted — status export_cancelled",
    "worker.export_replay.job_cancelled": "Export job {0} cancelled, not applying terminal status {1}",
    "worker.export_replay.minio_upload_failed": "MinIO export upload failed jobId={0}, using local path: {1}",
    "worker.export_replay.no_chats_row": "Export job: no chats row for chatId={0} jobId={1}",
    "worker.export_replay.solr_failed": "Solr index export failed chatId={0}: {1}",
    "worker.export_replay.completeness_failed": "Export completeness validation failed jobId={0} strict={1}",
    "worker.export_replay.published_complete": "Published {0} status={1}",
    "worker.export_replay.minio_upload_enabled": "EXPORT_REPLAY_MINIO_UPLOAD=true but MinIO env incomplete (MINIO_ENDPOINT, keys)",
    "worker.export_replay.deep_archive_minio_incomplete": "EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE=true but MinIO env incomplete",
    "worker.export_replay.retention_snapshots_minio_incomplete": "EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS=true but MinIO env incomplete",
    "worker.export_replay.solr_not_set": "EXPORT_REPLAY_INCLUDE_SOLR_INDEX=true but SOLR_URL/SOLR_ZK not set",
    "worker.export_replay.file_bodies_minio_incomplete": "EXPORT_REPLAY_INCLUDE_FILE_BODIES=true but MinIO env incomplete",
    "worker.export_replay.startup_config": "Export-replay worker: exportDir={0} publishComplete={1} maxMessages={2}",
    "worker.export_replay.db_stub_only": "DB_JDBC_URL not set: export-replay writes stub JSON only",
    "worker.export_replay.metrics_url": "Prometheus metrics on http://0.0.0.0:{0}/metrics; GET /health on same port",
    "worker.export_replay.job_store.mark_failed": "export_jobs markProcessingIfQueued failed jobId={0}: {1}",
    "worker.export_replay.job_store.load_failed": "export_jobs loadStatus failed jobId={0}: {1}",
    "worker.export_replay.job_store.not_found": "export_jobs row not found for jobId={0}",
    "worker.export_replay.job_store.update_failed": "export_jobs update failed jobId={0} status={1}: {2}",
    "worker.export_replay.audit_insert_failed": "export.completed audit insert failed jobId={0}: {1}",
    "worker.export_replay.minio_uploaded": "Uploaded export to MinIO bucket={0} key={1} bytes={2}",
    "worker.export_replay.minio_stat_failed": "MinIO stat/get failed source={0} messageId={1} key={2}: {3}",
    "worker.export_replay.minio_read_failed": "MinIO read failed source={0} messageId={1} bucket={2} key={3}: {4}",
    "worker.export_replay.attachment_skip": "Attachment skip fileId={0}: {1}",
}

EXPORT_RU = {
    "worker.module": "export-replay",
    "worker.export_replay.debug_delay_warn": "EXPORT_REPLAY_DEBUG_DELAY_MS={0} — только dev/smoke; экспорт приостановится после обработки",
    "worker.export_replay.subscribed": "Подписка на {0} (очередь: {1}) exportDir={2} dbExport={3} maxMessages={4} includeVersions={5} maxVersionRows={6} includeReactions={7} maxReactionRows={8} includePins={9} maxPinnedRows={10} includeChat={11} includeChatMembers={12} maxChatMemberRows={13} includeReferencedUsers={14} maxReferencedUserRows={15} includeReferencedFiles={16} maxFileIdsFromContent={17} maxReferencedFileRows={18} messageTtlFilter={19}",
    "worker.export_replay.invalid_payload": "Неверный payload export job: {0}",
    "worker.export_replay.stub_written": "Export replay stub записан jobId={0} path={1}",
    "worker.export_replay.job_skipped": "Export job {0} пропущен (не queued — отменён или дубликат)",
    "worker.export_replay.zip_failed": "Export zip bundle не удался jobId={0}, оставлен JSON: {1}",
    "worker.export_replay.export_written": "Export записан jobId={0} path={1} messages={2}",
    "worker.export_replay.invalid_chat_uuid": "Export job: неверный chat UUID jobId={0} chatId={1}",
    "worker.export_replay.db_query_failed": "Ошибка SQL export jobId={0}",
    "worker.export_replay.handle_failed": "Не удалось обработать export-replay сообщение",
    "worker.export_replay.debug_delay": "Export job {0} debug delay {1} ms (EXPORT_REPLAY_DEBUG_DELAY_MS)",
    "worker.export_replay.debug_delay_interrupted": "Debug delay export прерван jobId={0}",
    "worker.export_replay.cancel_hint": "Export cancel hint jobId={0} chatId={1}",
    "worker.export_replay.job_aborted": "Export job {0} прерван — status export_cancelled",
    "worker.export_replay.job_cancelled": "Export job {0} отменён, terminal status {1} не применяется",
    "worker.export_replay.minio_upload_failed": "MinIO upload export не удался jobId={0}, используется local path: {1}",
    "worker.export_replay.no_chats_row": "Export job: нет строки chats для chatId={0} jobId={1}",
    "worker.export_replay.solr_failed": "Solr index export не удался chatId={0}: {1}",
    "worker.export_replay.completeness_failed": "Export completeness validation не пройдена jobId={0} strict={1}",
    "worker.export_replay.published_complete": "Опубликовано {0} status={1}",
    "worker.export_replay.minio_upload_enabled": "EXPORT_REPLAY_MINIO_UPLOAD=true, но MinIO env неполный (MINIO_ENDPOINT, keys)",
    "worker.export_replay.deep_archive_minio_incomplete": "EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE=true, но MinIO env неполный",
    "worker.export_replay.retention_snapshots_minio_incomplete": "EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS=true, но MinIO env неполный",
    "worker.export_replay.solr_not_set": "EXPORT_REPLAY_INCLUDE_SOLR_INDEX=true, но SOLR_URL/SOLR_ZK не заданы",
    "worker.export_replay.file_bodies_minio_incomplete": "EXPORT_REPLAY_INCLUDE_FILE_BODIES=true, но MinIO env неполный",
    "worker.export_replay.startup_config": "Export-replay worker: exportDir={0} publishComplete={1} maxMessages={2}",
    "worker.export_replay.db_stub_only": "DB_JDBC_URL не задан: export-replay пишет только stub JSON",
    "worker.export_replay.metrics_url": "Prometheus metrics: http://0.0.0.0:{0}/metrics; GET /health на том же порту",
    "worker.export_replay.job_store.mark_failed": "export_jobs markProcessingIfQueued failed jobId={0}: {1}",
    "worker.export_replay.job_store.load_failed": "export_jobs loadStatus failed jobId={0}: {1}",
    "worker.export_replay.job_store.not_found": "export_jobs: строка не найдена для jobId={0}",
    "worker.export_replay.job_store.update_failed": "export_jobs update failed jobId={0} status={1}: {2}",
    "worker.export_replay.audit_insert_failed": "export.completed audit insert failed jobId={0}: {1}",
    "worker.export_replay.minio_uploaded": "Export загружен в MinIO bucket={0} key={1} bytes={2}",
    "worker.export_replay.minio_stat_failed": "MinIO stat/get failed source={0} messageId={1} key={2}: {3}",
    "worker.export_replay.minio_read_failed": "MinIO read failed source={0} messageId={1} bucket={2} key={3}: {4}",
    "worker.export_replay.attachment_skip": "Attachment skip fileId={0}: {1}",
}

RETENTION_EN = {
    "worker.module": "retention",
    "worker.retention.disabled": "Retention worker disabled (RETENTION_WORKER_ENABLED=false); idle. See docs/RETENTION_AND_DEEP_ARCHIVE.md",
    "worker.retention.requires_db_nats": "RETENTION_WORKER_ENABLED=true requires DB_JDBC_URL and NATS_URL",
    "worker.retention.requires_db": "RETENTION_WORKER_ENABLED=true requires DB_JDBC_URL",
    "worker.retention.enabled_config": "Retention worker enabled: interval={0}s initialDelay={1}s batchLimit={2} requireMinio={3} useAppliedLog={4} auditEnabled={5} bulkAuditMinCleared={6} retentionMinioBucket={7} retentionObjectPrefix={8} skipSnapshotIfDeepExists={9} minioDefaultBucket={10} postgresOnlyHotBody={11} jdbcQueryTimeoutSeconds={12} interMessageDelayMs={13} snapshotTempfileThresholdBytes={14} minioMultipartThresholdBytes={15} dryRun={16} useAdvisoryLock={17}",
    "worker.retention.first_scan_delay": "Retention worker: first scan scheduled after {0}s initial delay",
    "worker.retention.postgres_skip": "Hot-body retention SQL runs on PostgreSQL only; jdbcUrl does not look like jdbc:postgresql — skipping purge pass",
    "worker.retention.scan_failed": "Retention scan pass failed",
    "worker.retention.db_ping_ok": "Hot DB ping OK",
    "worker.retention.db_ping_failed": "Hot DB ping failed: {0}",
    "worker.retention.metrics_start_failed": "Failed to start Prometheus metrics HTTP server on port {0}",
    "worker.retention.metrics_url": "Prometheus metrics on http://0.0.0.0:{0}/metrics; GET /health (same port) for readiness",
    "worker.retention.dry_run_warn": "RETENTION_DRY_RUN=true: hot-body passes are read-only (SELECT candidates only; no UPDATE messages, MinIO put/stat on mutation path, retention_hot_body_applied, audit_events, or NATS msg.event.index / msg.event.retention). See docs/RETENTION_AND_DEEP_ARCHIVE.md §9.",
    "worker.retention.shutdown_started": "Retention worker: graceful shutdown started",
    "worker.retention.shutdown_complete": "Retention worker: graceful shutdown complete",
    "worker.retention.failed": "Retention worker failed",
    "worker.retention.hot_body.minio_required_skip": "Hot-body retention skipped: RETENTION_REQUIRE_MINIO=true and MinIO is not configured",
    "worker.retention.hot_body.message_failed": "Retention hot-body failed messageId={0}: {1}",
    "worker.retention.hot_body.pass_cleared": "Retention hot-body pass: cleared {0} message(s)",
    "worker.retention.hot_body.advisory_unlock_failed": "Retention hot-body: pg_advisory_unlock failed: {0}",
    "worker.retention.hot_body.jdbc_close_failed": "Retention hot-body: failed closing pass JDBC connection: {0}",
    "worker.retention.hot_body.advisory_unlock_false": "Retention hot-body: pg_advisory_unlock returned false (session did not hold the lock)",
    "worker.retention.hot_body.row_race_skip": "Retention skip messageId={0}: row not updated (race or already cleared)",
    "worker.retention.hot_body.stat_unexpected": "Retention statObject unexpected response bucket={0} key={1}: {2}",
    "worker.retention.hot_body.stat_failed": "Retention statObject failed bucket={0} key={1}: {2}",
    "worker.retention.hot_body.bulk_audit_failed": "Retention bulk audit insert failed passId={0}: {1}",
    "worker.retention.hot_body.audit_failed": "Retention audit insert failed messageId={0}: {1}",
    "worker.retention.hot_body.skip_file_ref": "Retention skip snapshot for message {0}: content is file reference",
    "worker.retention.hot_body.temp_delete_failed": "Retention failed to delete temp snapshot file {0}: {1}",
    "worker.retention.hot_row.dry_run": "Retention hot-row purge dry-run: candidates={0}",
    "worker.retention.hot_row.purged": "Retention hot-row purge pass: purged={0}",
    "worker.retention.read_receipt.purged": "Read receipt retention purge: deleted={0} days={1}",
    "worker.retention.read_receipt.failed": "Read receipt retention purge failed: {0}",
    "worker.retention.file.dry_run": "File retention dry-run: candidates={0}",
    "worker.retention.file.deleted": "File retention pass: deleted={0}",
    "worker.retention.file.minio_delete_failed": "MinIO delete failed fileId={0} key={1}: {2}",
    "worker.retention.export_suggest_publish_failed": "Failed to publish {0} chatId={1}: {2}",
    "worker.retention.minio_bucket_created": "Created MinIO bucket {0} for retention snapshots",
    "worker.retention.minio_bucket_failed": "Retention MinIO bucket ensure failed bucket={0}: {1}",
    "worker.retention.shutdown.close_failed": "Retention shutdown: failed closing {0}",
    "worker.retention.shutdown.executor_timeout": "Retention shutdown: scan executor did not terminate within {0}s (in-flight pass may still be running; closing resources best-effort)",
    "worker.retention.shutdown.interrupted": "Retention shutdown: interrupted while awaiting scan executor termination",
}

RETENTION_RU = {
    "worker.module": "retention",
    "worker.retention.disabled": "Retention worker отключён (RETENTION_WORKER_ENABLED=false); idle. См. docs/RETENTION_AND_DEEP_ARCHIVE.md",
    "worker.retention.requires_db_nats": "RETENTION_WORKER_ENABLED=true требует DB_JDBC_URL и NATS_URL",
    "worker.retention.requires_db": "RETENTION_WORKER_ENABLED=true требует DB_JDBC_URL",
    "worker.retention.enabled_config": "Retention worker включён: interval={0}s initialDelay={1}s batchLimit={2} requireMinio={3} useAppliedLog={4} auditEnabled={5} bulkAuditMinCleared={6} retentionMinioBucket={7} retentionObjectPrefix={8} skipSnapshotIfDeepExists={9} minioDefaultBucket={10} postgresOnlyHotBody={11} jdbcQueryTimeoutSeconds={12} interMessageDelayMs={13} snapshotTempfileThresholdBytes={14} minioMultipartThresholdBytes={15} dryRun={16} useAdvisoryLock={17}",
    "worker.retention.first_scan_delay": "Retention worker: первый scan через {0}s initial delay",
    "worker.retention.postgres_skip": "Hot-body retention SQL только для PostgreSQL; jdbcUrl не jdbc:postgresql — purge pass пропущен",
    "worker.retention.scan_failed": "Retention scan pass failed",
    "worker.retention.db_ping_ok": "Hot DB ping OK",
    "worker.retention.db_ping_failed": "Hot DB ping failed: {0}",
    "worker.retention.metrics_start_failed": "Не удалось запустить Prometheus metrics HTTP server на порту {0}",
    "worker.retention.metrics_url": "Prometheus metrics: http://0.0.0.0:{0}/metrics; GET /health (тот же порт) для readiness",
    "worker.retention.dry_run_warn": "RETENTION_DRY_RUN=true: hot-body passes read-only. См. docs/RETENTION_AND_DEEP_ARCHIVE.md §9.",
    "worker.retention.shutdown_started": "Retention worker: graceful shutdown started",
    "worker.retention.shutdown_complete": "Retention worker: graceful shutdown complete",
    "worker.retention.failed": "Retention worker failed",
    "worker.retention.hot_body.minio_required_skip": "Hot-body retention пропущен: RETENTION_REQUIRE_MINIO=true и MinIO не настроен",
    "worker.retention.hot_body.message_failed": "Retention hot-body failed messageId={0}: {1}",
    "worker.retention.hot_body.pass_cleared": "Retention hot-body pass: cleared {0} message(s)",
    "worker.retention.hot_body.advisory_unlock_failed": "Retention hot-body: pg_advisory_unlock failed: {0}",
    "worker.retention.hot_body.jdbc_close_failed": "Retention hot-body: failed closing pass JDBC connection: {0}",
    "worker.retention.hot_body.advisory_unlock_false": "Retention hot-body: pg_advisory_unlock returned false (session did not hold the lock)",
    "worker.retention.hot_body.row_race_skip": "Retention skip messageId={0}: row not updated (race or already cleared)",
    "worker.retention.hot_body.stat_unexpected": "Retention statObject unexpected response bucket={0} key={1}: {2}",
    "worker.retention.hot_body.stat_failed": "Retention statObject failed bucket={0} key={1}: {2}",
    "worker.retention.hot_body.bulk_audit_failed": "Retention bulk audit insert failed passId={0}: {1}",
    "worker.retention.hot_body.audit_failed": "Retention audit insert failed messageId={0}: {1}",
    "worker.retention.hot_body.skip_file_ref": "Retention skip snapshot for message {0}: content is file reference",
    "worker.retention.hot_body.temp_delete_failed": "Retention failed to delete temp snapshot file {0}: {1}",
    "worker.retention.hot_row.dry_run": "Retention hot-row purge dry-run: candidates={0}",
    "worker.retention.hot_row.purged": "Retention hot-row purge pass: purged={0}",
    "worker.retention.read_receipt.purged": "Read receipt retention purge: deleted={0} days={1}",
    "worker.retention.read_receipt.failed": "Read receipt retention purge failed: {0}",
    "worker.retention.file.dry_run": "File retention dry-run: candidates={0}",
    "worker.retention.file.deleted": "File retention pass: deleted={0}",
    "worker.retention.file.minio_delete_failed": "MinIO delete failed fileId={0} key={1}: {2}",
    "worker.retention.export_suggest_publish_failed": "Failed to publish {0} chatId={1}: {2}",
    "worker.retention.minio_bucket_created": "Created MinIO bucket {0} for retention snapshots",
    "worker.retention.minio_bucket_failed": "Retention MinIO bucket ensure failed bucket={0}: {1}",
    "worker.retention.shutdown.close_failed": "Retention shutdown: failed closing {0}",
    "worker.retention.shutdown.executor_timeout": "Retention shutdown: scan executor did not terminate within {0}s (in-flight pass may still be running; closing resources best-effort)",
    "worker.retention.shutdown.interrupted": "Retention shutdown: interrupted while awaiting scan executor termination",
}


def write_props(base: Path, entries: dict[str, str]) -> None:
    base.parent.mkdir(parents=True, exist_ok=True)
    base.write_text("\n".join(f"{k}={v}" for k, v in entries.items()) + "\n", encoding="utf-8")


def patch_file(rel: str, replacements: list[tuple[str, str]], inserts: list[tuple[str, str]] | None = None) -> None:
    p = ROOT / rel
    text = p.read_text(encoding="utf-8")
    if inserts:
        for needle, insert in inserts:
            if insert.strip() not in text and needle in text:
                text = text.replace(needle, needle + insert, 1)
    for old, new in replacements:
        if old not in text:
            print(f"MISSING in {rel}: {old[:80]}")
        else:
            text = text.replace(old, new)
    p.write_text(text, encoding="utf-8")


def main() -> None:
    write_props(
        ROOT / "modules/workers/export-replay/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_export_replay_en.properties",
        EXPORT_EN,
    )
    write_props(
        ROOT / "modules/workers/export-replay/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_export_replay_ru.properties",
        EXPORT_RU,
    )
    write_props(
        ROOT / "modules/workers/retention/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_retention_en.properties",
        RETENTION_EN,
    )
    write_props(
        ROOT / "modules/workers/retention/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_retention_ru.properties",
        RETENTION_RU,
    )

    # ExportJobStore
    patch_file(
        "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportJobStore.java",
        inserts=[("import org.slf4j.Logger;", "\nimport com.avandocmsg.messenger.common.i18n.UserMessageSource;\n")],
        replacements=[
            ("    private final DataSource dataSource;\n", "    private final DataSource dataSource;\n    private final UserMessageSource workerMessages;\n"),
            ("    ExportJobStore(DataSource dataSource) {\n        this.dataSource = dataSource;\n    }",
             "    ExportJobStore(DataSource dataSource, UserMessageSource workerMessages) {\n        this.dataSource = dataSource;\n        this.workerMessages = workerMessages;\n    }"),
            ('log.warn("export_jobs markProcessingIfQueued failed jobId={}: {}", jobId, e.getMessage());',
             'log.warn(workerMessages.format("worker.export_replay.job_store.mark_failed", jobId, e.getMessage()));'),
            ('log.warn("export_jobs loadStatus failed jobId={}: {}", jobId, e.getMessage());',
             'log.warn(workerMessages.format("worker.export_replay.job_store.load_failed", jobId, e.getMessage()));'),
            ('log.warn("export_jobs row not found for jobId={}", jobId);',
             'log.warn(workerMessages.format("worker.export_replay.job_store.not_found", jobId));'),
            ('log.warn("export_jobs update failed jobId={} status={}: {}", jobId, status, e.getMessage());',
             'log.warn(workerMessages.format("worker.export_replay.job_store.update_failed", jobId, status, e.getMessage()));'),
        ],
    )

    patch_file(
        "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportAuditWriter.java",
        inserts=[("import org.slf4j.Logger;", "\nimport com.avandocmsg.messenger.common.i18n.UserMessageSource;\n")],
        replacements=[
            ("    private final DataSource dataSource;\n", "    private final DataSource dataSource;\n    private final UserMessageSource workerMessages;\n"),
            ("    ExportAuditWriter(DataSource dataSource) {\n        this.dataSource = dataSource;\n    }",
             "    ExportAuditWriter(DataSource dataSource, UserMessageSource workerMessages) {\n        this.dataSource = dataSource;\n        this.workerMessages = workerMessages;\n    }"),
            ('log.warn("export.completed audit insert failed jobId={}: {}", jobId, e.getMessage());',
             'log.warn(workerMessages.format("worker.export_replay.audit_insert_failed", jobId, e.getMessage()));'),
        ],
    )

    patch_file(
        "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportMinioUploader.java",
        inserts=[("import org.slf4j.Logger;", "\nimport com.avandocmsg.messenger.common.i18n.UserMessageSource;\n")],
        replacements=[
            ("    private final String bucket;\n", "    private final String bucket;\n    private final UserMessageSource workerMessages;\n"),
            ("    ExportMinioUploader(MinioClient client, String bucket) {\n        this.client = client;\n        this.bucket = bucket;\n    }",
             "    ExportMinioUploader(MinioClient client, String bucket, UserMessageSource workerMessages) {\n        this.client = client;\n        this.bucket = bucket;\n        this.workerMessages = workerMessages;\n    }"),
            ('log.info("Uploaded export to MinIO bucket={} key={} bytes={}", bucket, objectKey, size);',
             'log.info(workerMessages.format("worker.export_replay.minio_uploaded", bucket, objectKey, size));'),
        ],
    )

    patch_file(
        "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportMinioJsonFetcher.java",
        inserts=[("import org.slf4j.Logger;", "\nimport com.avandocmsg.messenger.common.i18n.UserMessageSource;\n")],
        replacements=[
            ("        String source\n    ) {",
             "        String source,\n        UserMessageSource workerMessages\n    ) {"),
            ('log.debug("MinIO stat/get failed source={} messageId={} key={}: {}", source, messageId, objectKey, e.getMessage());',
             'log.debug(workerMessages.format("worker.export_replay.minio_stat_failed", source, messageId, objectKey, e.getMessage()));'),
            ('log.warn("MinIO read failed source={} messageId={} bucket={} key={}: {}",\n                source, messageId, bucket, objectKey, e.getMessage());',
             'log.warn(workerMessages.format("worker.export_replay.minio_read_failed",\n                source, messageId, bucket, objectKey, e.getMessage()));'),
        ],
    )

    patch_file(
        "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportFileBundleBuilder.java",
        inserts=[("import org.slf4j.Logger;", "\nimport com.avandocmsg.messenger.common.i18n.UserMessageSource;\n")],
        replacements=[
            ("        long maxBytesPerFile\n    ) throws IOException {",
             "        long maxBytesPerFile,\n        UserMessageSource workerMessages\n    ) throws IOException {"),
            ('log.debug("Attachment skip fileId={}: {}", id, e.getMessage());',
             'log.debug(workerMessages.format("worker.export_replay.attachment_skip", id, e.getMessage()));'),
        ],
    )

    print("Properties + helper patches done.")


if __name__ == "__main__":
    main()
