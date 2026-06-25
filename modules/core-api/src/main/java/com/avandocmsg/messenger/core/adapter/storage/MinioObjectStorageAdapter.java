package com.avandocmsg.messenger.core.adapter.storage;

import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;

import java.io.InputStream;
import java.util.Optional;

/** MinIO adapter for {@link ObjectStoragePort}. */
public final class MinioObjectStorageAdapter implements ObjectStoragePort {
    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorageAdapter(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectName, InputStream data, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .stream(data, size, -1)
            .contentType(contentType)
            .build());
    }

    @Override
    public InputStream get(String objectName) throws Exception {
        return client.getObject(GetObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .build());
    }

    @Override
    public void delete(String objectName) throws Exception {
        client.removeObject(RemoveObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .build());
    }

    @Override
    public Optional<String> presignedGetUrl(String objectName, int ttlSeconds) throws Exception {
        return Optional.of(client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(ttlSeconds)
                .build()));
    }

    @Override
    public Optional<String> presignedPutUrl(String objectName, int ttlSeconds, String contentType) throws Exception {
        var builder = GetPresignedObjectUrlArgs.builder()
            .method(Method.PUT)
            .bucket(bucket)
            .object(objectName)
            .expiry(ttlSeconds);
        if (contentType != null && !contentType.isBlank()) {
            builder.extraHeaders(java.util.Map.of("Content-Type", contentType));
        }
        return Optional.of(client.getPresignedObjectUrl(builder.build()));
    }
}
