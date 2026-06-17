package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.dto.RetentionAppliedEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotEnvelopeDigest;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.avandocmsg.messenger.common.retention.ChunkManifestWriter;
import com.avandocmsg.messenger.common.retention.ContentAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вынос тела сообщения из Hot DB по эффективной политике (платформа → org → {@code chat_retention_policy}),
 * снимок тела в MinIO и {@code MessageWorkerEvent} с {@code index_op=update} для Solr.
 */
final class RetentionHotBodyJanitor {
    private static final Logger log = LoggerFactory.getLogger(RetentionHotBodyJanitor.class);
    private static final ThreadLocal<UserMessageSource> LOG_MESSAGES = new ThreadLocal<>();

    private static UserMessageSource logMessages() {
        return LOG_MESSAGES.get();
    }
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Согласовано с {@code AuditRepository} / админским аудитом; массовые операции воркера. */
    private static final String AUDIT_ACTION_HOT_BODY_CLEARED = "message.retention.hot_body_cleared";
    /**
     * Одна строка на проход при {@code RETENTION_BULK_AUDIT_MIN_CLEARED} (порог по числу успешно очищенных тел за проход);
     * не заменяет построчный аудит {@link #AUDIT_ACTION_HOT_BODY_CLEARED} при {@code RETENTION_AUDIT_ENABLED=true}.
     */
    private static final String AUDIT_ACTION_BULK_CLEARED = "message.retention.bulk_cleared";

    private static final String SELECT_CANDIDATES_TEMPLATE = """
        SELECT m.id, m.chat_id, m.sender_id, m.client_msg_id, m.type, m.content,
               (EXTRACT(EPOCH FROM m.created_at) * 1000)::bigint AS created_ms
        FROM messages m
        LEFT JOIN chat_retention_policy crp ON crp.chat_id = m.chat_id
        LEFT JOIN org_retention_policy orp ON orp.org_id = COALESCE(
            (SELECT u.org_id FROM chats ch2 JOIN users u ON u.id = ch2.owner_id
             WHERE ch2.id = m.chat_id AND u.org_id IS NOT NULL LIMIT 1),
            (SELECT u.org_id FROM chat_members cm JOIN users u ON u.id = cm.user_id
             WHERE cm.chat_id = m.chat_id AND u.org_id IS NOT NULL
             ORDER BY CASE cm.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, cm.user_id
             LIMIT 1)
        )
        CROSS JOIN LATERAL (
            SELECT
                COALESCE(crp.hot_message_body_max_age_days, COALESCE(orp.hot_message_body_max_age_days, ?)) AS eff_body_days,
                CASE WHEN crp.chat_id IS NOT NULL THEN crp.legal_hold
                     ELSE COALESCE(orp.legal_hold, ?)
                END AS eff_legal,
                CASE WHEN crp.chat_id IS NOT NULL THEN crp.deep_archive_enabled
                     ELSE COALESCE(orp.deep_archive_enabled, ?)
                END AS eff_deep
        ) pol
        WHERE m.deleted = false
          AND m.content IS NOT NULL
          AND trim(m.content) <> ''
          AND pol.eff_body_days IS NOT NULL
          AND m.created_at < (CURRENT_TIMESTAMP - (pol.eff_body_days * INTERVAL '1 day'))
          AND pol.eff_legal = false
          AND pol.eff_deep = true
        %s
        ORDER BY m.created_at ASC
        LIMIT ?
        """;

    private static final String SQL_NOT_YET_APPLIED = """
          AND NOT EXISTS (SELECT 1 FROM retention_hot_body_applied r WHERE r.message_id = m.id)
        """;

    private static final String INSERT_APPLIED = """
        INSERT INTO retention_hot_body_applied (message_id, storage_object_key) VALUES (?, ?)
        ON CONFLICT (message_id) DO NOTHING
        """;

    private static final String UPDATE_CLEAR = """
        UPDATE messages SET content = NULL
        WHERE id = ? AND deleted = false AND content IS NOT NULL
        """;

