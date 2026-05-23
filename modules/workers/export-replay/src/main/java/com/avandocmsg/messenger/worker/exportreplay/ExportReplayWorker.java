package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.dto.ExportReplayCancelEvent;
import com.avandocmsg.messenger.common.dto.ExportReplayCompleteEvent;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.prometheus.client.hotspot.DefaultExports;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Export / compliance replay: consumes JSON {@link ExportReplayJob} on {@link NatsSubjects#MSG_EXPORT_REPLAY},
 * writes JSON under {@code EXPORT_DIR}. When a JDBC {@link DataSource} is configured ({@code DB_JDBC_URL} in {@link #main}),
 * loads message rows from the hot PostgreSQL {@code messages} table (cap {@code EXPORT_REPLAY_MAX_MESSAGES}),
 * optionally {@code message_versions} for those messages (cap {@code EXPORT_REPLAY_MAX_MESSAGE_VERSIONS}, toggle
 * {@code EXPORT_REPLAY_INCLUDE_VERSIONS}); optionally {@code message_reactions} / {@code pinned_messages} (same message subset;
 * {@code EXPORT_REPLAY_INCLUDE_REACTIONS}, {@code EXPORT_REPLAY_MAX_REACTION_ROWS}, {@code EXPORT_REPLAY_INCLUDE_PINS},
 * {@code EXPORT_REPLAY_MAX_PINNED_ROWS}); optionally one {@code chats} row and {@code chat_members} rows
 * ({@code EXPORT_REPLAY_INCLUDE_CHAT}, {@code EXPORT_REPLAY_INCLUDE_CHAT_MEMBERS}, {@code EXPORT_REPLAY_MAX_CHAT_MEMBERS});
 * optionally {@code users} rows referenced by the export (senders, members cap, editors, reactors, pinners, chat owner,
 * and {@code file_metadata.uploaded_by} for collected file UUIDs;
 * {@code EXPORT_REPLAY_INCLUDE_REFERENCED_USERS}, {@code EXPORT_REPLAY_MAX_REFERENCED_USERS}) — без email/телефона/пароля;
 * optionally {@code file_metadata} for UUIDs found in non-E2EE message/version text and chat {@code avatar_file_id}
 * ({@code EXPORT_REPLAY_INCLUDE_REFERENCED_FILES}, {@code EXPORT_REPLAY_MAX_FILE_IDS_FROM_CONTENT}, {@code EXPORT_REPLAY_MAX_REFERENCED_FILES});
 * message TTL visibility matches API list when {@code EXPORT_REPLAY_APPLY_MESSAGE_TTL_FILTER=true} (default);
 * optional MinIO upload ({@code EXPORT_REPLAY_MINIO_UPLOAD}) stores {@code minio:exports/…} in {@code output_path};
 * {@code retentionPolicy} / {@code exportCompleteness} for compliance vs retention (env {@code EXPORT_REPLAY_INCLUDE_RETENTION_POLICY},
 * {@code EXPORT_REPLAY_INCLUDE_EXPORT_COMPLETENESS}).
 * Optional deep-archive MinIO snapshots ({@code EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE}, cap {@code EXPORT_REPLAY_MAX_DEEP_ARCHIVE_SNAPSHOTS}).
 * Optional retention hot-body snapshots ({@code EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS}, {@code retention_hot_body_applied}).
 * Optional Solr index dump ({@code EXPORT_REPLAY_INCLUDE_SOLR_INDEX}, {@code SOLR_URL} / {@code SOLR_ZK}).
 * Otherwise writes a stub marker. Optionally publishes {@link NatsSubjects#MSG_EXPORT_REPLAY_COMPLETE}.
 * Cooperative cancel: polls {@code export_jobs} during export ({@code EXPORT_REPLAY_CANCEL_CHECK_EVERY_ROWS});
 * listens to {@link NatsSubjects#MSG_EXPORT_REPLAY_CANCEL} as a hint.
 * Dev/smoke: {@code EXPORT_REPLAY_DEBUG_DELAY_MS} sleeps after {@code processing} (keeps job cancellable longer).
 */
public class ExportReplayWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportReplayWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "export-replay-workers";

    private static final String SQL_REFERENCED_FILES = """
        SELECT id, filename, mime_type, size, uploaded_by, created_at
        FROM file_metadata
        WHERE id = ANY(?::uuid[])
        ORDER BY created_at ASC
        """;

    /** Heuristic: files uploaded by chat members, excluding IDs already in {@code referencedFiles}. */
    private static final String SQL_E2EE_FILE_CANDIDATES = """
        SELECT fm.id, fm.filename, fm.mime_type, fm.size, fm.uploaded_by, fm.created_at
        FROM file_metadata fm
        WHERE fm.uploaded_by IN (
            SELECT cm.user_id FROM chat_members cm WHERE cm.chat_id = ?::uuid
        )
        AND NOT (fm.id = ANY(?::uuid[]))
        ORDER BY fm.created_at ASC
        LIMIT ?
        """;

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

    private final Connection connection;
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

    public ExportReplayWorker(
        String natsUrl,
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
        ExportFileBodyFetcher fileBodyFetcher
    ) throws Exception {
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
        this.jobStore = dataSource != null ? new ExportJobStore(dataSource) : null;
        this.auditWriter = dataSource != null ? new ExportAuditWriter(dataSource) : null;
        this.minioUploader = minioUploader;
        this.includeFileBodies = includeFileBodies;
        this.maxFileBodies = maxFileBodies > 0 ? maxFileBodies : 500;
        this.maxFileBodyBytes = maxFileBodyBytes > 0 ? maxFileBodyBytes : 52_428_800L;
        this.fileBodyFetcher = fileBodyFetcher;
        this.cancelCheckEveryRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_CANCEL_CHECK_EVERY_ROWS"), 500);
        this.debugDelayMs = parseNonNegativeLong(System.getenv("EXPORT_REPLAY_DEBUG_DELAY_MS"), 0L);
        if (this.debugDelayMs > 0) {
            log.warn("EXPORT_REPLAY_DEBUG_DELAY_MS={} — dev/smoke only; export will pause after processing", this.debugDelayMs);
        }
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("export-replay-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info("Connected to NATS at {}", natsUrl);
    }

    public void start() throws Exception {
        Files.createDirectories(exportDir);
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_REPLAY, QUEUE_GROUP);
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, QUEUE_GROUP, this::onCancelHint);
        log.info(
            "Subscribed to {} (queue: {}) exportDir={} dbExport={} maxMessages={} includeVersions={} maxVersionRows={} "
                + "includeReactions={} maxReactionRows={} includePins={} maxPinnedRows={} includeChat={} includeChatMembers={} "
                + "maxChatMemberRows={} includeReferencedUsers={} maxReferencedUserRows={} includeReferencedFiles={} "
                + "maxFileIdsFromContent={} maxReferencedFileRows={} messageTtlFilter={}",
            NatsSubjects.MSG_EXPORT_REPLAY,
            QUEUE_GROUP,
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
        );
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var job = MAPPER.readValue(payload, ExportReplayJob.class);
            if (job.jobId() == null || job.jobId().isBlank() || job.chatId() == null || job.chatId().isBlank()) {
                log.warn("Invalid export job payload: {}", payload);
                ExportReplayMetrics.jobSkipped("invalid_payload");
                return;
            }
            var safeJobId = ExportOutputRef.safeJobIdForFilename(job.jobId());
            var out = exportDir.resolve(safeJobId + ".export.json");

            if (dataSource == null) {
                writeStub(out, job);
                log.info("Export replay stub written jobId={} path={}", job.jobId(), out.toAbsolutePath());
                finishJob(job, "stub_written", out);
                return;
            }

            var jobUuid = parseJobId(job.jobId());
            if (jobStore != null && jobUuid != null && !jobStore.markProcessingIfQueued(jobUuid)) {
                log.info("Export job {} skipped (not queued — cancelled or duplicate)", job.jobId());
                ExportReplayMetrics.jobSkipped("not_queued");
                return;
            }
            if (jobStore != null && jobUuid != null) {
                ExportReplayMetrics.jobStarted();
            }
            sleepDebugDelayIfConfigured(job.jobId());

            try {
                var root = exportFromDatabase(job, jobUuid);
                Path artifact = out;
                if (includeFileBodies && fileBodyFetcher != null && includeReferencedFiles) {
                    var zip = exportDir.resolve(safeJobId + ".export.zip");
                    try {
                        ExportFileBundleBuilder.build(root, zip, fileBodyFetcher, maxFileBodies, maxFileBodyBytes);
                        artifact = zip;
                        Files.deleteIfExists(out);
                    } catch (IOException zipErr) {
                        log.warn("Export zip bundle failed jobId={}, keeping JSON: {}", job.jobId(), zipErr.getMessage());
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
                log.info(
                    "Export replay v1 written jobId={} path={} bundle={} messageCount={} versionRows={} reactionRows={} pinnedRows={} "
                        + "chatMemberRows={} referencedUserRows={} referencedFileRows={} fileBodiesIncluded={}",
                    job.jobId(),
                    artifact.toAbsolutePath(),
                    ExportOutputRef.isZipBundlePath(artifact.getFileName().toString()),
                    root.path("messageCount").asInt(0),
                    intOrZero(root, "messageVersionCount"),
                    intOrZero(root, "reactionCount"),
                    intOrZero(root, "pinnedCount"),
                    intOrZero(root, "chatMemberCount"),
                    intOrZero(root, "referencedUserCount"),
                    intOrZero(root, "referencedFileCount"),
                    root.path("fileBodies").path("includedCount").asInt(0)
                );
                if (!abortIfCancelled(jobUuid, artifact, out)) {
                    finishJob(job, "export_v1", artifact);
                }
            } catch (ExportCancelledException e) {
                abortIfCancelled(e.jobId(), out);
            } catch (IllegalArgumentException e) {
                log.warn("Export job invalid chat UUID jobId={} chatId={}", job.jobId(), job.chatId());
                writeError(out, job, "invalid_chat_id", e.getMessage());
                if (!abortIfCancelled(jobUuid, out)) {
                    finishJob(job, "export_failed", out);
                }
            } catch (SQLException e) {
                log.error("Export DB query failed jobId={}", job.jobId(), e);
                writeError(out, job, "db_error", "query_failed");
                if (!abortIfCancelled(jobUuid, out)) {
                    finishJob(job, "export_failed", out);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle export-replay message", e);
        }
    }

    private void sleepDebugDelayIfConfigured(String jobId) {
        if (debugDelayMs <= 0) {
            return;
        }
        log.info("Export job {} debug delay {} ms (EXPORT_REPLAY_DEBUG_DELAY_MS)", jobId, debugDelayMs);
        try {
            Thread.sleep(debugDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Export debug delay interrupted jobId={}", jobId);
        }
    }

    private void onCancelHint(io.nats.client.Message msg) {
        ExportReplayMetrics.cancelHint();
        try {
            var event = MAPPER.readValue(msg.getData(), ExportReplayCancelEvent.class);
            log.debug("Export cancel hint jobId={} chatId={}", event.jobId(), event.chatId());
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
        log.info("Export job {} aborted — status export_cancelled", jobUuid);
        ExportReplayMetrics.jobCancelled();
        return true;
    }

    boolean natsConnected() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    private void finishJob(ExportReplayJob job, String status, Path out) {
        var pathStr = resolveStoredOutputPath(job, out);
        var jobUuid = parseJobId(job.jobId());
        var requester = parseUserId(job.requestedBy());
        if (jobStore != null && jobUuid != null) {
            if (jobStore.isCancelled(jobUuid)) {
                log.info("Export job {} cancelled, not applying terminal status {}", job.jobId(), status);
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
                log.warn("MinIO export upload failed jobId={}, using local path: {}", job.jobId(), e.getMessage());
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
                                if (tryAddUuidString(avatarId.trim(), referencedFileIds, maxFileIdsFromContent)) {
                                    referencedFileIdsTruncated[0] = true;
                                }
                            }
                        }
                        root.set("chat", chatRowToNode(rs));
                    } else {
                        root.putNull("chat");
                        root.put("chatMissing", true);
                        log.warn("Export job: no chats row for chatId={} jobId={}", job.chatId(), job.jobId());
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
                    if (isE2eeEnvelopeType(type)) {
                        e2eeMessageCount++;
                    } else {
                        nonE2eeMessageCount++;
                    }
                    if (includeReferencedFiles) {
                        if (!isE2eeEnvelopeType(type)) {
                            var content = rs.getString("content");
                            if (collectFileIdsFromText(content, referencedFileIds, maxFileIdsFromContent)) {
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
                            if (!isE2eeEnvelopeType(parentType)) {
                                var vcontent = rs.getString("content");
                                if (collectFileIdsFromText(vcontent, referencedFileIds, maxFileIdsFromContent)) {
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
            log.warn("Solr index export failed chatId={}: {}", chatId, e.getMessage());
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
        if (isE2eeEnvelopeType(type)) {
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

    /**
     * Scans {@code text} for UUID-shaped tokens (e.g. embedded in URLs). Does not scan E2EE ciphertext callers should gate.
     *
     * @return true if {@code maxSinkSize} was reached while more matches may remain in the string
     */
    static boolean collectFileIdsFromText(String text, Set<UUID> sink, int maxSinkSize) {
        return ExportMessageLoader.collectFileIdsFromText(text, sink, maxSinkSize);
    }

    /**
     * @return true if {@code maxSinkSize} is already reached (caller should record truncation).
     */
    static boolean tryAddUuidString(String raw, Set<UUID> sink, int maxSinkSize) {
        return ExportMessageLoader.tryAddUuidString(raw, sink, maxSinkSize);
    }

    /**
     * MLS-encrypted payloads are stored with {@code e2ee-*} types ({@code MessageService}); plaintext is not exported here.
     */
    static boolean isE2eeEnvelopeType(String type) {
        return ExportMessageLoader.isE2eeEnvelopeType(type);
    }

    static String messageSubsetWhere(boolean applyTtlFilter) {
        return ExportMessageLoader.messageSubsetWhere(applyTtlFilter);
    }

    static String buildMessageIdSubsetSql(boolean applyTtlFilter) {
        return ExportMessageLoader.buildMessageIdSubsetSql(applyTtlFilter);
    }

    static String buildMessagesSql(boolean applyTtlFilter) {
        return ExportMessageLoader.buildMessagesSql(applyTtlFilter);
    }

    static String buildMessageVersionsSql(boolean applyTtlFilter) {
        return ExportMessageLoader.buildMessageVersionsSql(applyTtlFilter);
    }

    static String buildMessageReactionsSql(boolean applyTtlFilter) {
        return ExportMessageLoader.buildMessageReactionsSql(applyTtlFilter);
    }

    static String buildPinnedMessagesSql(boolean applyTtlFilter) {
        return ExportMessageLoader.buildPinnedMessagesSql(applyTtlFilter);
    }

    static String buildReferencedUsersSql(boolean applyTtlFilter) {
        return ExportMessageLoader.buildReferencedUsersSql(applyTtlFilter);
    }

    private static ObjectNode versionRowToNode(ResultSet rs, Map<String, String> messageTypesById) throws SQLException {
        var n = MAPPER.createObjectNode();
        n.put("id", Long.toString(rs.getLong("id")));
        var messageId = rs.getString("message_id");
        n.put("messageId", messageId);
        n.put("editedBy", rs.getString("edited_by"));
        putInstant(n, "createdAt", rs.getTimestamp("created_at"));
        var parentType = messageTypesById.getOrDefault(messageId, "text");
        if (isE2eeEnvelopeType(parentType)) {
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

    /** Public fields only — no email, phone, or password hashes. */
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
            connection.publish(NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, MAPPER.writeValueAsBytes(done));
            log.debug("Published {} status={}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, status);
        } catch (Exception e) {
            log.warn("Failed to publish {}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE, e);
        }
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Error closing NATS connection", e);
        }
    }

    public static void main(String[] args) {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var dir = Path.of(System.getenv().getOrDefault("EXPORT_DIR", "export-output"));
        var publishComplete = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_PUBLISH_COMPLETE", "false"));
        var jdbcUrl = System.getenv("DB_JDBC_URL");
        var maxMessages = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_MESSAGES"), 100_000);
        var maxVersionRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_MESSAGE_VERSIONS"), 500_000);
        var includeVersions = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_VERSIONS", "true"));
        var maxReactionRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_REACTION_ROWS"), 500_000);
        var includeReactions = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_REACTIONS", "true"));
        var maxPinnedRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_PINNED_ROWS"), 50_000);
        var includePins = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_PINS", "true"));
        var maxChatMemberRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_CHAT_MEMBERS"), 100_000);
        var includeChat = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_CHAT", "true"));
        var includeChatMembers = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_CHAT_MEMBERS", "true"));
        var maxReferencedUserRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_REFERENCED_USERS"), 50_000);
        var includeReferencedUsers = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_REFERENCED_USERS", "true"));
        var maxReferencedFileRows = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_REFERENCED_FILES"), 100_000);
        var maxFileIdsFromContent = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_FILE_IDS_FROM_CONTENT"), 50_000);
        var includeReferencedFiles = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_REFERENCED_FILES", "true"));
        var messageTtlFilterApplied = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_APPLY_MESSAGE_TTL_FILTER", "true"));
        var minioUpload = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_REPLAY_MINIO_UPLOAD", "false"));
        var minioUploader = minioUpload ? ExportMinioUploader.fromEnv() : null;
        if (minioUpload && minioUploader == null) {
            log.warn("EXPORT_REPLAY_MINIO_UPLOAD=true but MinIO env incomplete (MINIO_ENDPOINT, keys)");
        }
        var queryTimeout = parseNonNegativeIntWithDefaultBlank(
            System.getenv("EXPORT_REPLAY_QUERY_TIMEOUT_SECONDS"),
            300
        );
        var includeRetentionPolicy = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_RETENTION_POLICY", "true"));
        var includeExportCompleteness = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_EXPORT_COMPLETENESS", "true"));
        var platformDefaults = ExportPlatformDefaults.fromEnv();
        var includeDeepArchive = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE", "false"));
        var maxDeepArchiveSnapshots = parsePositiveInt(
            System.getenv("EXPORT_REPLAY_MAX_DEEP_ARCHIVE_SNAPSHOTS"), 500);
        var deepArchiveReader = includeDeepArchive ? ExportDeepArchiveReader.fromEnv() : null;
        if (includeDeepArchive && deepArchiveReader == null) {
            log.warn("EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE=true but MinIO env incomplete");
        }
        var includeRetentionSnapshots = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS", "false"));
        var maxRetentionSnapshots = parsePositiveInt(
            System.getenv("EXPORT_REPLAY_MAX_RETENTION_SNAPSHOTS"), 500);
        var retentionSnapshotReader = includeRetentionSnapshots ? ExportRetentionSnapshotReader.fromEnv() : null;
        if (includeRetentionSnapshots && retentionSnapshotReader == null) {
            log.warn("EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS=true but MinIO env incomplete");
        }
        var includeSolrIndex = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_SOLR_INDEX", "false"));
        var maxSolrDocs = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_SOLR_DOCS"), 10_000);
        var solrReader = includeSolrIndex ? ExportSolrReader.fromEnv() : null;
        if (includeSolrIndex && solrReader == null) {
            log.warn("EXPORT_REPLAY_INCLUDE_SOLR_INDEX=true but SOLR_URL/SOLR_ZK not set");
        }
        var includeFileBodies = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_FILE_BODIES", "false"));
        var maxFileBodies = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_FILE_BODIES"), 500);
        var maxFileBodyBytes = parsePositiveLong(System.getenv("EXPORT_REPLAY_MAX_FILE_BODY_BYTES"), 52_428_800L);
        ExportFileBodyFetcher fileBodyFetcher = includeFileBodies ? ExportFileBodyFetcher.Minio.fromEnv() : null;
        if (includeFileBodies && fileBodyFetcher == null) {
            log.warn("EXPORT_REPLAY_INCLUDE_FILE_BODIES=true but MinIO env incomplete");
        }

        com.zaxxer.hikari.HikariDataSource ds = null;
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            var user = System.getenv().getOrDefault("DB_USER", "avandocmsg");
            var password = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
            var cfg = new com.zaxxer.hikari.HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(user);
            cfg.setPassword(password);
            cfg.setMaximumPoolSize(2);
            cfg.setPoolName("export-replay-worker");
            ds = new com.zaxxer.hikari.HikariDataSource(cfg);
            log.info(
                "DB export enabled JDBC URL configured maxMessages={} includeVersions={} maxVersionRows={} "
                    + "includeReactions={} maxReactionRows={} includePins={} maxPinnedRows={} includeChat={} "
                    + "includeChatMembers={} maxChatMemberRows={} includeReferencedUsers={} maxReferencedUserRows={} "
                    + "includeReferencedFiles={} maxFileIdsFromContent={} maxReferencedFileRows={} queryTimeoutSec={}",
                maxMessages,
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
                queryTimeout
            );
        } else {
            log.warn("DB_JDBC_URL not set: export-replay writes stub JSON only");
        }

        var metricsPort = ExportPlatformDefaults.metricsPortFromEnv();
        ExportReplayMetricsHttpServer metricsServer = null;

        try {
            var worker = new ExportReplayWorker(
                natsUrl,
                dir,
                publishComplete,
                ds,
                maxMessages,
                queryTimeout,
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
                messageTtlFilterApplied,
                minioUploader,
                includeRetentionPolicy,
                includeExportCompleteness,
                platformDefaults,
                includeDeepArchive,
                maxDeepArchiveSnapshots,
                deepArchiveReader,
                includeRetentionSnapshots,
                maxRetentionSnapshots,
                retentionSnapshotReader,
                includeSolrIndex,
                maxSolrDocs,
                solrReader,
                includeFileBodies,
                maxFileBodies,
                maxFileBodyBytes,
                fileBodyFetcher
            );
            if (metricsPort > 0) {
                DefaultExports.initialize();
                metricsServer = ExportReplayMetricsHttpServer.start(metricsPort, worker::natsConnected);
                log.info(
                    "Prometheus metrics on http://0.0.0.0:{}/metrics; GET /health on same port",
                    metricsServer.getPort()
                );
            }
            worker.start();
            var finalDs = ds;
            var finalMetricsServer = metricsServer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                worker.shutdown();
                if (finalMetricsServer != null) {
                    finalMetricsServer.close();
                }
                if (finalDs != null) {
                    finalDs.close();
                }
            }));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal error", e);
            if (metricsServer != null) {
                metricsServer.close();
            }
            if (ds != null) {
                ds.close();
            }
            System.exit(1);
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

    private static long parsePositiveLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            var v = Long.parseLong(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Blank env → {@code defaultWhenBlank}; {@code "0"} → no JDBC statement timeout. */
    private static int parseNonNegativeIntWithDefaultBlank(String raw, int defaultWhenBlank) {
        if (raw == null || raw.isBlank()) {
            return defaultWhenBlank;
        }
        try {
            var v = Integer.parseInt(raw.trim());
            return Math.max(0, v);
        } catch (NumberFormatException e) {
            return defaultWhenBlank;
        }
    }
}
