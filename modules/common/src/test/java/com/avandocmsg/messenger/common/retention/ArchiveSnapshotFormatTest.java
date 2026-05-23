package com.avandocmsg.messenger.common.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveSnapshotFormatTest {

    @Test
    void constants_stableInteropContract() {
        assertEquals(1, ArchiveSnapshotFormat.SNAPSHOT_VERSION);
        assertEquals("retention-worker", ArchiveSnapshotFormat.PRODUCER_RETENTION);
        assertEquals("deep-archiver", ArchiveSnapshotFormat.PRODUCER_DEEP_ARCHIVER);
        assertEquals("snapshot_version", ArchiveSnapshotFormat.JSON_SNAPSHOT_VERSION);
        assertEquals("producer", ArchiveSnapshotFormat.JSON_PRODUCER);
        assertEquals("manifest.json", ArchiveSnapshotFormat.CHUNK_MANIFEST_FILENAME);
        assertEquals("part-", ArchiveSnapshotFormat.CHUNK_PART_PREFIX);
        assertEquals("part-%03d.json", ArchiveSnapshotFormat.CHUNK_PART_FORMAT);
        assertEquals(10 * 1024 * 1024, ArchiveSnapshotFormat.DEFAULT_CHUNK_SIZE_BYTES);
    }
}
