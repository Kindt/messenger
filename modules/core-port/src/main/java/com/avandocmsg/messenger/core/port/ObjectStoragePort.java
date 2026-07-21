package com.avandocmsg.messenger.core.port;

import java.io.InputStream;
import java.util.Optional;

/** Port for binary object storage (MinIO / file proxy). */
public interface ObjectStoragePort {
    void put(String objectName, InputStream data, long size, String contentType) throws Exception; // NOSONAR java:S112 -- port mirrors MinIO/IO failures

    InputStream get(String objectName) throws Exception; // NOSONAR java:S112 -- port mirrors MinIO/IO failures

    void delete(String objectName) throws Exception; // NOSONAR java:S112 -- port mirrors MinIO/IO failures

    /** Presigned GET URL when backend supports direct object access (MinIO). */
    Optional<String> presignedGetUrl(String objectName, int ttlSeconds) throws Exception; // NOSONAR java:S112 -- port mirrors MinIO/IO failures

    /** Presigned PUT URL for direct client upload (MinIO). */
    Optional<String> presignedPutUrl(String objectName, int ttlSeconds, String contentType) throws Exception; // NOSONAR java:S112 -- port mirrors MinIO/IO failures
}
