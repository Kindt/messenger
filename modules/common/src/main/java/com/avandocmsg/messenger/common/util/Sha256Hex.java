package com.avandocmsg.messenger.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 over arbitrary bytes, expressed as lowercase hexadecimal (no prefix). */
public final class Sha256Hex {

    private Sha256Hex() {
    }

    public static String of(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().withLowerCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
