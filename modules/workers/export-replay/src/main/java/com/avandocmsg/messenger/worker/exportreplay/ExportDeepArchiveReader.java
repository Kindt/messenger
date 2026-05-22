package com.avandocmsg.messenger.worker.exportreplay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;

import java.util.Optional;

/** Reads {@code messages/{messageId}.json} snapshots from deep-archive MinIO (same layout as deep-archiver). */
final class ExportDeepArchiveReader {

    private static final String SOURCE = "deep-archive";

    private final MinioClient client;
    private final String bucket;

    ExportDeepArchiveReader(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    static String objectKeyForMessage(String messageId) {
        return "messages/" + messageId + ".json";
    }

    static ExportDeepArchiveReader fromEnv() {
        var endpoint = System.getenv("MINIO_ENDPOINT");
        var accessKey = System.getenv("MINIO_ACCESS_KEY");
        var secretKey = System.getenv("MINIO_SECRET_KEY");
        if (endpoint == null || endpoint.isBlank() || accessKey == null || secretKey == null) {
            return null;
        }
        var bucket = System.getenv("EXPORT_REPLAY_DEEP_ARCHIVE_BUCKET");
        if (bucket == null || bucket.isBlank()) {
            bucket = System.getenv().getOrDefault("MINIO_BUCKET", "deep-archive");
        }
        var client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        return new ExportDeepArchiveReader(client, bucket.trim());
    }

    Optional<ObjectNode> fetchMessageSnapshot(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        var key = objectKeyForMessage(messageId.trim());
        return ExportMinioJsonFetcher.fetchSnapshot(client, bucket, key, messageId.trim(), SOURCE);
    }

    String bucket() {
        return bucket;
    }
}
