package com.avandocmsg.messenger.desktop.sdk.mls;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HKDF-SHA256 (RFC 5869) matching web `korus-mls-wasm.js`. */
public final class MlsSessionKeyDeriver {

    private static final String INFO = "mls-session-key";
    private static final int HASH_LEN = 32;

    private MlsSessionKeyDeriver() {}

    public static byte[] derive(String sessionId, String chatId) {
        var ikm = (sessionId + ":" + chatId).getBytes(StandardCharsets.UTF_8);
        var salt = new byte[HASH_LEN];
        var prk = hmacSha256(salt, ikm);
        return hkdfExpand(prk, INFO.getBytes(StandardCharsets.UTF_8), 32);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
        var out = new byte[length];
        var t = new byte[0];
        var offset = 0;
        for (byte counter = 1; offset < length; counter++) {
            var input = concat(t, info, new byte[] { counter });
            t = hmacSha256(prk, input);
            var copy = Math.min(t.length, length - offset);
            System.arraycopy(t, 0, out, offset, copy);
            offset += copy;
        }
        return out;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("hmac", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        var len = 0;
        for (var p : parts) {
            len += p.length;
        }
        var out = new byte[len];
        var pos = 0;
        for (var p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
