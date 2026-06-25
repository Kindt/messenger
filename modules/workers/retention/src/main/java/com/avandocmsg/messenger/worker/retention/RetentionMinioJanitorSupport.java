package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared MinIO helpers for retention janitors: batch {@code removeObjects} and existence checks
 * with prefix listing plus deduplicated {@code statObject} fallback.
 */
final class RetentionMinioJanitorSupport {

    private static final Logger log = LoggerFactory.getLogger(RetentionMinioJanitorSupport.class);

    /** S3/MinIO multi-object delete limit per request. */
    static final int REMOVE_OBJECTS_CHUNK_SIZE = 1000;

    private RetentionMinioJanitorSupport() {
    }

    record MinioDeleteRequest(UUID fileId, String objectName) {
    }

    /**
     * Lazily lists object keys under {@code prefix} (recursive). Used once per hot-body batch
     * instead of per-message {@code statObject} for retention snapshot keys.
     */
    static Set<String> listObjectKeys(MinioClient minioClient, String bucket, String prefix) throws Exception {
        var keys = new HashSet<String>();
        if (minioClient == null || bucket == null || bucket.isBlank() || prefix == null) {
            return keys;
        }
        Iterable<Result<Item>> results = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build());
        for (Result<Item> result : results) {
            Item item = result.get();
            if (item != null && item.objectName() != null) {
                keys.add(item.objectName());
            }
        }
        return keys;
    }

    /**
     * Batch existence index: {@code listedKeys} from prefix listing; {@code statObject} at most once
     * per distinct key on cache miss (e.g. deep-archive keys under {@code messages/}).
     */
    static final class ExistenceIndex {
        private final MinioClient minioClient;
        private final String bucket;
        private final Set<String> listedKeys;
        private final Map<String, Boolean> statCache = new HashMap<>();
        private final UserMessageSource logMessages;

        ExistenceIndex(
            MinioClient minioClient,
            String bucket,
            Set<String> listedKeys,
            UserMessageSource logMessages
        ) {
            this.minioClient = minioClient;
            this.bucket = bucket;
            this.listedKeys = listedKeys != null ? Set.copyOf(listedKeys) : Set.of();
            this.logMessages = logMessages;
        }

        boolean objectExists(String objectKey) {
            if (objectKey == null || objectKey.isBlank()) {
                return false;
            }
            if (listedKeys.contains(objectKey)) {
                return true;
            }
            return statCache.computeIfAbsent(objectKey, this::statExists);
        }

        private boolean statExists(String objectKey) {
            try {
                minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
                return true;
            } catch (ErrorResponseException e) {
                var code = e.errorResponse() != null ? e.errorResponse().code() : "";
                if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                    return false;
                }
                if (logMessages != null) {
                    log.debug(logMessages.format(
                        "worker.retention.hot_body.stat_unexpected", bucket, objectKey, e.getMessage()));
                }
                return false;
            } catch (Exception e) {
                if (logMessages != null) {
                    log.debug(logMessages.format(
                        "worker.retention.hot_body.stat_failed", bucket, objectKey, e.getMessage()));
                }
                return false;
            }
        }
    }

    static void removeObjectsBatch(
        MinioClient minioClient,
        boolean minioEnabled,
        String bucket,
        List<MinioDeleteRequest> requests,
        UserMessageSource workerMessages
    ) {
        if (!minioEnabled || minioClient == null || bucket == null || bucket.isBlank() || requests.isEmpty()) {
            return;
        }
        for (int start = 0; start < requests.size(); start += REMOVE_OBJECTS_CHUNK_SIZE) {
            int end = Math.min(start + REMOVE_OBJECTS_CHUNK_SIZE, requests.size());
            removeObjectsChunk(minioClient, bucket, requests.subList(start, end), workerMessages);
        }
    }

    private static void removeObjectsChunk(
        MinioClient minioClient,
        String bucket,
        List<MinioDeleteRequest> chunk,
        UserMessageSource workerMessages
    ) {
        var objects = new ArrayList<DeleteObject>(chunk.size());
        var nameToFileId = new HashMap<String, UUID>(chunk.size());
        for (var req : chunk) {
            objects.add(new DeleteObject(req.objectName()));
            nameToFileId.put(req.objectName(), req.fileId());
        }
        try {
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucket).objects(objects).build());
            var failed = new HashSet<String>();
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                if (error != null && error.objectName() != null) {
                    failed.add(error.objectName());
                    RetentionMetrics.purgeError("minio_delete");
                    UUID fileId = nameToFileId.get(error.objectName());
                    log.warn(workerMessages.format(
                        "worker.retention.file.minio_delete_failed",
                        fileId != null ? fileId : error.objectName(),
                        error.objectName(),
                        error.message()));
                }
            }
            for (var req : chunk) {
                if (!failed.contains(req.objectName())) {
                    RetentionMetrics.minioObjectDeleted();
                }
            }
        } catch (Exception e) {
            RetentionMetrics.purgeError("minio_delete");
            for (var req : chunk) {
                log.warn(workerMessages.format(
                    "worker.retention.file.minio_delete_failed", req.fileId(), req.objectName(), e.getMessage()));
            }
        }
    }
}
