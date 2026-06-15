package com.avandocmsg.messenger.core.adapter.cache;

import com.avandocmsg.messenger.core.port.ReadCachePort;

import java.util.Optional;

/** Disabled read cache — always miss, no Redis I/O. */
public enum NoOpReadCacheAdapter implements ReadCachePort {
    INSTANCE;

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, String jsonValue, int ttlSeconds) {
        // no-op
    }

    @Override
    public void invalidate(String key) {
        // no-op
    }
}
