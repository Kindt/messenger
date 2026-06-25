package com.avandocmsg.messenger.core.adapter.storage;

import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;

import java.io.InputStream;
import java.util.Optional;

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

    @Override
    public Optional<String> presignedGetUrl(String objectName, int ttlSeconds) {
        return Optional.empty();
    }

    @Override
    public Optional<String> presignedPutUrl(String objectName, int ttlSeconds, String contentType) {
        return Optional.empty();
    }
}
