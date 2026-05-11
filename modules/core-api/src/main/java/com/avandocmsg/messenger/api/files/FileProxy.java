package com.avandocmsg.messenger.api.files;

import java.io.InputStream;

public interface FileProxy {
    void upload(String objectName, InputStream data, long size, String contentType) throws Exception;
    InputStream download(String objectName) throws Exception;
    void delete(String objectName) throws Exception;
}