    private static final String INSERT_AUDIT = """
        INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, details_json)
        VALUES (NULL, ?, ?, ?, ?)
        """;

    private RetentionHotBodyJanitor() {
    }

    /**
     * Full PostgreSQL candidate SELECT (with optional {@code retention_hot_body_applied} filter).
     * Package-private for unit tests that assert effective-policy predicates (legal hold, deep)
     * without a live database.
     */
    static String hotBodyCandidateSelectSql(boolean useAppliedLog) {
        return SELECT_CANDIDATES_TEMPLATE.formatted(useAppliedLog ? SQL_NOT_YET_APPLIED : "");
    }

    static int runOnce(
        DataSource dataSource,
        Connection nats,
        MinioClient minioClient,
        boolean minioEnabled,
        String retentionWriteBucket,
        String retentionObjectPrefix,
        RetentionPlatformDefaults platform,
        int batchLimit,
        boolean requireMinio,
        boolean useAppliedLog,
        boolean auditEnabled,
        int bulkAuditMinCleared,
        boolean skipSnapshotIfDeepExists,
        String minioDefaultBucket,
        int jdbcQueryTimeoutSeconds,
        int interMessageDelayMs,
        long snapshotTempfileThresholdBytes,
        long minioMultipartThresholdBytes,
        boolean dryRun,
        String jdbcUrl,
        boolean useAdvisoryLock,
        UserMessageSource workerMessages
    ) throws Exception {
        long passStartNanos = System.nanoTime();
        LOG_MESSAGES.set(workerMessages);
        UUID passUuid = UUID.randomUUID();
        String passId = passUuid.toString();
        java.sql.Connection passJdbcConn = null;
        boolean advisoryLockHeld = false;
        /** When {@code true}, pass-completion gauges run in {@code finally} after duration histogram. */
        boolean recordPassCompletionGauges = false;
        int passClearedCountForGauge = 0;
        try {
            if (requireMinio && !minioEnabled) {
                log.debug(workerMessages.get("worker.retention.hot_body.minio_required_skip"));
                RetentionMetrics.passSkippedMinioRequired();
                RetentionMetrics.observePassCandidates(0);
                return 0;
            }
            boolean wantAdvisoryLock =
                useAdvisoryLock && RetentionPlatformDefaults.jdbcLooksLikePostgres(jdbcUrl);
            if (wantAdvisoryLock) {
                passJdbcConn = dataSource.getConnection();
                advisoryLockHeld = trySessionAdvisoryLock(passJdbcConn, jdbcQueryTimeoutSeconds);
                if (!advisoryLockHeld) {
                    log.info(workerMessages.get("worker.retention.hot_body.advisory_lock_skip"));
                    RetentionMetrics.passSkippedAdvisoryLock();
                    RetentionMetrics.observePassCandidates(0);
                    return 0;
                }
            }
            var selectSql = hotBodyCandidateSelectSql(useAppliedLog);
            List<Candidate> batch = loadCandidateBatch(
                wantAdvisoryLock ? passJdbcConn : null,
                dataSource,
                selectSql,
                platform,
                batchLimit,
                jdbcQueryTimeoutSeconds
            );
            RetentionMetrics.observePassCandidates(batch.size());
            if (dryRun) {
                int wouldClear = batch.size();
                log.info(workerMessages.format("worker.retention.hot_body.dry_run_pass",
                    passId, batch.size(), wouldClear));
                publishExportSuggestedIfEnabled(nats, batch);
                RetentionMetrics.dryRunPassCompleted();
                recordPassCompletionGauges = true;
                passClearedCountForGauge = 0;
                return 0;
            }
            if (batch.isEmpty()) {
                recordPassCompletionGauges = true;
                passClearedCountForGauge = 0;
                return 0;
            }
            publishExportSuggestedIfEnabled(nats, batch);
            var sampleChatIds = sampleChatIdsFromBatch(batch, 5);
            int done = 0;
            int errors = 0;
            for (int i = 0; i < batch.size(); i++) {
                var c = batch.get(i);
                try {
                    if (processOne(
                        wantAdvisoryLock ? passJdbcConn : null,
                        dataSource,
                        nats,
                        minioClient,
                        minioEnabled,
                        retentionWriteBucket,
                        retentionObjectPrefix,
                        c,
                        useAppliedLog,
                        auditEnabled,
                        skipSnapshotIfDeepExists,
                        minioDefaultBucket,
                        jdbcQueryTimeoutSeconds,
                        snapshotTempfileThresholdBytes,
                        minioMultipartThresholdBytes,
                        passId
                    )) {
                        done++;
                    }
                } catch (Exception e) {
                    errors++;
                    RetentionMetrics.processingError();
                    log.warn(workerMessages.format("worker.retention.hot_body.message_failed", c.id(), e.getMessage()));
                }
                boolean hasMore = i < batch.size() - 1;
                if (hasMore && interMessageDelayMs > 0) {
                    if (RetentionInterMessageSleep.sleepQuiet(interMessageDelayMs)) {
                        log.warn(workerMessages.format("worker.retention.hot_body.interrupted",
                            interMessageDelayMs, done, errors, batch.size() - i - 1));
                        break;
                    }
                }
            }
            if (done > 0) {
                log.info(workerMessages.format("worker.retention.hot_body.pass_cleared", done));
            }
            long durationMs = (System.nanoTime() - passStartNanos) / 1_000_000L;
            if (RetentionBulkAudit.shouldRecordSummary(done, bulkAuditMinCleared)) {
                insertBulkAuditSummaryRow(
                    wantAdvisoryLock ? passJdbcConn : null,
                    dataSource,
                    passUuid,
                    done,
                    batchLimit,
                    batch.size(),
                    errors,
                    durationMs,
                    sampleChatIds
                );
            }
            recordPassCompletionGauges = true;
            passClearedCountForGauge = done;
            return done;
        } finally {
            if (advisoryLockHeld && passJdbcConn != null) {
                try {
                    releaseSessionAdvisoryLock(passJdbcConn, jdbcQueryTimeoutSeconds);
                } catch (SQLException e) {
                    log.warn(workerMessages.format("worker.retention.hot_body.advisory_unlock_failed", e.getMessage()));
                }
            }
            if (passJdbcConn != null) {
                try {
                    passJdbcConn.close();
                } catch (SQLException e) {
                    log.warn(workerMessages.format("worker.retention.hot_body.jdbc_close_failed", e.getMessage()));
                }
            }
            RetentionMetrics.observePassDurationSeconds((System.nanoTime() - passStartNanos) / 1_000_000_000.0);
            if (recordPassCompletionGauges) {
                RetentionMetrics.recordHotBodyPassCompletionGauges(Instant.now().getEpochSecond(), passClearedCountForGauge);
            }
            LOG_MESSAGES.remove();
        }
    }

