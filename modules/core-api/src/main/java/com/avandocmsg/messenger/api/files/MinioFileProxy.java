package com.avandocmsg.messenger.api.files;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class MinioFileProxy implements FileProxy {
    private static final Logger log = LoggerFactory.getLogger(MinioFileProxy.class);

    private final MinioClient client;
    private final String bucket;

    public MinioFileProxy(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void upload(String objectName, InputStream data, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .stream(data, size, -1)
            .contentType(contentType)
            .build());
    }

    @Override
    public InputStream download(String objectName) throws Exception {
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
