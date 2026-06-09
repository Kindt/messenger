from pathlib import Path

p = Path(r"D:\proj\korus_messenger\modules\workers\export-replay\src\main\java\com\avandocmsg\messenger\worker\exportreplay\ExportReplayWorker.java")
text = p.read_text(encoding="utf-8")
start = text.index("    public static void main(String[] args) {")
end = text.index("    private static long parseNonNegativeLong(String raw, long defaultValue) {")

new_main = r"""    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            ExportReplayWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_export_replay");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
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
        var minioUploader = minioUpload ? ExportMinioUploader.fromEnv(workerMessages) : null;
        if (minioUpload && minioUploader == null) {
            log.warn(workerMessages.get("worker.export_replay.minio_upload_enabled"));
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
            log.warn(workerMessages.get("worker.export_replay.deep_archive_minio_incomplete"));
        }
        var includeRetentionSnapshots = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS", "false"));
        var maxRetentionSnapshots = parsePositiveInt(
            System.getenv("EXPORT_REPLAY_MAX_RETENTION_SNAPSHOTS"), 500);
        var retentionSnapshotReader = includeRetentionSnapshots ? ExportRetentionSnapshotReader.fromEnv(workerMessages) : null;
        if (includeRetentionSnapshots && retentionSnapshotReader == null) {
            log.warn(workerMessages.get("worker.export_replay.retention_snapshots_minio_incomplete"));
        }
        var includeSolrIndex = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_SOLR_INDEX", "false"));
        var maxSolrDocs = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_SOLR_DOCS"), 10_000);
        var solrReader = includeSolrIndex ? ExportSolrReader.fromEnv() : null;
        if (includeSolrIndex && solrReader == null) {
            log.warn(workerMessages.get("worker.export_replay.solr_not_set"));
        }
        var includeFileBodies = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_INCLUDE_FILE_BODIES", "false"));
        var maxFileBodies = parsePositiveInt(System.getenv("EXPORT_REPLAY_MAX_FILE_BODIES"), 500);
        var maxFileBodyBytes = parsePositiveLong(System.getenv("EXPORT_REPLAY_MAX_FILE_BODY_BYTES"), 52_428_800L);
        ExportFileBodyFetcher fileBodyFetcher = includeFileBodies ? ExportFileBodyFetcher.Minio.fromEnv() : null;
        if (includeFileBodies && fileBodyFetcher == null) {
            log.warn(workerMessages.get("worker.export_replay.file_bodies_minio_incomplete"));
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
        } else {
            log.warn(workerMessages.get("worker.export_replay.db_stub_only"));
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
                fileBodyFetcher,
                workerMessages
            );
            if (metricsPort > 0) {
                DefaultExports.initialize();
                metricsServer = ExportReplayMetricsHttpServer.start(
                    metricsPort, worker::natsConnected, workerMessages);
                log.info(workerMessages.format("worker.export_replay.metrics_url", metricsServer.getPort()));
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
            log.error(workerMessages.get("worker.common.fatal_error"), e);
            if (metricsServer != null) {
                metricsServer.close();
            }
            if (ds != null) {
                ds.close();
            }
            System.exit(1);
        }
    }

"""

text = text[:start] + new_main + text[end:]

old_sub = '''        log.info(
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
        );'''
new_sub = '''        log.info(workerMessages.format("worker.export_replay.subscribed",
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
        ));'''
text = text.replace(old_sub, new_sub)

old_v1 = '''                log.info(
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
                );'''
new_v1 = '''                log.info(workerMessages.format("worker.export_replay.export_written",
                    job.jobId(),
                    artifact.toAbsolutePath(),
                    root.path("messageCount").asInt(0)));'''
text = text.replace(old_v1, new_v1)

p.write_text(text, encoding="utf-8")
print("fixed")