    private static boolean trySessionAdvisoryLock(java.sql.Connection c, int jdbcQueryTimeoutSeconds) throws SQLException {
        try (var st = c.createStatement()) {
            applyStatementQueryTimeout(st, jdbcQueryTimeoutSeconds);
            try (var rs = st.executeQuery(RetentionAdvisoryLockIds.tryLockQuery())) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void releaseSessionAdvisoryLock(java.sql.Connection c, int jdbcQueryTimeoutSeconds) throws SQLException {
        try (var st = c.createStatement()) {
            applyStatementQueryTimeout(st, jdbcQueryTimeoutSeconds);
            try (var rs = st.executeQuery(RetentionAdvisoryLockIds.unlockQuery())) {
                if (rs.next() && !rs.getBoolean(1)) {
                    log.debug(logMessages().get("worker.retention.hot_body.advisory_unlock_false"));
                }
            }
        }
    }

    private static List<Candidate> loadCandidateBatch(
        java.sql.Connection sharedJdbc,
        DataSource dataSource,
        String selectSql,
        RetentionPlatformDefaults platform,
        int batchLimit,
        int jdbcQueryTimeoutSeconds
    ) throws SQLException {
        if (sharedJdbc != null) {
            return loadCandidateBatchOnConnection(sharedJdbc, selectSql, platform, batchLimit, jdbcQueryTimeoutSeconds);
        }
        try (var conn = dataSource.getConnection()) {
            return loadCandidateBatchOnConnection(conn, selectSql, platform, batchLimit, jdbcQueryTimeoutSeconds);
        }
    }

    private static List<Candidate> loadCandidateBatchOnConnection(
        java.sql.Connection conn,
        String selectSql,
        RetentionPlatformDefaults platform,
        int batchLimit,
        int jdbcQueryTimeoutSeconds
    ) throws SQLException {
        List<Candidate> batch = new ArrayList<>();
        try (var ps = conn.prepareStatement(selectSql)) {
            applyStatementQueryTimeout(ps, jdbcQueryTimeoutSeconds);
            int i = 1;
            if (platform.hotBodyMaxAgeDays() == null) {
                ps.setNull(i++, Types.INTEGER);
            } else {
                ps.setInt(i++, platform.hotBodyMaxAgeDays());
            }
            ps.setBoolean(i++, platform.defaultLegalHold());
            ps.setBoolean(i++, platform.defaultDeepArchiveEnabled());
            ps.setInt(i++, batchLimit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    batch.add(new Candidate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("chat_id", UUID.class),
                        rs.getObject("sender_id", UUID.class),
                        rs.getString("client_msg_id"),
                        rs.getString("type"),
                        rs.getString("content"),
                        rs.getLong("created_ms")
                    ));
                }
            }
        }
        return batch;
    }

