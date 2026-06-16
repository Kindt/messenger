package com.avandocmsg.messenger.common.retention;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Wire format for compressed snapshot parts: magic {@code KDA1} + payload (gzip or zstd).
 * Legacy parts without magic are returned as-is.
 */
public final class SnapshotPartCodec {

    static final byte[] MAGIC = new byte[] {'K', 'D', 'A', '1'};

    private SnapshotPartCodec() {
    }

    public static byte[] compress(SnapshotCompression mode, byte[] plain, int zstdLevel) {
        if (mode == null || mode == SnapshotCompression.NONE || plain == null) {
            return plain;
        }
        return switch (mode) {
            case GZIP -> wrapMagic(MAGIC, gzip(plain));
            case ZSTD -> wrapMagic(MAGIC, Zstd.compress(plain, zstdLevel));
            case NONE -> plain;
        };
    }

    public static byte[] decompress(byte[] stored) {
        if (stored == null || stored.length < MAGIC.length + 1) {
            return stored;
        }
        if (!hasMagic(stored)) {
            return stored;
        }
        var payload = slicePayload(stored);
        if (payload.length == 0) {
            return stored;
        }
        if (looksLikeZstd(payload)) {
            long size = Zstd.getFrameContentSize(payload);
            if (size <= 0 || size > Integer.MAX_VALUE) {
                throw new IllegalStateException("invalid zstd payload size");
            }
            byte[] out = new byte[(int) size];
            Zstd.decompress(out, payload);
            return out;
        }
        return gunzip(payload);
    }

    public static long bytesSaved(int plainSize, int storedSize) {
        return Math.max(0, (long) plainSize - storedSize);
    }

    private static boolean hasMagic(byte[] stored) {
        for (int i = 0; i < MAGIC.length; i++) {
            if (stored[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] slicePayload(byte[] stored) {
        var payload = new byte[stored.length - MAGIC.length];
        System.arraycopy(stored, MAGIC.length, payload, 0, payload.length);
        return payload;
    }

    private static byte[] wrapMagic(byte[] magic, byte[] payload) {
        var out = new byte[magic.length + payload.length];
        System.arraycopy(magic, 0, out, 0, magic.length);
        System.arraycopy(payload, 0, out, magic.length, payload.length);
        return out;
    }

    private static boolean looksLikeZstd(byte[] payload) {
        return payload.length >= 4 && (payload[0] & 0xFF) == 0x28 && (payload[1] & 0xFF) == 0xB5
            && (payload[2] & 0xFF) == 0x2F && (payload[3] & 0xFF) == 0xFD;
    }

    private static byte[] gzip(byte[] plain) {
        try {
            var out = new ByteArrayOutputStream(plain.length / 2 + 32);
            try (var gzip = new GZIPOutputStream(out)) {
                gzip.write(plain);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("gzip compress failed", e);
        }
    }

    private static byte[] gunzip(byte[] compressed) {
        try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
             var out = new ByteArrayOutputStream(compressed.length * 2)) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("gzip decompress failed", e);
        }
    }
}
