package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.retention.DeepArchiveReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;

import java.util.Optional;

/** Reads deep-archive snapshots from MinIO, supporting both flat and chunked formats via {@link DeepArchiveReader}. */
final class ExportDeepArchiveReader {

    private static final String SOURCE = "deep-archive";
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

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
        var id = messageId.trim();
        var key = objectKeyForMessage(id);
        return DeepArchiveReader.readMessage(client, bucket, id)
            .map(in -> {
                try (in) {
                    var snapshot = MAPPER.readTree(in);
                    var out = MAPPER.createObjectNode();
                    out.put("messageId", id);
                    out.put("source", SOURCE);
                    out.put("objectKey", key);
                    out.put("bucket", bucket);
                    out.set("snapshot", snapshot);
                    return out;
                } catch (Exception e) {
                    return null;
                }
            });
    }

    String bucket() {
        return bucket;
    }
}
