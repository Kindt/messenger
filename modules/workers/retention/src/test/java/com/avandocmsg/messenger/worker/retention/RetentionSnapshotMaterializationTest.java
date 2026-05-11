package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionSnapshotMaterializationTest {

    @Test
    void utf8ByteLength_matchesEncodedSize_forSamples() {
        assertEquals(0, RetentionSnapshotMaterialization.utf8ByteLength(null));
        assertEquals(0, RetentionSnapshotMaterialization.utf8ByteLength(""));
        assertEquals(3, RetentionSnapshotMaterialization.utf8ByteLength("abc"));
        var cyrillic = "привет";
        assertEquals(cyrillic.getBytes(StandardCharsets.UTF_8).length, RetentionSnapshotMaterialization.utf8ByteLength(cyrillic));
        var emoji = "a\uD83D\uDE00b";
        assertEquals(emoji.getBytes(StandardCharsets.UTF_8).length, RetentionSnapshotMaterialization.utf8ByteLength(emoji));
    }

    @Test
    void shouldUseTempFile_strictlyGreaterThanThreshold_andRequiresPositiveThreshold() {
        assertFalse(RetentionSnapshotMaterialization.shouldUseTempFile(0L, 1_000_000));
        assertFalse(RetentionSnapshotMaterialization.shouldUseTempFile(100L, 100));
        assertFalse(RetentionSnapshotMaterialization.shouldUseTempFile(100L, 50));
        assertTrue(RetentionSnapshotMaterialization.shouldUseTempFile(100L, 101));
        assertTrue(RetentionSnapshotMaterialization.shouldUseTempFile(1L, 2));
    }
}
