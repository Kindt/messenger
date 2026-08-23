package com.avandocmsg.messenger.desktop.sdk.mls;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM encrypt/decrypt compatible with web MLS shim. */
public final class MlsAesGcmCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    public String encrypt(String plaintext, byte[] key, String chatId, long epoch) {
        try {
            var iv = new byte[NONCE_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(aad(chatId, epoch));
            var enc = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("mls encrypt", e);
        }
    }

    public String decrypt(String contentBase64, byte[] key, String chatId, long epoch) {
        try {
            var combined = Base64.getDecoder().decode(contentBase64.trim());
            if (combined.length < NONCE_BYTES + 16) {
                throw new IllegalArgumentException("invalid ciphertext");
            }
            var iv = Arrays.copyOfRange(combined, 0, NONCE_BYTES);
            var ct = Arrays.copyOfRange(combined, NONCE_BYTES, combined.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(aad(chatId, epoch));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("mls decrypt", e);
        }
    }

    private static byte[] aad(String chatId, long epoch) {
        return (chatId + ":" + epoch).getBytes(StandardCharsets.UTF_8);
    }
}
