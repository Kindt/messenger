package com.avandocmsg.messenger.common.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkManifestWriterTest {

    @Test
    void shouldWriteChunked_whenPayloadExceedsPositiveThreshold() {
        assertTrue(ChunkManifestWriter.shouldWriteChunked(2048, 2049));
        assertTrue(ChunkManifestWriter.shouldWriteChunked(1, 2));
    }

    @Test
    void shouldWriteChunked_falseWhenThresholdDisabledOrNotExceeded() {
        assertFalse(ChunkManifestWriter.shouldWriteChunked(0, 10_000));
        assertFalse(ChunkManifestWriter.shouldWriteChunked(-1, 10_000));
        assertFalse(ChunkManifestWriter.shouldWriteChunked(2048, 2048));
        assertFalse(ChunkManifestWriter.shouldWriteChunked(2048, 1000));
    }

    @Test
    void objectPrefixDir_appendsMessageIdSlash() {
        assertEquals("retention/msg-1/", ChunkManifestWriter.objectPrefixDir("retention/", "msg-1"));
        assertEquals("messages/abc/", ChunkManifestWriter.objectPrefixDir("messages/", "abc"));
    }

    @Test
    void resolveChunkSizeBytes_usesThresholdOrDefault() {
        assertEquals(ArchiveSnapshotFormat.DEFAULT_CHUNK_SIZE_BYTES, ChunkManifestWriter.resolveChunkSizeBytes(0));
        assertEquals(4096, ChunkManifestWriter.resolveChunkSizeBytes(4096));
    }
}
