package com.avandocmsg.messenger.desktop.sdk.secure;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM for secrets at rest (FSTEC-aligned local protection). */
public final class AesGcmCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipher(byte[] rawKey) {
        if (rawKey == null || rawKey.length != 32) {
            throw new IllegalArgumentException("AES-256 key required");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            var iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            var enc = cipher.doFinal(plain);
            var out = ByteBuffer.allocate(iv.length + enc.length);
            out.put(iv);
            out.put(enc);
            return out.array();
        } catch (Exception e) {
            throw new IllegalStateException("encrypt", e);
        }
    }

    public byte[] decrypt(byte[] blob) {
        try {
            if (blob == null || blob.length <= IV_BYTES) {
                throw new IllegalArgumentException("invalid blob");
            }
            var iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
            var enc = Arrays.copyOfRange(blob, IV_BYTES, blob.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(enc);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt", e);
        }
    }
}
