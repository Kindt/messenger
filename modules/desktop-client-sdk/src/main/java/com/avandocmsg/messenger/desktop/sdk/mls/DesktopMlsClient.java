package com.avandocmsg.messenger.desktop.sdk.mls;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Desktop MLS phase-1: AES-GCM session keys (parity with web korus-mls-wasm.js). */
public final class DesktopMlsClient {

    private final KorusApiClient api;
    private final boolean demo;
    private final MlsAesGcmCipher cipher = new MlsAesGcmCipher();
    private final Map<String, MlsSessionInfo> cache = new ConcurrentHashMap<>();

    public DesktopMlsClient(KorusApiClient api, boolean demo) {
        this.api = api;
        this.demo = demo;
    }

    public String encrypt(String plaintext, String chatId, String token) {
        var sess = session(chatId, token);
        var key = MlsSessionKeyDeriver.derive(sess.sessionId(), chatId);
        return cipher.encrypt(plaintext, key, chatId, sess.epoch());
    }

    public String decrypt(String ciphertext, String chatId, String token) {
        if (!MlsWireCodec.looksEncrypted(ciphertext)) {
            return ciphertext;
        }
        var sess = session(chatId, token);
        var key = MlsSessionKeyDeriver.derive(sess.sessionId(), chatId);
        return cipher.decrypt(ciphertext, key, chatId, sess.epoch());
    }

    public void clearCache(String chatId) {
        if (chatId == null) {
            cache.clear();
        } else {
            cache.remove(chatId);
        }
    }

    private MlsSessionInfo session(String chatId, String token) {
        return cache.computeIfAbsent(chatId, id -> {
            if (demo) {
                return new MlsSessionInfo("demo-mls-session", 1L);
            }
            return api.mlsSession(token, id);
        });
    }
}
