package com.avandocmsg.messenger.core.port;

import java.io.InputStream;

/** Port for binary object storage (MinIO / file proxy). */
public interface ObjectStoragePort {
    void put(String objectName, InputStream data, long size, String contentType) throws Exception;

    InputStream get(String objectName) throws Exception;

    void delete(String objectName) throws Exception;
}
