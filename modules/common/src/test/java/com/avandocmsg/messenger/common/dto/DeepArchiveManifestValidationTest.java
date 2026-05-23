package com.avandocmsg.messenger.common.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiveManifestValidationTest {

    @Test
    void chunkCount_matchesChunksSize() {
        var chunks = List.of(
            new ChunkEntry("part-000.json", 0, 100L, "a"),
            new ChunkEntry("part-001.json", 1, 200L, "b"));
        var manifest = new DeepArchiveManifest("id", 2, chunks, 300L, "s");
        assertEquals(chunks.size(), manifest.chunkCount());
    }

    @Test
    void totalSizeBytes_isSumOfChunkSizes() {
        var chunks = List.of(
            new ChunkEntry("part-000.json", 0, 100L, "a"),
            new ChunkEntry("part-001.json", 1, 200L, "b"));
        long sum = chunks.stream().mapToLong(ChunkEntry::sizeBytes).sum();
        var manifest = new DeepArchiveManifest("id", 2, chunks, sum, "s");
        assertEquals(sum, manifest.totalSizeBytes());
    }

    @Test
    void partNames_areUniqueAndSequential() {
        var chunks = List.of(
            new ChunkEntry("part-000.json", 0, 100L, "a"),
            new ChunkEntry("part-001.json", 1, 200L, "b"));
        var names = chunks.stream().map(ChunkEntry::partName).toList();
        assertTrue(names.contains("part-000.json"));
        assertTrue(names.contains("part-001.json"));
        assertEquals(2, names.stream().distinct().count());
    }
}
