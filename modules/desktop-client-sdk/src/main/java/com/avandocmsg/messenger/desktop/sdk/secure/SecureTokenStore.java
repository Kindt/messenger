package com.avandocmsg.messenger.desktop.sdk.secure;

public interface SecureTokenStore {
    void put(String key, String value);

    String get(String key);

    void remove(String key);

    void clear();
}