    private static boolean processOne(
        java.sql.Connection sharedJdbc,
        DataSource dataSource,
        Connection nats,
        MinioClient minioClient,
        boolean minioEnabled,
        String retentionWriteBucket,
        String retentionObjectPrefix,
        Candidate c,
        boolean useAppliedLog,
        boolean auditEnabled,
        boolean skipSnapshotIfDeepExists,
        String minioDefaultBucket,
        int jdbcQueryTimeoutSeconds,
        long snapshotTempfileThresholdBytes,
        long minioMultipartThresholdBytes,
        String passId
    ) throws Exception {
        int contentUtf8Bytes = RetentionSnapshotMaterialization.utf8ByteLength(c.content());
        var retentionSnapKey = minioEnabled ? retentionObjectPrefix + c.id() + ".json" : null;
        String storageKey = retentionSnapKey;
        String snapshotSha256 = null;
        if (minioEnabled) {
            var snapshotResult = RetentionSnapshotWriter.persistSnapshot(
                minioClient,
                retentionWriteBucket,
                retentionObjectPrefix,
                minioDefaultBucket,
                c,
                passId,
                skipSnapshotIfDeepExists,
                snapshotTempfileThresholdBytes,
                minioMultipartThresholdBytes,
                contentUtf8Bytes
            );
            storageKey = snapshotResult.storageKey();
            snapshotSha256 = snapshotResult.snapshotSha256();
        }
        int updated;
        if (sharedJdbc != null) {
            try (var ps = sharedJdbc.prepareStatement(UPDATE_CLEAR)) {
                applyStatementQueryTimeout(ps, jdbcQueryTimeoutSeconds);
                ps.setObject(1, c.id());
                updated = ps.executeUpdate();
            }
        } else {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement(UPDATE_CLEAR)) {
                applyStatementQueryTimeout(ps, jdbcQueryTimeoutSeconds);
                ps.setObject(1, c.id());
                updated = ps.executeUpdate();
            }
        }
        if (updated == 0) {
            RetentionMetrics.rowNotUpdated();
            log.debug(logMessages().format("worker.retention.hot_body.row_race_skip", c.id()));
            return false;
        }
        var evt = MessageWorkerEvent.fromPersistedMessage(
            c.id().toString(),
            c.chatId().toString(),
            c.senderId().toString(),
            c.clientMsgId(),
            c.createdMs(),
            c.type() != null ? c.type() : "text",
            null,
            "update"
        );
        nats.publish(NatsSubjects.MSG_EVENT_INDEX, MAPPER.writeValueAsBytes(evt));
        var clearedBytes = contentUtf8Bytes;
        var retentionEvt = RetentionAppliedEvent.hotBodyCleared(
            c.id().toString(),
            c.chatId().toString(),
            storageKey,
            clearedBytes,
            System.currentTimeMillis(),
            ArchiveSnapshotFormat.SNAPSHOT_VERSION,
            passId,
            snapshotSha256
        );
        nats.publish(NatsSubjects.MSG_EVENT_RETENTION, MAPPER.writeValueAsBytes(retentionEvt));
        if (useAppliedLog) {
            if (sharedJdbc != null) {
                try (var ps = sharedJdbc.prepareStatement(INSERT_APPLIED)) {
                    ps.setObject(1, c.id());
                    ps.setString(2, storageKey);
                    ps.executeUpdate();
                }
            } else {
                try (var conn = dataSource.getConnection();
                     var ps = conn.prepareStatement(INSERT_APPLIED)) {
                    ps.setObject(1, c.id());
                    ps.setString(2, storageKey);
                    ps.executeUpdate();
                }
            }
        }
        /*
         * Per-message audit (RETENTION_AUDIT_ENABLED): one audit_events row per cleared message.
         * When RETENTION_BULK_AUDIT_MIN_CLEARED is met, runOnce also inserts a single summary row
         * (message.retention.bulk_cleared); that bulk row is additional telemetry, not a substitute
         * and does not suppress these per-message rows.
         */
        if (auditEnabled) {
            insertAuditRow(sharedJdbc, dataSource, c.id().toString(), c.chatId().toString(), storageKey, clearedBytes, snapshotSha256, passId);
        }
        RetentionMetrics.hotBodyCleared();
        return true;
    }

    private static boolean minioObjectExists(MinioClient minioClient, String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (ErrorResponseException e) {
            var code = e.errorResponse() != null ? e.errorResponse().code() : "";
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return false;
            }
            log.debug(logMessages().format("worker.retention.hot_body.stat_unexpected", bucket, objectKey, e.getMessage()));
            return false;
        } catch (Exception e) {
            log.debug(logMessages().format("worker.retention.hot_body.stat_failed", bucket, objectKey, e.getMessage()));
            return false;
        }
    }

    private static void publishExportSuggestedIfEnabled(Connection nats, List<Candidate> batch) {
        if (RetentionPlatformDefaults.publishExportSuggestedFromEnv() && nats != null && !batch.isEmpty()) {
            RetentionExportSuggester.publishForChatCounts(nats, candidateCountByChatId(batch), logMessages());
        }
    }

    private static Map<UUID, Integer> candidateCountByChatId(List<Candidate> batch) {
        var counts = new HashMap<UUID, Integer>();
        for (var c : batch) {
            counts.merge(c.chatId(), 1, Integer::sum);
        }
        return counts;
    }

    private static List<UUID> sampleChatIdsFromBatch(List<Candidate> batch, int maxIds) {
        var seen = new LinkedHashSet<UUID>();
        for (var c : batch) {
            if (seen.size() >= maxIds) {
                break;
            }
            seen.add(c.chatId());
        }
        return new ArrayList<>(seen);
    }

    private static void insertBulkAuditSummaryRow(
        java.sql.Connection sharedJdbc,
        DataSource dataSource,
        UUID passId,
        int clearedCount,
        int batchLimit,
        int candidateCount,
        int errorsCount,
        long durationMs,
        List<UUID> sampleChatIds
    ) {
        var details = MAPPER.createObjectNode();
        details.put("pass_id", passId.toString());
        details.put("run_timestamp", Instant.now().toString());
        details.put("cleared_count", clearedCount);
        details.put("batch_limit", batchLimit);
        details.put("candidate_count", candidateCount);
        details.put("errors_count", errorsCount);
        details.put("duration_ms", durationMs);
        var arr = details.putArray("sample_chat_ids");
        for (var id : sampleChatIds) {
            arr.add(id.toString());
        }
        try {
            if (sharedJdbc != null) {
                try (var ps = sharedJdbc.prepareStatement(INSERT_AUDIT)) {
                    ps.setString(1, AUDIT_ACTION_BULK_CLEARED);
                    ps.setString(2, "retention_pass");
                    ps.setString(3, passId.toString());
                    ps.setString(4, details.toString());
                    ps.executeUpdate();
                }
            } else {
                try (var conn = dataSource.getConnection();
                     var ps = conn.prepareStatement(INSERT_AUDIT)) {
                    ps.setString(1, AUDIT_ACTION_BULK_CLEARED);
                    ps.setString(2, "retention_pass");
                    ps.setString(3, passId.toString());
                    ps.setString(4, details.toString());
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            RetentionMetrics.auditInsertFailed();
            log.warn(logMessages().format("worker.retention.hot_body.bulk_audit_failed", passId, e.getMessage()));
        }
    }

    private static void insertAuditRow(
        java.sql.Connection sharedJdbc,
        DataSource dataSource,
        String messageId,
        String chatId,
        String storageObjectKey,
        int clearedContentUtf8Bytes,
        String snapshotSha256,
        String passId
    ) {
        var details = MAPPER.createObjectNode();
        details.put("chat_id", chatId);
        details.put("cleared_content_utf8_bytes", clearedContentUtf8Bytes);
        if (passId != null) {
            details.put("pass_id", passId);
        }
        if (storageObjectKey != null) {
            details.put("storage_object_key", storageObjectKey);
        }
        if (snapshotSha256 != null) {
            details.put(ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256, snapshotSha256);
        }
        try {
            if (sharedJdbc != null) {
                try (var ps = sharedJdbc.prepareStatement(INSERT_AUDIT)) {
                    ps.setString(1, AUDIT_ACTION_HOT_BODY_CLEARED);
                    ps.setString(2, "message");
                    ps.setString(3, messageId);
                    ps.setString(4, details.toString());
                    ps.executeUpdate();
                }
            } else {
                try (var conn = dataSource.getConnection();
                     var ps = conn.prepareStatement(INSERT_AUDIT)) {
                    ps.setString(1, AUDIT_ACTION_HOT_BODY_CLEARED);
                    ps.setString(2, "message");
                    ps.setString(3, messageId);
                    ps.setString(4, details.toString());
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            RetentionMetrics.auditInsertFailed();
            log.warn(logMessages().format("worker.retention.hot_body.audit_failed", messageId, e.getMessage()));
        }
    }

    /** JDBC: {@code seconds <= 0} — не вызывать {@link java.sql.Statement#setQueryTimeout(int)} (дефолт драйвера). */
    private static void applyStatementQueryTimeout(java.sql.PreparedStatement ps, int seconds) throws SQLException {
        if (seconds > 0) {
            ps.setQueryTimeout(seconds);
        }
    }

    private static void applyStatementQueryTimeout(Statement st, int seconds) throws SQLException {
        if (seconds > 0) {
            st.setQueryTimeout(seconds);
        }
    }

    /**
     * MinIO JSON snapshot for hot-body retention (envelope + legacy body fields). Kept package-private for tests.
     * Integrity digest {@link ArchiveSnapshotFormat#JSON_SNAPSHOT_SHA256} is <strong>not</strong> set here;
     * {@link ArchiveSnapshotEnvelopeDigest#computeAndAttach} appends it immediately before upload / NATS.
     *
     * @param passId same UUID string as {@code RetentionAppliedEvent.pass_id} for this {@code runOnce} pass; omitted from JSON when {@code null}
     */
    static ObjectNode minioSnapshotPayload(
        ObjectMapper mapper,
        UUID messageId,
        UUID chatId,
        UUID senderId,
        String type,
        long createdMs,
        String content,
        String passId
    ) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put(ArchiveSnapshotFormat.JSON_SNAPSHOT_VERSION, ArchiveSnapshotFormat.SNAPSHOT_VERSION);
        payload.put(ArchiveSnapshotFormat.JSON_PRODUCER, ArchiveSnapshotFormat.PRODUCER_RETENTION);
        if (passId != null) {
            payload.put("pass_id", passId);
        }
        payload.put("message_id", messageId.toString());
        payload.put("chat_id", chatId.toString());
        payload.put("sender_id", senderId.toString());
        payload.put("type", type != null ? type : "text");
        payload.put("created_at_epoch_ms", createdMs);
        payload.put("content", content);
        return payload;
    }

    static boolean shouldSkipSnapshotForContent(String content) {
        return ContentAnalyzer.isFileReference(content);
    }

    static boolean shouldWriteChunkedSnapshot(long chunkThreshold, long payloadBytes) {
        return ChunkManifestWriter.shouldWriteChunked(chunkThreshold, payloadBytes);
    }

    private record SnapshotStoreResult(String storageKey, String snapshotSha256) {
    }

    private static final class RetentionSnapshotWriter {
        private RetentionSnapshotWriter() {
        }

        private static SnapshotStoreResult persistSnapshot(
            MinioClient minioClient,
            String retentionWriteBucket,
            String retentionObjectPrefix,
            String minioDefaultBucket,
            Candidate candidate,
            String passId,
            boolean skipSnapshotIfDeepExists,
            long snapshotTempfileThresholdBytes,
            long minioMultipartThresholdBytes,
            int contentUtf8Bytes
        ) throws Exception {
            if (shouldSkipSnapshotForContent(candidate.content())) {
                log.debug(logMessages().format("worker.retention.hot_body.skip_file_ref", candidate.id()));
                RetentionMetrics.minioSnapshotSkippedExisting("file_ref");
                RetentionMetrics.fileRefSkipped();
                return new SnapshotStoreResult(null, null);
            }

            var retentionSnapKey = retentionObjectPrefix + candidate.id() + ".json";
            String storageKey = retentionSnapKey;
            boolean doPut = true;
            if (skipSnapshotIfDeepExists) {
                if (RetentionSnapshotSkipResolver.sameBucketAsDeepArchive(retentionWriteBucket, minioDefaultBucket)) {
                    var deepKey = RetentionSnapshotSkipResolver.deepArchiveObjectKey(candidate.id());
                    if (minioObjectExists(minioClient, retentionWriteBucket, deepKey)) {
                        doPut = false;
                        storageKey = deepKey;
                        RetentionMetrics.minioSnapshotSkippedExisting("deep");
                    }
                }
                if (doPut && minioObjectExists(minioClient, retentionWriteBucket, retentionSnapKey)) {
                    doPut = false;
                    storageKey = retentionSnapKey;
                    RetentionMetrics.minioSnapshotSkippedExisting("retention");
                }
            }

            ObjectNode payload = minioSnapshotPayload(
                MAPPER,
                candidate.id(),
                candidate.chatId(),
                candidate.senderId(),
                candidate.type(),
                candidate.createdMs(),
                candidate.content(),
                passId
            );
            var snapshotSha256 = ArchiveSnapshotEnvelopeDigest.computeAndAttach(MAPPER, payload);
            if (doPut) {
                uploadSnapshotPayload(
                    minioClient,
                    retentionWriteBucket,
                    retentionObjectPrefix,
                    candidate.id(),
                    retentionSnapKey,
                    payload,
                    snapshotTempfileThresholdBytes,
                    minioMultipartThresholdBytes,
                    contentUtf8Bytes
                );
            }
            return new SnapshotStoreResult(storageKey, snapshotSha256);
        }

        private static void uploadSnapshotPayload(
            MinioClient minioClient,
            String retentionWriteBucket,
            String retentionObjectPrefix,
            UUID messageId,
            String retentionSnapKey,
            ObjectNode payload,
            long snapshotTempfileThresholdBytes,
            long minioMultipartThresholdBytes,
            int contentUtf8Bytes
        ) throws Exception {
            long chunkThreshold = RetentionPlatformDefaults.chunkThresholdBytesFromEnv();
            if (RetentionSnapshotMaterialization.shouldUseTempFile(snapshotTempfileThresholdBytes, contentUtf8Bytes)) {
                uploadThroughTempFile(
                    minioClient,
                    retentionWriteBucket,
                    retentionObjectPrefix,
                    messageId,
                    retentionSnapKey,
                    payload,
                    chunkThreshold,
                    minioMultipartThresholdBytes
                );
                return;
            }

            byte[] bytes = MAPPER.writeValueAsBytes(payload);
            if (shouldWriteChunkedSnapshot(chunkThreshold, bytes.length)) {
                writeRetentionChunks(minioClient, retentionWriteBucket, retentionObjectPrefix, messageId, bytes);
            } else {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(retentionWriteBucket)
                        .object(retentionSnapKey)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("application/json")
                        .build()
                );
                RetentionMetrics.minioSnapshotUploaded(bytes.length);
            }
        }

        private static void uploadThroughTempFile(
            MinioClient minioClient,
            String retentionWriteBucket,
            String retentionObjectPrefix,
            UUID messageId,
            String retentionSnapKey,
            ObjectNode payload,
            long chunkThreshold,
            long minioMultipartThresholdBytes
        ) throws Exception {
            Path tmp = null;
            try {
                tmp = Files.createTempFile("retention-snapshot-", ".json");
                MAPPER.writeValue(tmp.toFile(), payload);
                long fileSize = Files.size(tmp);
                if (shouldWriteChunkedSnapshot(chunkThreshold, fileSize)) {
                    writeRetentionChunksFromFile(
                        minioClient, retentionWriteBucket, retentionObjectPrefix, messageId, tmp, chunkThreshold);
                } else if (fileSize >= minioMultipartThresholdBytes) {
                    minioClient.uploadObject(
                        UploadObjectArgs.builder()
                            .bucket(retentionWriteBucket)
                            .object(retentionSnapKey)
                            .filename(tmp.toAbsolutePath().toString())
                            .contentType("application/json")
                            .build()
                    );
                    RetentionMetrics.minioMultipartUploadSucceeded();
                } else {
                    try (InputStream in = Files.newInputStream(tmp)) {
                        minioClient.putObject(
                            PutObjectArgs.builder()
                                .bucket(retentionWriteBucket)
                                .object(retentionSnapKey)
                                .stream(in, fileSize, -1)
                                .contentType("application/json")
                                .build()
                        );
                    }
                }
                int metricBytes = fileSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fileSize;
                RetentionMetrics.minioSnapshotUploaded(metricBytes);
                RetentionMetrics.minioSnapshotTempfileUsed();
            } finally {
                if (tmp != null) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (Exception delEx) {
                        log.warn(logMessages().format("worker.retention.hot_body.temp_delete_failed", tmp, delEx.getMessage()));
                    }
                }
            }
        }
    }

    private static void writeRetentionChunks(
        MinioClient client,
        String bucket,
        String prefix,
        UUID messageId,
        byte[] bytes
    ) throws Exception {
        var dir = ChunkManifestWriter.objectPrefixDir(prefix, messageId.toString());
        ChunkManifestWriter.writeChunkedSnapshot(
            client,
            bucket,
            dir,
            messageId.toString(),
            bytes,
            ChunkManifestWriter.resolveChunkSizeBytes(RetentionPlatformDefaults.chunkThresholdBytesFromEnv()),
            MAPPER
        );
        RetentionMetrics.chunkWrite();
    }

    private static void writeRetentionChunksFromFile(
        MinioClient client,
        String bucket,
        String prefix,
        UUID messageId,
        Path file,
        long chunkThreshold
    ) throws Exception {
        var dir = ChunkManifestWriter.objectPrefixDir(prefix, messageId.toString());
        ChunkManifestWriter.writeChunkedSnapshotFromFile(
            client,
            bucket,
            dir,
            messageId.toString(),
            file,
            ChunkManifestWriter.resolveChunkSizeBytes(chunkThreshold),
            MAPPER
        );
        RetentionMetrics.chunkWrite();
    }

    private record Candidate(
        UUID id,
        UUID chatId,
        UUID senderId,
        String clientMsgId,
        String type,
        String content,
        long createdMs
    ) {
    }
}
