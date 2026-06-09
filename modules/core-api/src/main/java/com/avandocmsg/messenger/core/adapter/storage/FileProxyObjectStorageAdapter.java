package com.avandocmsg.messenger.core.adapter.storage;

import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;

import java.io.InputStream;

/** Delegates {@link ObjectStoragePort} to legacy {@link FileProxy} (HTTP mode). */
public final class FileProxyObjectStorageAdapter implements ObjectStoragePort {
    private final FileProxy delegate;

    public FileProxyObjectStorageAdapter(FileProxy delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String objectName, InputStream data, long size, String contentType) throws Exception {
        delegate.upload(objectName, data, size, contentType);
    }

    @Override
    public InputStream get(String objectName) throws Exception {
        return delegate.download(objectName);
    }

    @Override
    public void delete(String objectName) throws Exception {
        delegate.delete(objectName);
    }
}
