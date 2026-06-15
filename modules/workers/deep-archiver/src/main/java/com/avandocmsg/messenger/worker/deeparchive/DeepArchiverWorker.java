package com.avandocmsg.messenger.worker.deeparchive;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotEnvelopeDigest;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.avandocmsg.messenger.common.retention.ChunkedSnapshotWriter;
import com.avandocmsg.messenger.common.retention.SnapshotCompression;
import com.avandocmsg.messenger.common.retention.SnapshotPartCodec;
import com.avandocmsg.messenger.common.retention.ContentAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Consumes {@link NatsSubjects#MSG_EVENT_DEEP_ARCHIVE} after {@link com.avandocmsg.messenger.worker.archiver.ArchiverWorker}
 * handoff.
 *
 * <p>Optional object storage (MinIO / S3-compatible), controlled by environment:
 * <ul>
 *   <li>{@code MINIO_ENDPOINT} — base URL, e.g. {@code http://localhost:9000}</li>
 *   <li>{@code MINIO_ACCESS_KEY} / {@code MINIO_SECRET_KEY} — credentials</li>
 *   <li>{@code MINIO_BUCKET} — bucket name (default {@code deep-archive})</li>
 *   <li>{@code MINIO_REGION} — optional region string</li>
 * </ul>
 * If any of endpoint/access/secret is missing, each event is acknowledged with INFO logging only (no MinIO write).
 */
public class DeepArchiverWorker {
    private static final Logger log = LoggerFactory.getLogger(DeepArchiverWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "deep-archiver-workers";

    private final Connection connection;
    private final MinioClient minioClient;
    private final String minioBucket;
    private final boolean minioEnabled;
    private final int chunkSizeBytes;
    private final SnapshotCompression compression;
    private final int zstdLevel;
    private final UserMessageSource workerMessages;

    public DeepArchiverWorker(String natsUrl, MinioClient minioClient, String minioBucket, boolean minioEnabled,
                                UserMessageSource workerMessages) throws Exception {
        this(natsUrl, minioClient, minioBucket, minioEnabled, ArchiveSnapshotFormat.DEFAULT_CHUNK_SIZE_BYTES,
            SnapshotCompression.fromEnv(), SnapshotCompression.zstdLevelFromEnv(), workerMessages);
    }

    public DeepArchiverWorker(String natsUrl, MinioClient minioClient, String minioBucket, boolean minioEnabled,
                               int chunkSizeBytes, UserMessageSource workerMessages) throws Exception {
        this(natsUrl, minioClient, minioBucket, minioEnabled, chunkSizeBytes,
            SnapshotCompression.fromEnv(), SnapshotCompression.zstdLevelFromEnv(), workerMessages);
    }

    public DeepArchiverWorker(String natsUrl, MinioClient minioClient, String minioBucket, boolean minioEnabled,
                               int chunkSizeBytes, SnapshotCompression compression, int zstdLevel,
                               UserMessageSource workerMessages) throws Exception {
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
        this.minioEnabled = minioEnabled;
        this.chunkSizeBytes = chunkSizeBytes;
        this.compression = compression != null ? compression : SnapshotCompression.NONE;
        this.zstdLevel = zstdLevel;
        this.workerMessages = workerMessages;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("deep-archiver-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void start() throws Exception {
        if (minioEnabled) {
            ensureBucket();
            log.info(workerMessages.format("worker.deep_archiver.minio_enabled", minioBucket));
        } else {
            log.info(workerMessages.get("worker.deep_archiver.minio_disabled"));
        }
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_DEEP_ARCHIVE, QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_DEEP_ARCHIVE, QUEUE_GROUP));
    }

    private void ensureBucket() throws Exception {
        var exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
            log.info(workerMessages.format("worker.deep_archiver.bucket_created", minioBucket));
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageWorkerEvent.class);
            log.info(workerMessages.format("worker.deep_archiver.received", event.messageId(), event.chatId()));
            if (minioEnabled) {
                var root = MAPPER.readTree(payload);
                var candidateText = resolveCandidateText(event, root);
                if (shouldSkipDeepArchiveForContent(candidateText)) {
                    log.info(workerMessages.format("worker.deep_archiver.skipped_file_ref", event.messageId()));
                    return;
                }
                var bytes = minioSnapshotBytesFromNatsJson(payload, MAPPER);
                if (shouldWriteChunked(chunkSizeBytes, bytes.length)) {
                    writeChunked(event.messageId(), bytes);
                } else {
                    var key = "messages/" + event.messageId() + ".json";
                    var stored = ChunkedSnapshotWriter.compressFlatSnapshot(bytes, compression, zstdLevel);
                    DeepArchiverMetrics.bytesSaved(SnapshotPartCodec.bytesSaved(bytes.length, stored.length));
                    minioClient.putObject(
                        PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(key)
                            .stream(new ByteArrayInputStream(stored), stored.length, -1)
                            .contentType(compression == SnapshotCompression.NONE ? "application/json" : "application/octet-stream")
                            .build()
                    );
                    log.debug(workerMessages.format("worker.deep_archiver.stored_object", key));
                }
            }
        } catch (Exception e) {
            log.error(workerMessages.get("worker.deep_archiver.handle_failed"), e);
        }
    }

    private void writeChunked(String messageId, byte[] jsonBytes) throws Exception {
        var dir = "messages/" + messageId + "/";
        int chunkCount = ChunkedSnapshotWriter.writeChunkedSnapshot(
            minioClient,
            minioBucket,
            dir,
            messageId,
            jsonBytes,
            chunkSizeBytes,
            MAPPER,
            compression,
            zstdLevel,
            DeepArchiverMetrics::bytesSaved
        );
        log.info(workerMessages.format("worker.deep_archiver.wrote_chunks", chunkCount, messageId, jsonBytes.length));
        DeepArchiverMetrics.chunkedMessage();
        for (int i = 0; i < chunkCount; i++) {
            DeepArchiverMetrics.chunkWrite();
        }
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
    }

    /**
     * MinIO object = NATS {@link MessageWorkerEvent} JSON plus {@link ArchiveSnapshotFormat} envelope fields
     * and {@link ArchiveSnapshotFormat#JSON_SNAPSHOT_SHA256} on the same root object (backward-compatible additive fields).
     */
    static byte[] minioSnapshotBytesFromNatsJson(String natsPayloadUtf8, ObjectMapper mapper) throws IOException {
        ObjectNode root = (ObjectNode) mapper.readTree(natsPayloadUtf8);
        root.put(ArchiveSnapshotFormat.JSON_SNAPSHOT_VERSION, ArchiveSnapshotFormat.SNAPSHOT_VERSION);
        root.put(ArchiveSnapshotFormat.JSON_PRODUCER, ArchiveSnapshotFormat.PRODUCER_DEEP_ARCHIVER);
        ArchiveSnapshotEnvelopeDigest.computeAndAttach(mapper, root);
        return mapper.writeValueAsBytes(root);
    }

    static String resolveCandidateText(MessageWorkerEvent event, JsonNode root) {
        // Worker events carry searchText (safe snippet), not raw content.
        // Keep legacy fallback for older publishers that may still emit "content".
        String candidateText = event.searchText();
        if ((candidateText == null || candidateText.isBlank()) && root != null && root.hasNonNull("searchText")) {
            candidateText = root.get("searchText").asText();
        }
        if ((candidateText == null || candidateText.isBlank()) && root != null && root.hasNonNull("content")) {
            candidateText = root.get("content").asText();
        }
        return candidateText;
    }

    static boolean shouldSkipDeepArchiveForContent(String candidateText) {
        return candidateText != null && ContentAnalyzer.isFileReference(candidateText);
    }

    static boolean shouldWriteChunked(int chunkSizeBytes, int payloadBytes) {
        return chunkSizeBytes > 0 && payloadBytes > chunkSizeBytes;
    }

    static int parseChunkSize(String chunkSizeEnv) {
        if (chunkSizeEnv == null || chunkSizeEnv.isBlank()) {
            return ArchiveSnapshotFormat.DEFAULT_CHUNK_SIZE_BYTES;
        }
        return Integer.parseInt(chunkSizeEnv);
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            DeepArchiverWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_deep_archiver");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var endpoint = System.getenv("MINIO_ENDPOINT");
        var access = System.getenv("MINIO_ACCESS_KEY");
        var secret = System.getenv("MINIO_SECRET_KEY");
        var bucket = System.getenv().getOrDefault("MINIO_BUCKET", "deep-archive");
        var region = System.getenv("MINIO_REGION");
        var chunkSizeEnv = System.getenv("DEEP_ARCHIVE_CHUNK_SIZE_BYTES");
        int chunkSize = parseChunkSize(chunkSizeEnv);

        MinioClient client = null;
        boolean minioOk = endpoint != null && !endpoint.isBlank()
            && access != null && !access.isBlank()
            && secret != null && !secret.isBlank();
        if (minioOk) {
            var builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(access, secret);
            if (region != null && !region.isBlank()) {
                builder.region(region);
            }
            client = builder.build();
        }

        try {
            var worker = new DeepArchiverWorker(natsUrl, client, bucket, minioOk, chunkSize, workerMessages);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error(workerMessages.get("worker.common.fatal_error"), e);
            System.exit(1);
        }
    }
}
