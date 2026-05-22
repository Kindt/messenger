package com.avandocmsg.messenger.worker.exportreplay;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportRetentionSnapshotReaderTest {

    @Test
    void normalizePrefix_defaultsAndTrims() {
        assertEquals("retention/body/", ExportRetentionSnapshotReader.normalizePrefix(null));
        assertEquals("retention/body/", ExportRetentionSnapshotReader.normalizePrefix(""));
        assertEquals("custom/prefix/", ExportRetentionSnapshotReader.normalizePrefix("/custom/prefix"));
    }

    @Test
    void defaultObjectKey_usesPrefix() {
        var id = UUID.randomUUID().toString();
        var key = ExportRetentionSnapshotReader.defaultObjectKey("retention/body/", id);
        assertTrue(key.endsWith(id + ".json"));
        assertTrue(key.startsWith("retention/body/"));
    }
}
