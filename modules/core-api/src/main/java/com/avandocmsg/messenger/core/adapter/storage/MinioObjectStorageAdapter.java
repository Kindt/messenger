package com.avandocmsg.messenger.core.adapter.storage;

import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

import java.io.InputStream;

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
}
