package com.avandocmsg.messenger.api.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinioConfig {
    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);
    private final MinioClient client;

    public MinioConfig(AppConfig appConfig) {
        this.client = MinioClient.builder()
            .endpoint(appConfig.minioEndpoint())
            .credentials(appConfig.minioAccessKey(), appConfig.minioSecretKey())
            .build();
        ensureBucket(appConfig.minioBucket());
        log.info("MinIO connected: {} bucket={}", appConfig.minioEndpoint(), appConfig.minioBucket());
    }

    private void ensureBucket(String bucket) {
        try {
            var exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure MinIO bucket {}: {}", bucket, e.getMessage());
        }
    }

    public MinioClient client() {
        return client;
    }
}
