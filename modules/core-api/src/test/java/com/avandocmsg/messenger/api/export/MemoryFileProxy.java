package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.files.FileProxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link FileProxy} for H2 integration tests. */
final class MemoryFileProxy implements FileProxy {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void upload(String objectName, InputStream data, long size, String contentType) throws IOException {
        objects.put(objectName, data.readAllBytes());
    }

    @Override
    public InputStream download(String objectName) throws IOException {
        var bytes = objects.get(objectName);
        if (bytes == null) {
            throw new IOException("not found: " + objectName);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(String objectName) throws IOException {
        objects.remove(objectName);
    }

    boolean hasObject(String objectName) {
        return objects.containsKey(objectName);
    }
}
