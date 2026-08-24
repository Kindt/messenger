package com.avandocmsg.messenger.media;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesCmSrtpCipher {

    private static final int AUTH_TAG_BYTES = 10;

    private final byte[] encryptionKey;
    private final byte[] authenticationKey;
    private final byte[] salt;

    public AesCmSrtpCipher(SrtpSessionKeys keys) {
        encryptionKey = keys.encryptionKey();
        authenticationKey = keys.authenticationKey();
        salt = keys.salt();
    }

    public byte[] protect(byte[] plainRtp, long rolloverCounter) {
        var headerLength = headerLength(plainRtp, plainRtp.length);
        var output = Arrays.copyOf(plainRtp, plainRtp.length + AUTH_TAG_BYTES);
        cryptPayload(output, headerLength, plainRtp.length - headerLength, rolloverCounter);
        var tag = authenticationTag(output, plainRtp.length, rolloverCounter);
        System.arraycopy(tag, 0, output, plainRtp.length, AUTH_TAG_BYTES);
        return output;
    }

    public byte[] unprotect(byte[] protectedRtp, long rolloverCounter) {
        if (protectedRtp == null || protectedRtp.length < 12 + AUTH_TAG_BYTES) {
            throw new IllegalArgumentException("SRTP packet too short");
        }
        var rtpLength = protectedRtp.length - AUTH_TAG_BYTES;
        var expected = authenticationTag(protectedRtp, rtpLength, rolloverCounter);
        var actual = Arrays.copyOfRange(protectedRtp, rtpLength, protectedRtp.length);
        if (!MessageDigest.isEqual(Arrays.copyOf(expected, AUTH_TAG_BYTES), actual)) {
            throw new SecurityException("invalid SRTP authentication tag");
        }
        var output = Arrays.copyOf(protectedRtp, rtpLength);
        var headerLength = headerLength(output, output.length);
        cryptPayload(output, headerLength, output.length - headerLength, rolloverCounter);
        return output;
    }

    private void cryptPayload(byte[] packet, int offset, int length, long rolloverCounter) {
        try {
            var cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(encryptionKey, "AES"),
                new IvParameterSpec(initializationVector(packet, rolloverCounter))
            );
            var transformed = cipher.doFinal(packet, offset, length);
            System.arraycopy(transformed, 0, packet, offset, transformed.length);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("AES-CTR unavailable", error);
        }
    }

    private byte[] authenticationTag(byte[] packet, int rtpLength, long rolloverCounter) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authenticationKey, "HmacSHA1"));
            mac.update(packet, 0, rtpLength);
            mac.update((byte) (rolloverCounter >>> 24));
            mac.update((byte) (rolloverCounter >>> 16));
            mac.update((byte) (rolloverCounter >>> 8));
            mac.update((byte) rolloverCounter);
            return mac.doFinal();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA1 unavailable", error);
        }
    }

    private byte[] initializationVector(byte[] rtp, long rolloverCounter) {
        var iv = new byte[16];
        System.arraycopy(salt, 0, iv, 0, salt.length);
        var ssrc = unsignedInt(rtp, 8);
        iv[4] ^= (byte) (ssrc >>> 24);
        iv[5] ^= (byte) (ssrc >>> 16);
        iv[6] ^= (byte) (ssrc >>> 8);
        iv[7] ^= (byte) ssrc;
        var sequenceNumber = ((long) Byte.toUnsignedInt(rtp[2]) << 8) | Byte.toUnsignedInt(rtp[3]);
        var packetIndex = ((rolloverCounter & 0xffffffffL) << 16) | sequenceNumber;
        for (var i = 0; i < 6; i++) {
            iv[8 + i] ^= (byte) (packetIndex >>> (40 - i * 8));
        }
        return iv;
    }

    private static int headerLength(byte[] packet, int rtpLength) {
        if (packet == null || rtpLength < 12) {
            throw new IllegalArgumentException("RTP packet too short");
        }
        var first = Byte.toUnsignedInt(packet[0]);
        if ((first >>> 6) != 2) {
            throw new IllegalArgumentException("unsupported RTP version");
        }
        var length = 12 + (first & 0x0f) * 4;
        if (length > rtpLength) {
            throw new IllegalArgumentException("truncated RTP CSRC list");
        }
        if ((first & 0x10) != 0) {
            if (length + 4 > rtpLength) {
                throw new IllegalArgumentException("truncated RTP extension");
            }
            var words = (Byte.toUnsignedInt(packet[length + 2]) << 8)
                | Byte.toUnsignedInt(packet[length + 3]);
            length += 4 + words * 4;
            if (length > rtpLength) {
                throw new IllegalArgumentException("truncated RTP extension payload");
            }
        }
        return length;
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) Byte.toUnsignedInt(bytes[offset]) << 24)
            | ((long) Byte.toUnsignedInt(bytes[offset + 1]) << 16)
            | ((long) Byte.toUnsignedInt(bytes[offset + 2]) << 8)
            | Byte.toUnsignedInt(bytes[offset + 3]);
    }
}
