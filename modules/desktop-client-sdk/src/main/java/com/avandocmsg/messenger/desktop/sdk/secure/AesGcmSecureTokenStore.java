package com.avandocmsg.messenger.desktop.sdk.secure;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** AES-GCM encrypted token map on disk. */
public final class AesGcmSecureTokenStore implements SecureTokenStore {

    private final Path file;
    private final AesGcmCipher cipher;
    private final Map<String, String> cache = new HashMap<>();

    public AesGcmSecureTokenStore(Path stateDir, byte[] masterKey) throws IOException {
        this.file = stateDir.resolve("tokens.enc");
        this.cipher = new AesGcmCipher(masterKey);
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

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        var blob = Files.readAllBytes(file);
        if (blob.length == 0) {
            return;
        }
        var json = new String(cipher.decrypt(blob), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, String> m = JsonSupport.mapper().readValue(json, Map.class);
        cache.clear();
        cache.putAll(m);
    }

    private void flush() {
        try {
            Files.createDirectories(file.getParent());
            var json = JsonSupport.mapper().writeValueAsBytes(cache);
            Files.write(file, cipher.encrypt(json));
        } catch (IOException e) {
            throw new IllegalStateException("write tokens.enc", e);
        }
    }
}
