package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.dto.ExportReplayCancelEvent;
import com.avandocmsg.messenger.common.dto.ExportReplayCompleteEvent;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.export.ExportCompletenessConfig;
import com.avandocmsg.messenger.common.export.ExportCompletenessValidator;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs export-replay jobs: JDBC export pipeline, artifact write, job store, and completion publish.
 * NATS subscription wiring lives in {@link ExportReplayNatsConsumer}.
 */
public class ExportReplayJobRunner {
    private static final Logger log = LoggerFactory.getLogger(ExportReplayJobRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SQL_REFERENCED_FILES = ExportReferencedFilesSql.REFERENCED_FILES;

    /** Heuristic: files uploaded by chat members, excluding IDs already in {@code referencedFiles}. */
    private static final String SQL_E2EE_FILE_CANDIDATES = ExportReferencedFilesSql.E2EE_FILE_CANDIDATES;

    /**
     * Same visibility rule as {@code MessageRepository#SQL_MSG_VISIBILITY_TTL_VISIBLE} (unqualified column names on {@code messages}).
     */
    static final String SQL_MSG_VISIBILITY_TTL_VISIBLE = ExportMessageLoader.SQL_MSG_VISIBILITY_TTL_VISIBLE;

    private static final String SQL_CHAT = """
        SELECT id, title, type, owner_id, avatar_file_id, hidden, muted, ttl_seconds, created_at, updated_at
        FROM chats
        WHERE id = ?::uuid
        """;

    private static final String SQL_CHAT_MEMBERS = """
        SELECT user_id, role, joined_at, muted, banned, personal_filter_active
        FROM chat_members
        WHERE chat_id = ?::uuid
        ORDER BY joined_at ASC, user_id
        LIMIT ?
        """;

    private final ExportReplayNatsConsumer natsConsumer;
    private final Path exportDir;
    private final boolean publishComplete;
    private final DataSource dataSource;
    private final int maxExportMessages;
    private final int jdbcQueryTimeoutSeconds;
    private final boolean includeVersions;
    private final int maxVersionRows;
    private final boolean includeReactions;
    private final int maxReactionRows;
    private final boolean includePins;
    private final int maxPinnedRows;
    private final boolean includeChat;
    private final boolean includeChatMembers;
    private final int maxChatMemberRows;
    private final boolean includeReferencedUsers;
    private final int maxReferencedUserRows;
    private final boolean includeReferencedFiles;
    private final int maxFileIdsFromContent;
    private final int maxReferencedFileRows;
    private final boolean messageTtlFilterApplied;
    private final boolean includeRetentionPolicy;
    private final boolean includeExportCompleteness;
    private final boolean includeDeepArchiveSnapshots;
    private final int maxDeepArchiveSnapshots;
    private final ExportPlatformDefaults platformDefaults;
    private final ExportDeepArchiveReader deepArchiveReader;
    private final boolean includeRetentionSnapshots;
    private final int maxRetentionSnapshots;
    private final ExportRetentionSnapshotReader retentionSnapshotReader;
    private final boolean includeSolrIndex;
    private final int maxSolrDocs;
    private final ExportSolrReader solrReader;
    private final String sqlMessages;
    private final String sqlMessageVersions;
    private final String sqlMessageReactions;
    private final String sqlPinnedMessages;
    private final String sqlReferencedUsers;
    private final ExportJobStore jobStore;
    private final ExportAuditWriter auditWriter;
    private final ExportMinioUploader minioUploader;
    private final boolean includeFileBodies;
    private final int maxFileBodies;
    private final long maxFileBodyBytes;
    private final ExportFileBodyFetcher fileBodyFetcher;
    private final int cancelCheckEveryRows;
    private final long debugDelayMs;
    private final UserMessageSource workerMessages;

    public ExportReplayJobRunner(
        ExportReplayNatsConsumer natsConsumer,
        Path exportDir,
        boolean publishComplete,
        DataSource dataSource,
        int maxExportMessages,
        int jdbcQueryTimeoutSeconds,
        boolean includeVersions,
        int maxVersionRows,
        boolean includeReactions,
        int maxReactionRows,
        boolean includePins,
        int maxPinnedRows,
        boolean includeChat,
        boolean includeChatMembers,
        int maxChatMemberRows,
        boolean includeReferencedUsers,
        int maxReferencedUserRows,
        boolean includeReferencedFiles,
        int maxFileIdsFromContent,
        int maxReferencedFileRows,
        boolean messageTtlFilterApplied,
        ExportMinioUploader minioUploader,
        boolean includeRetentionPolicy,
        boolean includeExportCompleteness,
        ExportPlatformDefaults platformDefaults,
        boolean includeDeepArchiveSnapshots,
        int maxDeepArchiveSnapshots,
        ExportDeepArchiveReader deepArchiveReader,
        boolean includeRetentionSnapshots,
        int maxRetentionSnapshots,
        ExportRetentionSnapshotReader retentionSnapshotReader,
        boolean includeSolrIndex,
        int maxSolrDocs,
        ExportSolrReader solrReader,
        boolean includeFileBodies,
        int maxFileBodies,
        long maxFileBodyBytes,
        ExportFileBodyFetcher fileBodyFetcher,
        UserMessageSource workerMessages
    ) {
        this.natsConsumer = natsConsumer;
        this.exportDir = exportDir;
        this.publishComplete = publishComplete;
        this.dataSource = dataSource;
        this.maxExportMessages = maxExportMessages > 0 ? maxExportMessages : 100_000;
        this.jdbcQueryTimeoutSeconds = jdbcQueryTimeoutSeconds > 0 ? jdbcQueryTimeoutSeconds : 0;
        this.includeVersions = includeVersions;
        this.maxVersionRows = maxVersionRows > 0 ? maxVersionRows : 500_000;
        this.includeReactions = includeReactions;
        this.maxReactionRows = maxReactionRows > 0 ? maxReactionRows : 500_000;
        this.includePins = includePins;
        this.maxPinnedRows = maxPinnedRows > 0 ? maxPinnedRows : 50_000;
        this.includeChat = includeChat;
        this.includeChatMembers = includeChatMembers;
        this.maxChatMemberRows = maxChatMemberRows > 0 ? maxChatMemberRows : 100_000;
        this.includeReferencedUsers = includeReferencedUsers;
        this.maxReferencedUserRows = maxReferencedUserRows > 0 ? maxReferencedUserRows : 50_000;
        this.includeReferencedFiles = includeReferencedFiles;
        this.maxFileIdsFromContent = maxFileIdsFromContent > 0 ? maxFileIdsFromContent : 50_000;
        this.maxReferencedFileRows = maxReferencedFileRows > 0 ? maxReferencedFileRows : 100_000;
        this.messageTtlFilterApplied = messageTtlFilterApplied;
        this.includeRetentionPolicy = includeRetentionPolicy;
        this.includeExportCompleteness = includeExportCompleteness;
        this.includeDeepArchiveSnapshots = includeDeepArchiveSnapshots;
        this.maxDeepArchiveSnapshots = maxDeepArchiveSnapshots > 0 ? maxDeepArchiveSnapshots : 500;
        this.platformDefaults = platformDefaults != null ? platformDefaults : ExportPlatformDefaults.fromEnv();
        this.deepArchiveReader = deepArchiveReader;
        this.includeRetentionSnapshots = includeRetentionSnapshots;
        this.maxRetentionSnapshots = maxRetentionSnapshots > 0 ? maxRetentionSnapshots : 500;
        this.retentionSnapshotReader = retentionSnapshotReader;
        this.includeSolrIndex = includeSolrIndex;
        this.maxSolrDocs = maxSolrDocs > 0 ? maxSolrDocs : 10_000;
        this.solrReader = solrReader;
        this.sqlMessages = ExportMessageLoader.buildMessagesSql(messageTtlFilterApplied);
        this.sqlMessageVersions = ExportMessageLoader.buildMessageVersionsSql(messageTtlFilterApplied);
        this.sqlMessageReactions = ExportMessageLoader.buildMessageReactionsSql(messageTtlFilterApplied);
        this.sqlPinnedMessages = ExportMessageLoader.buildPinnedMessagesSql(messageTtlFilterApplied);
        this.sqlReferencedUsers = ExportMessageLoader.buildReferencedUsersSql(messageTtlFilterApplied);
        this.jobStore = dataSource != null ? new ExportJobStore(dataSource, workerMessages) : null;
        this.auditWriter = dataSource != null ? new ExportAuditWriter(dataSource, workerMessages) : null;
        this.minioUploader = minioUploader;
        this.includeFileBodies = includeFileBodies;
        this.maxFileBodies = maxFileBodies > 0 ? maxFileBodies : 500;
        this.maxFileBodyBytes = maxFileBodyBytes > 0 ? maxFileBodyBytes : 52_428_800L;
        this.fileBodyFetcher = fileBodyFetcher;
        this.workerMessages = workerMessages;
        this.cancelCheckEveryRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_CANCEL_CHECK_EVERY_ROWS"), 500);
        this.debugDelayMs = parseNonNegativeLong(System.getenv("EXPORT_REPLAY_DEBUG_DELAY_MS"), 0L);
        if (this.debugDelayMs > 0) {
            log.warn(workerMessages.format("worker.export_replay.debug_delay_warn", this.debugDelayMs));
        }
    }

    void logSubscribed() {
        log.info(workerMessages.format("worker.export_replay.subscribed",
            NatsSubjects.MSG_EXPORT_REPLAY,
            ExportReplayNatsConsumer.QUEUE_GROUP,
            exportDir,
            dataSource != null,
            maxExportMessages,
            includeVersions,
            maxVersionRows,
            includeReactions,
            maxReactionRows,
            includePins,
            maxPinnedRows,
            includeChat,
            includeChatMembers,
            maxChatMemberRows,
            includeReferencedUsers,
            maxReferencedUserRows,
            includeReferencedFiles,
            maxFileIdsFromContent,
            maxReferencedFileRows,
            messageTtlFilterApplied
        ));
    }

    public void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var job = MAPPER.readValue(payload, ExportReplayJob.class);
            if (job.jobId() == null || job.jobId().isBlank() || job.chatId() == null || job.chatId().isBlank()) {
                log.warn(workerMessages.format("worker.export_replay.invalid_payload", payload));
                ExportReplayMetrics.jobSkipped("invalid_payload");
                return;
            }
            var safeJobId = ExportOutputRef.safeJobIdForFilename(job.jobId());
            var out = exportDir.resolve(safeJobId + ".export.json");

            if (dataSource == null) {
                writeStub(out, job);
                log.info(workerMessages.format("worker.export_replay.stub_written", job.jobId(), out.toAbsolutePath()));
                finishJob(job, "stub_written", out);
                return;
            }

            var jobUuid = parseJobId(job.jobId());
            if (jobStore != null && jobUuid != null && !jobStore.markProcessingIfQueued(jobUuid)) {
                log.info(workerMessages.format("worker.export_replay.job_skipped", job.jobId()));
                ExportReplayMetrics.jobSkipped("not_queued");
                return;
            }
            if (jobStore != null && jobUuid != null) {
                ExportReplayMetrics.jobStarted();
            }
            sleepDebugDelayIfConfigured(job.jobId());

            try {
                var root = exportFromDatabase(job, jobUuid);
                if (!applyCompletenessValidation(root, job)) {
                    writeError(out, job, "completeness_failed", "mandatory_fields_missing");
                    if (!abortIfCancelled(jobUuid, out)) {
                        finishJob(job, "export_failed", out);
                    }
                    return;
                }
                Path artifact = out;
                if (includeFileBodies && fileBodyFetcher != null && includeReferencedFiles) {
                    var zip = exportDir.resolve(safeJobId + ".export.zip");
                    try {
                        ExportFileBundleBuilder.build(root, zip, fileBodyFetcher, maxFileBodies, maxFileBodyBytes, workerMessages);
                        artifact = zip;
                        Files.deleteIfExists(out);
                    } catch (IOException zipErr) {
                        log.warn(workerMessages.format("worker.export_replay.zip_failed", job.jobId(), zipErr.getMessage()));
                        Files.writeString(
                            out,
                            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                            StandardCharsets.UTF_8
                        );
                    }
                } else {
                    Files.writeString(
                        out,
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                        StandardCharsets.UTF_8
                    );
                }
                log.info(workerMessages.format("worker.export_replay.export_written",
                    job.jobId(),
                    artifact.toAbsolutePath(),
                    root.path("messageCount").asInt(0)));
                if (!abortIfCancelled(jobUuid, artifact, out)) {
                    finishJob(job, "export_v1", artifact);
                }
            } catch (ExportCancelledException e) {
                abortIfCancelled(e.jobId(), out);
            } catch (IllegalArgumentException e) {
                log.warn(workerMessages.format("worker.export_replay.invalid_chat_uuid", job.jobId(), job.chatId()));
                writeError(out, job, "invalid_chat_id", e.getMessage());
                if (!abortIfCancelled(jobUuid, out)) {
                    finishJob(job, "export_failed", out);
                }
            } catch (SQLException e) {
                log.error(workerMessages.format("worker.export_replay.db_query_failed", job.jobId()), e);
                writeError(out, job, "db_error", "query_failed");
                if (!abortIfCancelled(jobUuid, out)) {
                    finishJob(job, "export_failed", out);
                }
            }
        } catch (Exception e) {
            log.error(workerMessages.get("worker.export_replay.handle_failed"), e);
        }
    }

    private void sleepDebugDelayIfConfigured(String jobId) {
        if (debugDelayMs <= 0) {
            return;
        }
        log.info(workerMessages.format("worker.export_replay.debug_delay", jobId, debugDelayMs));
        try {
            Thread.sleep(debugDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(workerMessages.format("worker.export_replay.debug_delay_interrupted", jobId));
        }
    }

    public void onCancelHint(io.nats.client.Message msg) {
        ExportReplayMetrics.cancelHint();
        try {
            var event = MAPPER.readValue(msg.getData(), ExportReplayCancelEvent.class);
            log.debug(workerMessages.format("worker.export_replay.cancel_hint", event.jobId(), event.chatId()));
        } catch (Exception e) {
            log.trace("Ignoring invalid export cancel hint: {}", e.getMessage());
        }
    }

    private void throwIfCancelled(UUID jobUuid) {
        if (jobUuid != null && jobStore != null && jobStore.isCancelled(jobUuid)) {
            throw new ExportCancelledException(jobUuid);
        }
    }

    private void throwIfCancelledEvery(UUID jobUuid, int rowIndex) {
        if (cancelCheckEveryRows <= 0 || rowIndex <= 0 || rowIndex % cancelCheckEveryRows != 0) {
            return;
        }
        throwIfCancelled(jobUuid);
    }

    private boolean abortIfCancelled(UUID jobUuid, Path... artifacts) {
        if (jobStore == null || jobUuid == null || !jobStore.isCancelled(jobUuid)) {
            return false;
        }
        for (var path : artifacts) {
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup of partial export
                }
            }
        }
        log.info(workerMessages.format("worker.export_replay.job_aborted", jobUuid));
        ExportReplayMetrics.jobCancelled();
        return true;
    }

    private void finishJob(ExportReplayJob job, String status, Path out) {
        var pathStr = resolveStoredOutputPath(job, out);
        var jobUuid = parseJobId(job.jobId());
        var requester = parseUserId(job.requestedBy());
        if (jobStore != null && jobUuid != null) {
            if (jobStore.isCancelled(jobUuid)) {
                log.info(workerMessages.format("worker.export_replay.job_cancelled", job.jobId(), status));
                return;
            }
            jobStore.markTerminal(jobUuid, status, pathStr, messageTtlFilterApplied);
        }
        if (auditWriter != null && requester != null) {
            auditWriter.recordCompleted(requester, job.jobId(), job.chatId(), status, pathStr);
        }
        ExportReplayMetrics.jobCompleted(status);
        publishDoneIfEnabled(job, pathStr, status);
    }

    private String resolveStoredOutputPath(ExportReplayJob job, Path out) {
        if (out == null) {
            return null;
        }
        if (minioUploader != null && job.jobId() != null && !job.jobId().isBlank()) {
            try {
                var zipArtifact = out.getFileName() != null
                    && out.getFileName().toString().endsWith(".export.zip");
                var key = zipArtifact
                    ? ExportOutputRef.zipObjectKey(job.jobId())
                    : ExportOutputRef.objectKey(job.jobId());
                minioUploader.upload(out, key);
                return ExportOutputRef.minioStoredPath(key);
            } catch (Exception e) {
                log.warn(workerMessages.format("worker.export_replay.minio_upload_failed", job.jobId(), e.getMessage()));
            }
        }
        return out.toAbsolutePath().toString();
    }

    private static UUID parseJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(jobId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void writeStub(Path out, ExportReplayJob job) throws Exception {
        var stub = MAPPER.createObjectNode()
            .put("jobId", job.jobId())
            .put("chatId", job.chatId())
            .put("requestedBy", job.requestedBy() != null ? job.requestedBy() : "")
            .put("stubStatus", "pending_implementation")
            .put("writtenAtEpochMs", Instant.now().toEpochMilli());
        Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(stub), StandardCharsets.UTF_8);
    }

    private void writeError(Path out, ExportReplayJob job, String code, String detail) throws Exception {
        var err = MAPPER.createObjectNode()
            .put("jobId", job.jobId())
            .put("chatId", job.chatId())
            .put("requestedBy", job.requestedBy() != null ? job.requestedBy() : "")
            .put("formatVersion", 1)
            .put("exportStatus", "failed")
            .put("errorCode", code)
            .put("errorDetail", detail != null ? detail : "")
            .put("writtenAtEpochMs", Instant.now().toEpochMilli());
        Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(err), StandardCharsets.UTF_8);
    }

    private ObjectNode exportFromDatabase(ExportReplayJob job, UUID jobUuid) throws SQLException {
        throwIfCancelled(jobUuid);
        var chatId = UUID.fromString(job.chatId().trim());
        var root = MAPPER.createObjectNode();
        root.put("formatVersion", 1);
        root.put("jobId", job.jobId());
        root.put("chatId", job.chatId());
        root.put("requestedBy", job.requestedBy() != null ? job.requestedBy() : "");
        root.put("exportedAtEpochMs", Instant.now().toEpochMilli());
        root.put("exportStatus", "ok");
        root.putNull("stubStatus");
        root.put("messageTtlFilterApplied", messageTtlFilterApplied);

        if (includeRetentionPolicy) {
            root.set("retentionPolicy", ExportRetentionPolicyLoader.loadEffectivePolicy(
                dataSource, chatId, platformDefaults, jdbcQueryTimeoutSeconds));
        }

        var referencedFileIds = new LinkedHashSet<UUID>();
        var referencedFileIdsTruncated = new boolean[] {false};
        var chatAvatarFileIds = new LinkedHashSet<UUID>();
        var deepArchiveFileIds = new LinkedHashSet<UUID>();

        if (includeChat) {
            root.put("includeChat", true);
            try (var jdbc = dataSource.getConnection();
                 PreparedStatement ps = jdbc.prepareStatement(SQL_CHAT)) {
                if (jdbcQueryTimeoutSeconds > 0) {
                    ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                }
                ps.setObject(1, chatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (includeReferencedFiles) {
                            var avatarId = rs.getString("avatar_file_id");
                            if (!rs.wasNull() && avatarId != null && !avatarId.isBlank()) {
                                var avatarUuid = parseUuidLenient(avatarId.trim());
                                if (avatarUuid != null) {
                                    chatAvatarFileIds.add(avatarUuid);
                                }
                                if (ExportMessageLoader.tryAddUuidString(avatarId.trim(), referencedFileIds, maxFileIdsFromContent)) {
                                    referencedFileIdsTruncated[0] = true;
                                }
                            }
                        }
                        root.set("chat", chatRowToNode(rs));
                    } else {
                        root.putNull("chat");
                        root.put("chatMissing", true);
                        log.warn(workerMessages.format("worker.export_replay.no_chats_row", job.chatId(), job.jobId()));
                    }
                }
            }
        } else {
            root.put("includeChat", false);
        }

        if (includeChatMembers) {
            root.put("includeChatMembers", true);
            var members = MAPPER.createArrayNode();
            try (var jdbc = dataSource.getConnection();
                 PreparedStatement ps = jdbc.prepareStatement(SQL_CHAT_MEMBERS)) {
                if (jdbcQueryTimeoutSeconds > 0) {
                    ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                }
                ps.setObject(1, chatId);
                ps.setInt(2, maxChatMemberRows);
                try (ResultSet rs = ps.executeQuery()) {
                    int memberRow = 0;
                    while (rs.next()) {
                        throwIfCancelledEvery(jobUuid, ++memberRow);
                        members.add(chatMemberRowToNode(rs));
                    }
                }
            }
            root.set("chatMembers", members);
            root.put("chatMemberCount", members.size());
            root.put("maxExportChatMembers", maxChatMemberRows);
            if (members.size() >= maxChatMemberRows) {
                root.put("chatMembersTruncated", true);
            }
        } else {
            root.put("includeChatMembers", false);
        }

        var messages = MAPPER.createArrayNode();
        var messageTypesById = new HashMap<String, String>();
        int e2eeMessageCount = 0;
        int nonE2eeMessageCount = 0;
        try (var jdbc = dataSource.getConnection();
             PreparedStatement ps = jdbc.prepareStatement(sqlMessages)) {
            if (jdbcQueryTimeoutSeconds > 0) {
                ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
            }
            ps.setObject(1, chatId);
            ps.setInt(2, maxExportMessages);
            try (ResultSet rs = ps.executeQuery()) {
                int messageRow = 0;
                while (rs.next()) {
                    throwIfCancelledEvery(jobUuid, ++messageRow);
                    var type = rs.getString("type");
                    if (ExportMessageLoader.isE2eeEnvelopeType(type)) {
                        e2eeMessageCount++;
                    } else {
                        nonE2eeMessageCount++;
                    }
                    if (includeReferencedFiles) {
                        if (!ExportMessageLoader.isE2eeEnvelopeType(type)) {
                            var content = rs.getString("content");
                            if (ExportMessageLoader.collectFileIdsFromText(content, referencedFileIds, maxFileIdsFromContent)) {
                                referencedFileIdsTruncated[0] = true;
                            }
                        }
                    }
                    var row = rowToMessageNode(rs);
                    messages.add(row);
                    messageTypesById.put(row.get("id").asText(), row.get("type").asText("text"));
                }
            }
        }
        throwIfCancelled(jobUuid);
        root.set("messages", messages);
        root.put("messageCount", messages.size());
        root.put("maxExportMessages", maxExportMessages);
        if (messages.size() >= maxExportMessages) {
            root.put("truncated", true);
        }
        root.put("e2eeMessageCount", e2eeMessageCount);
        root.put("nonE2eeMessageCount", nonE2eeMessageCount);

        if (includeVersions) {
            root.put("includeMessageVersions", true);
            var versions = MAPPER.createArrayNode();
            try (var jdbc = dataSource.getConnection();
                 PreparedStatement ps = jdbc.prepareStatement(sqlMessageVersions)) {
                if (jdbcQueryTimeoutSeconds > 0) {
                    ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                }
                ps.setObject(1, chatId);
                ps.setInt(2, maxExportMessages);
                ps.setInt(3, maxVersionRows);
                try (ResultSet rs = ps.executeQuery()) {
                    int versionRow = 0;
                    while (rs.next()) {
                        throwIfCancelledEvery(jobUuid, ++versionRow);
                        if (includeReferencedFiles) {
                            var messageId = rs.getString("message_id");
                            var parentType = messageTypesById.getOrDefault(messageId, "text");
                            if (!ExportMessageLoader.isE2eeEnvelopeType(parentType)) {
                                var vcontent = rs.getString("content");
                                if (ExportMessageLoader.collectFileIdsFromText(vcontent, referencedFileIds, maxFileIdsFromContent)) {
                                    referencedFileIdsTruncated[0] = true;
                                }
                            }
                        }
                        versions.add(versionRowToNode(rs, messageTypesById));
                    }
                }
            }
            root.set("messageVersions", versions);
            root.put("messageVersionCount", versions.size());
            root.put("maxExportMessageVersions", maxVersionRows);
            if (versions.size() >= maxVersionRows) {
                root.put("versionsTruncated", true);
            }
        } else {
            root.put("includeMessageVersions", false);
        }

        if (includeReactions) {
            root.put("includeReactions", true);
            var reactions = MAPPER.createArrayNode();
            try (var jdbc = dataSource.getConnection();
                 PreparedStatement ps = jdbc.prepareStatement(sqlMessageReactions)) {
                if (jdbcQueryTimeoutSeconds > 0) {
                    ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                }
                ps.setObject(1, chatId);
                ps.setInt(2, maxExportMessages);
                ps.setInt(3, maxReactionRows);
                try (ResultSet rs = ps.executeQuery()) {
                    int reactionRow = 0;
                    while (rs.next()) {
                        throwIfCancelledEvery(jobUuid, ++reactionRow);
                        reactions.add(reactionRowToNode(rs));
                    }
                }
            }
            root.set("reactions", reactions);
            root.put("reactionCount", reactions.size());
            root.put("maxExportReactions", maxReactionRows);
            if (reactions.size() >= maxReactionRows) {
                root.put("reactionsTruncated", true);
            }
        } else {
            root.put("includeReactions", false);
        }

        if (includePins) {
            root.put("includePinnedMessages", true);
            var pinned = MAPPER.createArrayNode();
            try (var jdbc = dataSource.getConnection();
                 PreparedStatement ps = jdbc.prepareStatement(sqlPinnedMessages)) {
                if (jdbcQueryTimeoutSeconds > 0) {
                    ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                }
                ps.setObject(1, chatId);
                ps.setObject(2, chatId);
                ps.setInt(3, maxExportMessages);
                ps.setInt(4, maxPinnedRows);
                try (ResultSet rs = ps.executeQuery()) {
                    int pinnedRow = 0;
                    while (rs.next()) {
                        throwIfCancelledEvery(jobUuid, ++pinnedRow);
                        pinned.add(pinnedRowToNode(rs));
                    }
                }
            }
            root.set("pinnedMessages", pinned);
            root.put("pinnedCount", pinned.size());
            root.put("maxExportPinnedMessages", maxPinnedRows);
            if (pinned.size() >= maxPinnedRows) {
                root.put("pinnedTruncated", true);
            }
        } else {
            root.put("includePinnedMessages", false);
        }

        var deepArchiveStats = attachDeepArchiveSnapshots(
            root, messages, referencedFileIds, referencedFileIdsTruncated, deepArchiveFileIds, maxFileIdsFromContent);

        if (includeReferencedFiles) {
            root.put("includeReferencedFiles", true);
            if (referencedFileIdsTruncated[0]) {
                root.put("referencedFileIdsTruncated", true);
            }
            var queryIds = new ArrayList<>(referencedFileIds);
            if (queryIds.size() > maxReferencedFileRows) {
                queryIds = new ArrayList<>(queryIds.subList(0, maxReferencedFileRows));
                root.put("referencedFilesTruncated", true);
            }
            var files = MAPPER.createArrayNode();
            if (!queryIds.isEmpty()) {
                try (var jdbc = dataSource.getConnection();
                     PreparedStatement ps = jdbc.prepareStatement(SQL_REFERENCED_FILES)) {
                    if (jdbcQueryTimeoutSeconds > 0) {
                        ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                    }
                    var sqlArray = jdbc.createArrayOf("uuid", queryIds.toArray(new UUID[0]));
                    try {
                        ps.setArray(1, sqlArray);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                var fileNode = referencedFileRowToNode(rs);
                                tagReferencedFileSource(fileNode, chatAvatarFileIds, deepArchiveFileIds);
                                files.add(fileNode);
                            }
                        }
                    } finally {
                        sqlArray.free();
                    }
                }
            }
            root.set("referencedFiles", files);
            root.put("referencedFileCount", files.size());
            root.put("maxReferencedFiles", maxReferencedFileRows);
            root.put("maxFileIdsFromContent", maxFileIdsFromContent);
            if (!chatAvatarFileIds.isEmpty()) {
                root.put("chatAvatarReferenced", true);
            }
        } else {
            root.put("includeReferencedFiles", false);
        }

        var e2eeCandidateStats = attachE2eeFileCandidates(root, chatId, referencedFileIds, e2eeMessageCount);

        if (includeReferencedUsers) {
            root.put("includeReferencedUsers", true);
            var mbrLimitForUsers = includeChatMembers ? maxChatMemberRows : 0;
            var users = MAPPER.createArrayNode();
            var fileIdArr = referencedFileIds.isEmpty() ? new UUID[0] : referencedFileIds.toArray(new UUID[0]);
            try (var jdbc = dataSource.getConnection();
                 PreparedStatement ps = jdbc.prepareStatement(sqlReferencedUsers)) {
                if (jdbcQueryTimeoutSeconds > 0) {
                    ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
                }
                ps.setObject(1, chatId);
                ps.setInt(2, maxExportMessages);
                ps.setObject(3, chatId);
                ps.setInt(4, mbrLimitForUsers);
                var sqlArray = jdbc.createArrayOf("uuid", fileIdArr);
                try {
                    ps.setArray(5, sqlArray);
                    ps.setObject(6, chatId);
                    ps.setObject(7, chatId);
                    ps.setInt(8, maxReferencedUserRows);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            users.add(referencedUserRowToNode(rs));
                        }
                    }
                } finally {
                    sqlArray.free();
                }
            }
            root.set("referencedUsers", users);
            root.put("referencedUserCount", users.size());
            root.put("maxReferencedUsers", maxReferencedUserRows);
            if (users.size() >= maxReferencedUserRows) {
                root.put("referencedUsersTruncated", true);
            }
        } else {
            root.put("includeReferencedUsers", false);
        }

        var retentionStats = attachRetentionSnapshots(root, messages);
        var solrStats = attachSolrIndex(root, chatId);
        if (includeExportCompleteness) {
            root.set("exportCompleteness", ExportRetentionPolicyLoader.buildExportCompleteness(
                messageTtlFilterApplied,
                includeVersions,
                includeReactions,
                includePins,
                includeReferencedFiles,
                deepArchiveStats.requested(),
                deepArchiveStats.scanned(),
                deepArchiveStats.found(),
                !deepArchiveFileIds.isEmpty(),
                retentionStats.requested(),
                retentionStats.scanned(),
                retentionStats.found(),
                solrStats.requested(),
                solrStats.numFound(),
                solrStats.exported(),
                e2eeMessageCount,
                nonE2eeMessageCount,
                includeFileBodies && fileBodyFetcher != null && includeReferencedFiles,
                e2eeCandidateStats.included(),
                e2eeCandidateStats.count()));
        }
        throwIfCancelled(jobUuid);
        return root;
    }

    private record E2eeFileCandidateStats(boolean included, int count) {}

    private E2eeFileCandidateStats attachE2eeFileCandidates(
        ObjectNode root,
        UUID chatId,
        Set<UUID> referencedFileIds,
        int e2eeMessageCount
    ) throws SQLException {
        if (e2eeMessageCount <= 0 || !includeReferencedFiles) {
            root.put("includeE2eeFileCandidates", false);
            return new E2eeFileCandidateStats(false, 0);
        }
        var enabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_E2EE_FILE_CANDIDATES", "false"));
        var maxRows = Math.max(1, Integer.parseInt(
            System.getenv().getOrDefault("EXPORT_REPLAY_MAX_E2EE_FILE_CANDIDATES", "64")));
        if (!enabled) {
            root.put("includeE2eeFileCandidates", false);
            return new E2eeFileCandidateStats(false, 0);
        }
        root.put("includeE2eeFileCandidates", true);
        root.put("e2eeFileCandidatesHeuristic", "uploaded_by_chat_member");
        root.put("maxE2eeFileCandidates", maxRows);
        var candidates = MAPPER.createArrayNode();
        var exclude = referencedFileIds.isEmpty() ? new UUID[0] : referencedFileIds.toArray(new UUID[0]);
        try (var jdbc = dataSource.getConnection();
             PreparedStatement ps = jdbc.prepareStatement(SQL_E2EE_FILE_CANDIDATES)) {
            if (jdbcQueryTimeoutSeconds > 0) {
                ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
            }
            ps.setObject(1, chatId);
            var sqlArray = jdbc.createArrayOf("uuid", exclude);
            try {
                ps.setArray(2, sqlArray);
                ps.setInt(3, maxRows);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        var fileNode = referencedFileRowToNode(rs);
                        fileNode.put("exportFileSource", "e2ee_file_candidate");
                        fileNode.put(
                            "candidateNote",
                            "Heuristic metadata only; not linked from E2EE message ciphertext.");
                        candidates.add(fileNode);
                    }
                }
            } finally {
                sqlArray.free();
            }
        }
        root.set("e2eeFileCandidates", candidates);
        root.put("e2eeFileCandidateCount", candidates.size());
        if (candidates.size() >= maxRows) {
            root.put("e2eeFileCandidatesTruncated", true);
        }
        return new E2eeFileCandidateStats(true, candidates.size());
    }

    private static UUID parseUuidLenient(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void tagReferencedFileSource(
        ObjectNode fileNode, Set<UUID> chatAvatarFileIds, Set<UUID> deepArchiveFileIds) {
        if (fileNode == null) {
            return;
        }
        var idText = fileNode.path("id").asText(null);
        if (idText == null) {
            return;
        }
        var id = parseUuidLenient(idText);
        if (id == null) {
            return;
        }
        if (!chatAvatarFileIds.isEmpty() && chatAvatarFileIds.contains(id)) {
            fileNode.put("exportFileSource", "chat_avatar");
        } else if (!deepArchiveFileIds.isEmpty() && deepArchiveFileIds.contains(id)) {
            fileNode.put("exportFileSource", "deep_archive");
        }
    }

    private record SolrAttachStats(boolean requested, int numFound, int exported) {
    }

    private SolrAttachStats attachSolrIndex(ObjectNode root, UUID chatId) {
        if (!includeSolrIndex || solrReader == null) {
            root.put("includeSolrIndex", false);
            return new SolrAttachStats(false, 0, 0);
        }
        try {
            root.put("includeSolrIndex", true);
            var result = solrReader.fetchChatIndex(chatId, maxSolrDocs);
            root.set("solrIndex", result.documents());
            root.put("solrIndexNumFound", result.numFound());
            root.put("solrIndexExported", result.exported());
            root.put("maxSolrDocs", maxSolrDocs);
            if (result.truncated()) {
                root.put("solrIndexTruncated", true);
            }
            return new SolrAttachStats(true, result.numFound(), result.exported());
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.export_replay.solr_failed", chatId, e.getMessage()));
            root.put("includeSolrIndex", false);
            root.put("solrIndexError", e.getMessage());
            return new SolrAttachStats(true, 0, 0);
        }
    }

    private record MinioAttachStats(boolean requested, int scanned, int found) {
    }

    private MinioAttachStats attachDeepArchiveSnapshots(
        ObjectNode root,
        com.fasterxml.jackson.databind.node.ArrayNode messages,
        Set<UUID> referencedFileIds,
        boolean[] referencedFileIdsTruncated,
        Set<UUID> deepArchiveFileIds,
        int maxFileIdsFromContent) {
        if (!includeDeepArchiveSnapshots || deepArchiveReader == null) {
            root.put("includeDeepArchiveSnapshots", false);
            return new MinioAttachStats(false, 0, 0);
        }
        root.put("includeDeepArchiveSnapshots", true);
        root.put("deepArchiveBucket", deepArchiveReader.bucket());
        var snapshots = MAPPER.createArrayNode();
        int scanned = 0;
        int found = 0;
        for (var msg : messages) {
            if (scanned >= maxDeepArchiveSnapshots) {
                root.put("deepArchiveSnapshotsTruncated", true);
                break;
            }
            scanned++;
            var messageId = msg.get("id").asText();
            var snap = deepArchiveReader.fetchMessageSnapshot(messageId);
            if (snap.isPresent()) {
                snapshots.add(snap.get());
                found++;
                if (includeReferencedFiles) {
                    mergeSnapshotFileIds(
                        snap.get().get("snapshot"), referencedFileIds, referencedFileIdsTruncated,
                        deepArchiveFileIds, maxFileIdsFromContent);
                }
            }
        }
        root.set("deepArchiveSnapshots", snapshots);
        root.put("deepArchiveSnapshotCount", found);
        root.put("deepArchiveMessagesScanned", scanned);
        root.put("maxDeepArchiveSnapshots", maxDeepArchiveSnapshots);
        if (includeReferencedFiles && !deepArchiveFileIds.isEmpty()) {
            root.put("deepArchiveReferencedFileIds", true);
        }
        return new MinioAttachStats(true, scanned, found);
    }

    private static void mergeSnapshotFileIds(
        com.fasterxml.jackson.databind.JsonNode snapshotNode,
        Set<UUID> referencedFileIds,
        boolean[] referencedFileIdsTruncated,
        Set<UUID> deepArchiveFileIds,
        int maxFileIdsFromContent) {
        if (snapshotNode == null || snapshotNode.isNull()) {
            return;
        }
        var fromSnap = new LinkedHashSet<UUID>();
        if (ExportSnapshotFileIdCollector.collectFromJson(snapshotNode, fromSnap, maxFileIdsFromContent)) {
            referencedFileIdsTruncated[0] = true;
        }
        deepArchiveFileIds.addAll(fromSnap);
        for (var id : fromSnap) {
            if (referencedFileIds.size() >= maxFileIdsFromContent && !referencedFileIds.contains(id)) {
                referencedFileIdsTruncated[0] = true;
            } else {
                referencedFileIds.add(id);
            }
        }
    }

    private MinioAttachStats attachRetentionSnapshots(ObjectNode root, com.fasterxml.jackson.databind.node.ArrayNode messages)
        throws SQLException {
        if (!includeRetentionSnapshots || retentionSnapshotReader == null || dataSource == null) {
            root.put("includeRetentionSnapshots", false);
            return new MinioAttachStats(false, 0, 0);
        }
        root.put("includeRetentionSnapshots", true);
        root.put("retentionSnapshotBucket", retentionSnapshotReader.bucket());
        root.put("retentionObjectPrefix", retentionSnapshotReader.objectPrefix());
        var result = retentionSnapshotReader.attachSnapshots(
            dataSource, messages, maxRetentionSnapshots, jdbcQueryTimeoutSeconds);
        root.set("retentionSnapshots", result.snapshots());
        root.put("retentionSnapshotCount", result.snapshotsFound());
        root.put("retentionMessagesScanned", result.messagesScanned());
        root.put("maxRetentionSnapshots", maxRetentionSnapshots);
        if (result.truncated()) {
            root.put("retentionSnapshotsTruncated", true);
        }
        return new MinioAttachStats(true, result.messagesScanned(), result.snapshotsFound());
    }

    private boolean applyCompletenessValidation(ObjectNode root, ExportReplayJob job) {
        if (!includeExportCompleteness) {
            return true;
        }
        var completeness = root.get("exportCompleteness");
        if (completeness == null || !completeness.isObject()) {
            return true;
        }
        var node = (ObjectNode) completeness;
        var required = ExportCompletenessConfig.requiredFieldsFromEnv(
            System.getenv("EXPORT_REQUIRED_FIELDS"));
        var strict = ExportCompletenessConfig.strictFromEnv(
            System.getenv("EXPORT_COMPLETENESS_STRICT"));
        long start = System.nanoTime();
        var built = ExportCompletenessValidator.validateAndBuild(node, root, required, strict);
        built.writeTo(node);
        ExportReplayMetrics.completenessChecked();
        if (!built.complete()) {
            ExportReplayMetrics.completenessFailed("mandatory_fields");
            log.warn(workerMessages.format("worker.export_replay.completeness_failed", job.jobId(), strict));
            return !strict;
        }
        ExportReplayMetrics.observeCompletenessDuration(start);
        return true;
    }

    private static int intOrZero(ObjectNode root, String field) {
        var n = root.get(field);
        return n != null && n.isNumber() ? n.asInt() : 0;
    }

    private static ObjectNode rowToMessageNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("id", rs.getString("id"));
        n.put("senderId", rs.getString("sender_id"));
        var clientMsgId = rs.getString("client_msg_id");
        if (!rs.wasNull() && clientMsgId != null) {
            n.put("clientMsgId", clientMsgId);
        }
        var type = rs.getString("type");
        n.put("type", type != null ? type : "text");
        if (ExportMessageLoader.isE2eeEnvelopeType(type)) {
            n.putNull("content");
            n.put("contentOmitted", true);
        } else {
            var content = rs.getString("content");
            if (rs.wasNull() || content == null) {
                n.putNull("content");
            } else {
                n.put("content", content);
            }
        }
        var reply = rs.getString("reply_to_msg_id");
        if (!rs.wasNull() && reply != null) {
            n.put("replyToMessageId", reply);
        }
        var threadId = rs.getString("thread_id");
        if (!rs.wasNull() && threadId != null) {
            n.put("threadId", threadId);
        }
        n.put("deleted", rs.getBoolean("deleted"));
        var ttl = rs.getObject("visibility_ttl_seconds");
        if (ttl != null) {
            n.put("visibilityTtlSeconds", ((Number) ttl).intValue());
        }
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        putInstant(n, "editedAt", rs.getTimestamp("edited_at"));
        return n;
    }

    private static void putInstant(ObjectNode n, String field, Timestamp ts) {
        if (ts == null) {
            n.putNull(field);
            return;
        }
        n.put(field, ts.toInstant().toString());
    }

    private static ObjectNode versionRowToNode(ResultSet rs, Map<String, String> messageTypesById) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("id", Long.toString(rs.getLong("id")));
        var messageId = rs.getString("message_id");
        n.put("messageId", messageId);
        n.put("editedBy", rs.getString("edited_by"));
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        var parentType = messageTypesById.getOrDefault(messageId, "text");
        if (ExportMessageLoader.isE2eeEnvelopeType(parentType)) {
            n.putNull("content");
            n.put("contentOmitted", true);
        } else {
            var content = rs.getString("content");
            if (rs.wasNull() || content == null) {
                n.putNull("content");
            } else {
                n.put("content", content);
            }
        }
        return n;
    }

    private static ObjectNode reactionRowToNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("messageId", rs.getString("message_id"));
        n.put("userId", rs.getString("user_id"));
        n.put("reaction", rs.getString("reaction"));
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        return n;
    }

    private static ObjectNode pinnedRowToNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("messageId", rs.getString("message_id"));
        n.put("pinnedBy", rs.getString("pinned_by"));
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        return n;
    }

    private static ObjectNode chatRowToNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("id", rs.getString("id"));
        n.put("title", rs.getString("title"));
        var type = rs.getString("type");
        n.put("type", type != null ? type : "p2p");
        var owner = rs.getObject("owner_id", UUID.class);
        if (owner == null) {
            n.putNull("ownerId");
        } else {
            n.put("ownerId", owner.toString());
        }
        var avatar = rs.getString("avatar_file_id");
        if (rs.wasNull() || avatar == null || avatar.isEmpty()) {
            n.putNull("avatarFileId");
        } else {
            n.put("avatarFileId", avatar);
        }
        n.put("hidden", rs.getBoolean("hidden"));
        n.put("muted", rs.getBoolean("muted"));
        var ttl = rs.getObject("ttl_seconds");
        if (ttl != null) {
            n.put("ttlSeconds", ((Number) ttl).intValue());
        }
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        putInstant(n, "updatedAt", rs.getTimestamp("updated_at"));
        return n;
    }

    private static ObjectNode chatMemberRowToNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("userId", rs.getString("user_id"));
        var role = rs.getString("role");
        n.put("role", role != null ? role : "member");
        putInstant(n, "joinedAt", rs.getTimestamp("joined_at"));
        n.put("muted", rs.getBoolean("muted"));
        n.put("banned", rs.getBoolean("banned"));
        n.put("personalFilterActive", rs.getBoolean("personal_filter_active"));
        return n;
    }

    /** Public fields only вЂ” no email, phone, or password hashes. */
    private static ObjectNode referencedUserRowToNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("id", rs.getString("id"));
        n.put("username", rs.getString("username"));
        n.put("displayName", rs.getString("display_name"));
        n.put("hidden", rs.getBoolean("hidden"));
        var orgId = rs.getObject("org_id", UUID.class);
        if (orgId == null) {
            n.putNull("orgId");
        } else {
            n.put("orgId", orgId.toString());
        }
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        putInstant(n, "updatedAt", rs.getTimestamp("updated_at"));
        return n;
    }

    private static ObjectNode referencedFileRowToNode(ResultSet rs) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("id", rs.getString("id"));
        n.put("filename", rs.getString("filename"));
        n.put("mimeType", rs.getString("mime_type"));
        n.put("size", rs.getLong("size"));
        n.put("uploadedBy", rs.getString("uploaded_by"));
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        return n;
    }

    private void publishDoneIfEnabled(ExportReplayJob job, String outputPath, String status) {
        if (!publishComplete) {
            return;
        }
        try {
            var done = new ExportReplayCompleteEvent(
                job.jobId(),
                job.chatId(),
                status,
                outputPath,
                messageTtlFilterApplied
            );
            natsConsumer.publish(NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, MAPPER.writeValueAsBytes(done));
            log.debug(workerMessages.format("worker.export_replay.published_complete", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, status));
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.common.publish_failed_simple", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE), e);
        }
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            var v = Integer.parseInt(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseNonNegativeLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            var v = Long.parseLong(raw.trim());
            return Math.max(0L, v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
