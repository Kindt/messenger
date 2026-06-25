package com.avandocmsg.messenger.api.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MinioConfig {
    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);
    private final MinioClient client;

    public MinioConfig(AppConfig appConfig) {
        var httpClient = new OkHttpClient.Builder()
            .connectTimeout(appConfig.minioConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(appConfig.minioReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(appConfig.minioWriteTimeoutMs(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(appConfig.minioHttpRetryOnConnectionFailure())
            .connectionPool(new ConnectionPool(
                appConfig.minioHttpMaxIdleConnections(),
                appConfig.minioHttpKeepAliveMinutes(),
                TimeUnit.MINUTES))
            .build();
        this.client = MinioClient.builder()
            .endpoint(appConfig.minioEndpoint())
            .credentials(appConfig.minioAccessKey(), appConfig.minioSecretKey())
            .httpClient(httpClient)
            .build();
        ensureBucket(appConfig.minioBucket());
        log.info(
            "MinIO connected: {} bucket={} (connectTimeout={}ms, pool={})",
            appConfig.minioEndpoint(),
            appConfig.minioBucket(),
            appConfig.minioConnectTimeoutMs(),
            appConfig.minioHttpMaxIdleConnections());
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
