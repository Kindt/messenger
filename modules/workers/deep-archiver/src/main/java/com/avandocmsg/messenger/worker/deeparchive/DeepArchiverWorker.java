package com.avandocmsg.messenger.worker.deeparchive;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotEnvelopeDigest;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public DeepArchiverWorker(String natsUrl, MinioClient minioClient, String minioBucket, boolean minioEnabled) throws Exception {
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
        this.minioEnabled = minioEnabled;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("deep-archiver-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info("Connected to NATS at {}", natsUrl);
    }

    public void start() throws Exception {
        if (minioEnabled) {
            ensureBucket();
            log.info("MinIO deep-archive writes enabled bucket={}", minioBucket);
        } else {
            log.info("MinIO not configured (set MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY); logging receipt INFO only");
        }
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_DEEP_ARCHIVE, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {})", NatsSubjects.MSG_EVENT_DEEP_ARCHIVE, QUEUE_GROUP);
    }

    private void ensureBucket() throws Exception {
        var exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
            log.info("Created MinIO bucket {}", minioBucket);
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageWorkerEvent.class);
            log.info("Deep-archiver received messageId={} chatId={}", event.messageId(), event.chatId());
            if (minioEnabled) {
                var bytes = minioSnapshotBytesFromNatsJson(payload, MAPPER);
                var key = "messages/" + event.messageId() + ".json";
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(key)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("application/json")
                        .build()
                );
                log.debug("Stored deep-archive object {}", key);
            }
        } catch (Exception e) {
            log.error("Failed to handle deep-archiver message", e);
        }
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Error closing NATS connection", e);
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

    public static void main(String[] args) {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var endpoint = System.getenv("MINIO_ENDPOINT");
        var access = System.getenv("MINIO_ACCESS_KEY");
        var secret = System.getenv("MINIO_SECRET_KEY");
        var bucket = System.getenv().getOrDefault("MINIO_BUCKET", "deep-archive");
        var region = System.getenv("MINIO_REGION");

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
            var worker = new DeepArchiverWorker(natsUrl, client, bucket, minioOk);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }
}
