package com.avandocmsg.messenger.api.files;

import java.io.IOException;
import java.io.InputStream;

public interface FileProxy {
    void upload(String objectName, InputStream data, long size, String contentType) throws IOException;
    InputStream download(String objectName) throws IOException;
    void delete(String objectName) throws IOException;
}
