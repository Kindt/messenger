package com.avandocmsg.messenger.worker.retention;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Создание бакета для снимков ретенции при старте воркера (если включено в конфиге).
 */
final class RetentionMinioBootstrap {
    private static final Logger log = LoggerFactory.getLogger(RetentionMinioBootstrap.class);

    private RetentionMinioBootstrap() {
    }

    static void ensureBucketExists(MinioClient client, String bucket) {
        if (client == null || bucket == null || bucket.isBlank()) {
            return;
        }
        try {
            var exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {} for retention snapshots", bucket);
            }
        } catch (Exception e) {
            log.warn("Retention MinIO bucket ensure failed bucket={}: {}", bucket, e.getMessage());
        }
    }
}
