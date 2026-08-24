package com.avandocmsg.messenger.media;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public record SrtpSessionKeys(
    byte[] encryptionKey,
    byte[] authenticationKey,
    byte[] salt
) {
    private static final int MASTER_KEY_BYTES = 16;
    private static final int MASTER_SALT_BYTES = 14;

    public SrtpSessionKeys {
        encryptionKey = copy(encryptionKey, 16, "encryptionKey");
        authenticationKey = copy(authenticationKey, 20, "authenticationKey");
        salt = copy(salt, 14, "salt");
    }

    public static SrtpSessionKeys derive(byte[] masterKey, byte[] masterSalt) {
        return derive(masterKey, masterSalt, 0x00);
    }

    public static SrtpSessionKeys deriveRtcp(byte[] masterKey, byte[] masterSalt) {
        return derive(masterKey, masterSalt, 0x03);
    }

    private static SrtpSessionKeys derive(byte[] masterKey, byte[] masterSalt, int firstLabel) {
        var key = copy(masterKey, MASTER_KEY_BYTES, "masterKey");
        var salt = copy(masterSalt, MASTER_SALT_BYTES, "masterSalt");
        return new SrtpSessionKeys(
            derive(key, salt, firstLabel, 16),
            derive(key, salt, firstLabel + 1, 20),
            derive(key, salt, firstLabel + 2, 14)
        );
    }

    @Override
    public byte[] encryptionKey() {
        return encryptionKey.clone();
    }

    @Override
    public byte[] authenticationKey() {
        return authenticationKey.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    private static byte[] derive(byte[] masterKey, byte[] masterSalt, int label, int length) {
        try {
            var iv = new byte[16];
            System.arraycopy(masterSalt, 0, iv, 0, masterSalt.length);
            iv[7] ^= (byte) label;
            var cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(masterKey, "AES"),
                new IvParameterSpec(iv)
            );
            return cipher.doFinal(new byte[length]);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("AES-CTR unavailable", error);
        }
    }

    private static byte[] copy(byte[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must be " + length + " bytes");
        }
        return value.clone();
    }
}
