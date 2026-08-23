package com.avandocmsg.messenger.desktop.sdk.secure;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Dev/lab token store — file under profile state (not production DPAPI yet). */
public final class FileSecureTokenStore implements SecureTokenStore {

    private final Path file;
    private final Map<String, String> cache = new HashMap<>();

    public FileSecureTokenStore(Path profileStateDir) {
        this.file = profileStateDir.resolve("tokens.json");
        load();
    }

    @Override
    public synchronized void put(String key, String value) {
        cache.put(key, value);
        flush();
    }

    @Override
    public synchronized String get(String key) {
        return cache.get(key);
    }

    @Override
    public synchronized void remove(String key) {
        cache.remove(key);
        flush();
    }

    @Override
    public synchronized void clear() {
        cache.clear();
        flush();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> m = JsonSupport.mapper().readValue(Files.readString(file), Map.class);
            cache.clear();
            cache.putAll(m);
        } catch (IOException e) {
            throw new IllegalStateException("read tokens", e);
        }
    }

    private void flush() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JsonSupport.mapper().writeValueAsString(cache));
        } catch (IOException e) {
            throw new IllegalStateException("write tokens", e);
        }
    }
}
