package com.avandocmsg.messenger.worker.exportreplay;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Uploads export JSON files to MinIO (S3-compatible). */
final class ExportMinioUploader {

    private static final Logger log = LoggerFactory.getLogger(ExportMinioUploader.class);

    private final MinioClient client;
    private final String bucket;

    ExportMinioUploader(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    void upload(Path localFile, String objectKey) throws Exception {
        var contentType = objectKey != null && objectKey.endsWith(".zip")
            ? "application/zip"
            : "application/json";
        var size = Files.size(localFile);
        try (var in = Files.newInputStream(localFile)) {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(in, size, -1)
                .contentType(contentType)
                .build());
        }
        log.info("Uploaded export to MinIO bucket={} key={} bytes={}", bucket, objectKey, size);
    }

    static ExportMinioUploader fromEnv() {
        var endpoint = System.getenv("MINIO_ENDPOINT");
        var accessKey = System.getenv("MINIO_ACCESS_KEY");
        var secretKey = System.getenv("MINIO_SECRET_KEY");
        var bucket = System.getenv().getOrDefault("MINIO_BUCKET", "avandocmsg");
        if (endpoint == null || endpoint.isBlank() || accessKey == null || secretKey == null) {
            return null;
        }
        var client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        return new ExportMinioUploader(client, bucket);
    }
}
