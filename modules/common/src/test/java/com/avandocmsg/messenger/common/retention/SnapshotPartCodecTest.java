package com.avandocmsg.messenger.common.retention;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotPartCodecTest {

    @Test
    void roundtrip_zstd_preservesPlainJson() {
        var plain = ("{\"chat\":\"x\",\"messages\":[" + "\"m\",".repeat(500) + "\"end\"]}")
            .getBytes(StandardCharsets.UTF_8);
        var stored = SnapshotPartCodec.compress(SnapshotCompression.ZSTD, plain, 3);
        assertTrue(stored.length < plain.length, "zstd should shrink repetitive JSON");
        assertArrayEquals(plain, SnapshotPartCodec.decompress(stored));
    }

    @Test
    void legacyPlainJson_passesThrough() {
        var plain = "{\"legacy\":true}".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(plain, SnapshotPartCodec.decompress(plain));
    }

    @Test
    void bytesSaved_nonNegative() {
        var plain = ("{\"payload\":\"" + "x".repeat(2000) + "\"}").getBytes(StandardCharsets.UTF_8);
        var stored = SnapshotPartCodec.compress(SnapshotCompression.ZSTD, plain, 3);
        var saved = SnapshotPartCodec.bytesSaved(plain.length, stored.length);
        assertTrue(saved >= 0);
        assertEquals(Math.max(0, plain.length - stored.length), saved);
    }
}
