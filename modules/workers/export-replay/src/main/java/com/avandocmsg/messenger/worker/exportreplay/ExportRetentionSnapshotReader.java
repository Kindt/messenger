package com.avandocmsg.messenger.worker.exportreplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.minio.MinioClient;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loads retention hot-body JSON snapshots from MinIO ({@code retention_hot_body_applied} + object keys). */
final class ExportRetentionSnapshotReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SOURCE = "retention-hot-body";

    private final MinioClient client;
    private final String bucket;
    private final String objectPrefix;
    private final boolean tryDefaultKeyWhenNotInLog;
    private final UserMessageSource workerMessages;

    ExportRetentionSnapshotReader(
        MinioClient client,
        String bucket,
        String objectPrefix,
        boolean tryDefaultKeyWhenNotInLog,
        UserMessageSource workerMessages
    ) {
        this.client = client;
        this.bucket = bucket;
        this.objectPrefix = normalizePrefix(objectPrefix);
        this.tryDefaultKeyWhenNotInLog = tryDefaultKeyWhenNotInLog;
        this.workerMessages = workerMessages;
    }

    static ExportRetentionSnapshotReader fromEnv(UserMessageSource workerMessages) {
        var endpoint = System.getenv("MINIO_ENDPOINT");
        var accessKey = System.getenv("MINIO_ACCESS_KEY");
        var secretKey = System.getenv("MINIO_SECRET_KEY");
        if (endpoint == null || endpoint.isBlank() || accessKey == null || secretKey == null) {
            return null;
        }
        var bucket = System.getenv("EXPORT_REPLAY_RETENTION_MINIO_BUCKET");
        if (bucket == null || bucket.isBlank()) {
            bucket = System.getenv("RETENTION_MINIO_BUCKET");
        }
        if (bucket == null || bucket.isBlank()) {
            bucket = System.getenv().getOrDefault("MINIO_BUCKET", "avandocmsg");
        }
        var prefix = System.getenv("EXPORT_REPLAY_RETENTION_OBJECT_PREFIX");
        if (prefix == null || prefix.isBlank()) {
            prefix = System.getenv("RETENTION_MINIO_OBJECT_PREFIX");
        }
        var tryDefault = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_RETENTION_TRY_DEFAULT_KEY", "true"));
        var client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        return new ExportRetentionSnapshotReader(client, bucket.trim(), prefix, tryDefault, workerMessages);
    }

    static String defaultObjectKey(String prefix, String messageId) {
        return normalizePrefix(prefix) + messageId + ".json";
    }

    static String normalizePrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "retention/body/";
        }
        var s = raw.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.isBlank()) {
            return "retention/body/";
        }
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return s;
    }

    RetentionSnapshotAttachResult attachSnapshots(
        DataSource dataSource,
        ArrayNode messages,
        int maxSnapshots,
        int jdbcQueryTimeoutSeconds
    ) throws SQLException {
        var snapshots = MAPPER.createArrayNode();
        var idsToScan = new ArrayList<String>();
        for (var msg : messages) {
            if (idsToScan.size() >= maxSnapshots) {
                break;
            }
            idsToScan.add(msg.get("id").asText());
        }
        if (idsToScan.isEmpty()) {
            return new RetentionSnapshotAttachResult(snapshots, 0, 0, idsToScan.size() >= maxSnapshots && messages.size() > maxSnapshots);
        }

        var keysByMessageId = loadStorageKeys(dataSource, idsToScan, jdbcQueryTimeoutSeconds);
        int found = 0;
        for (var messageId : idsToScan) {
            var key = keysByMessageId.get(messageId);
            if (key == null && tryDefaultKeyWhenNotInLog) {
                key = defaultObjectKey(objectPrefix, messageId);
            }
            if (key == null) {
                continue;
            }
            var snap = ExportMinioJsonFetcher.fetchSnapshot(client, bucket, key, messageId, SOURCE, workerMessages);
            if (snap.isPresent()) {
                snapshots.add(snap.get());
                found++;
            }
        }
        var truncated = messages.size() > maxSnapshots;
        return new RetentionSnapshotAttachResult(snapshots, idsToScan.size(), found, truncated);
    }

    private Map<String, String> loadStorageKeys(
        DataSource dataSource,
        List<String> messageIds,
        int jdbcQueryTimeoutSeconds
    ) throws SQLException {
        var uuids = new UUID[messageIds.size()];
        for (int i = 0; i < messageIds.size(); i++) {
            uuids[i] = UUID.fromString(messageIds.get(i));
        }
        var sql = """
            SELECT message_id, storage_object_key
            FROM retention_hot_body_applied
            WHERE message_id = ANY(?::uuid[])
            """;
        var map = new HashMap<String, String>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            if (jdbcQueryTimeoutSeconds > 0) {
                ps.setQueryTimeout(jdbcQueryTimeoutSeconds);
            }
            var arr = conn.createArrayOf("uuid", uuids);
            try {
                ps.setArray(1, arr);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        var mid = rs.getObject("message_id", UUID.class).toString();
                        var key = rs.getString("storage_object_key");
                        if (key != null && !key.isBlank()) {
                            map.put(mid, key);
                        }
                    }
                }
            } finally {
                arr.free();
            }
        }
        return map;
    }

    String bucket() {
        return bucket;
    }

    String objectPrefix() {
        return objectPrefix;
    }

    record RetentionSnapshotAttachResult(
        ArrayNode snapshots,
        int messagesScanned,
        int snapshotsFound,
        boolean truncated
    ) {
    }
}
