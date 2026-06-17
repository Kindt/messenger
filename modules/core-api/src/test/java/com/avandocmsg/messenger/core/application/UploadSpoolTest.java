package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadSpoolTest {

    @Test
    void from_spoolsAndHashesContent() throws Exception {
        var data = "stream-upload".getBytes(StandardCharsets.UTF_8);
        try (var spool = UploadSpool.from(new ByteArrayInputStream(data), data.length, 1024).orElseThrow()) {
            assertEquals(data.length, spool.size());
            assertEquals(sha256Hex(data), spool.sha256Hex());
            try (var in = spool.open()) {
                assertArrayEquals(data, in.readAllBytes());
            }
        }
    }

    @Test
    void from_rejectsDeclaredSizeOverMax() throws IOException {
        assertTrue(UploadSpool.from(new ByteArrayInputStream(new byte[1]), 100, 50).isEmpty());
    }

    @Test
    void from_rejectsStreamOverMax() throws IOException {
        var data = new byte[64];
        assertTrue(UploadSpool.from(new ByteArrayInputStream(data), -1, 32).isEmpty());
    }

    @Test
    void from_rejectsContentLengthMismatch() throws IOException {
        var data = new byte[10];
        assertTrue(UploadSpool.from(new ByteArrayInputStream(data), 20, 1024).isEmpty());
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
