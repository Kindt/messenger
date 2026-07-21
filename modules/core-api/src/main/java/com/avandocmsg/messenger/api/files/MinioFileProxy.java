package com.avandocmsg.messenger.api.files;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

import java.io.IOException;
import java.io.InputStream;

public class MinioFileProxy implements FileProxy {
    private final MinioClient client;
    private final String bucket;

    public MinioFileProxy(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void upload(String objectName, InputStream data, long size, String contentType) throws IOException {
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(data, size, -1)
                .contentType(contentType)
                .build());
        } catch (Exception e) {
            throw new IOException("MinIO upload failed: " + objectName, e);
        }
    }

    @Override
    public InputStream download(String objectName) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        } catch (Exception e) {
            throw new IOException("MinIO download failed: " + objectName, e);
        }
    }

    @Override
    public void delete(String objectName) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        } catch (Exception e) {
            throw new IOException("MinIO delete failed: " + objectName, e);
        }
    }
}
