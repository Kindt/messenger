package com.avandocmsg.messenger.desktop.sdk.secure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySecureTokenStore implements SecureTokenStore {

    private final Map<String, String> map = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
        map.put(key, value);
    }

    @Override
    public String get(String key) {
        return map.get(key);
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }
}
