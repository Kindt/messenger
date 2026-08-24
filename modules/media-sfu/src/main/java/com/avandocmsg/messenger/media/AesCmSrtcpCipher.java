package com.avandocmsg.messenger.media;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesCmSrtcpCipher {

    private static final int FIXED_HEADER_BYTES = 8;
    private static final int INDEX_BYTES = 4;
    private static final int AUTH_TAG_BYTES = 10;
    private static final int ENCRYPTED_FLAG = 0x80000000;

    private final byte[] encryptionKey;
    private final byte[] authenticationKey;
    private final byte[] salt;

    public AesCmSrtcpCipher(SrtpSessionKeys keys) {
        encryptionKey = keys.encryptionKey();
        authenticationKey = keys.authenticationKey();
        salt = keys.salt();
    }

    public byte[] protect(byte[] plainRtcp, int index) {
        validateRtcp(plainRtcp);
        validateIndex(index);
        var indexOffset = plainRtcp.length;
        var output = Arrays.copyOf(plainRtcp, indexOffset + INDEX_BYTES + AUTH_TAG_BYTES);
        cryptPayload(output, FIXED_HEADER_BYTES, plainRtcp.length - FIXED_HEADER_BYTES, index);
        writeInt(output, indexOffset, index | ENCRYPTED_FLAG);
        var tag = authenticationTag(output, indexOffset + INDEX_BYTES);
        System.arraycopy(tag, 0, output, indexOffset + INDEX_BYTES, AUTH_TAG_BYTES);
        return output;
    }

    public UnprotectedPacket unprotect(byte[] protectedRtcp) {
        if (protectedRtcp == null
            || protectedRtcp.length < FIXED_HEADER_BYTES + INDEX_BYTES + AUTH_TAG_BYTES) {
            throw new IllegalArgumentException("SRTCP packet too short");
        }
        var indexOffset = protectedRtcp.length - INDEX_BYTES - AUTH_TAG_BYTES;
        var encodedIndex = readInt(protectedRtcp, indexOffset);
        if ((encodedIndex & ENCRYPTED_FLAG) == 0) {
            throw new SecurityException("unencrypted SRTCP is not accepted");
        }
        var index = encodedIndex & 0x7fffffff;
        var expected = authenticationTag(protectedRtcp, indexOffset + INDEX_BYTES);
        var actual = Arrays.copyOfRange(
            protectedRtcp,
            indexOffset + INDEX_BYTES,
            protectedRtcp.length
        );
        if (!MessageDigest.isEqual(Arrays.copyOf(expected, AUTH_TAG_BYTES), actual)) {
            throw new SecurityException("invalid SRTCP authentication tag");
        }
        var output = Arrays.copyOf(protectedRtcp, indexOffset);
        cryptPayload(output, FIXED_HEADER_BYTES, output.length - FIXED_HEADER_BYTES, index);
        validateRtcp(output);
        return new UnprotectedPacket(output, index);
    }

    private void cryptPayload(byte[] packet, int offset, int length, int index) {
        if (length == 0) {
            return;
        }
        try {
            var cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(encryptionKey, "AES"),
                new IvParameterSpec(initializationVector(packet, index))
            );
            var transformed = cipher.doFinal(packet, offset, length);
            System.arraycopy(transformed, 0, packet, offset, transformed.length);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("AES-CTR unavailable", error);
        }
    }

    private byte[] authenticationTag(byte[] packet, int length) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authenticationKey, "HmacSHA1"));
            mac.update(packet, 0, length);
            return mac.doFinal();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA1 unavailable", error);
        }
    }

    private byte[] initializationVector(byte[] rtcp, int index) {
        var iv = new byte[16];
        System.arraycopy(salt, 0, iv, 0, salt.length);
        for (var i = 0; i < 4; i++) {
            iv[4 + i] ^= rtcp[4 + i];
            iv[10 + i] ^= (byte) (index >>> (24 - i * 8));
        }
        return iv;
    }

    private static void validateRtcp(byte[] packet) {
        if (packet == null || packet.length < FIXED_HEADER_BYTES || (packet[0] & 0xc0) != 0x80) {
            throw new IllegalArgumentException("invalid RTCP packet");
        }
        var packetType = Byte.toUnsignedInt(packet[1]);
        if (packetType < 192 || packetType > 223) {
            throw new IllegalArgumentException("invalid RTCP packet type");
        }
    }

    private static void validateIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("SRTCP index must be between 0 and 2^31-1");
        }
    }

    private static int readInt(byte[] source, int offset) {
        return (Byte.toUnsignedInt(source[offset]) << 24)
            | (Byte.toUnsignedInt(source[offset + 1]) << 16)
            | (Byte.toUnsignedInt(source[offset + 2]) << 8)
            | Byte.toUnsignedInt(source[offset + 3]);
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    public record UnprotectedPacket(byte[] bytes, int index) {
        public UnprotectedPacket {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
