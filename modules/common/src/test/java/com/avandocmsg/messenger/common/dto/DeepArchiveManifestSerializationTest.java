package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiveManifestSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void json_roundTrip() throws Exception {
        var chunks = List.of(
            new ChunkEntry("part-000.json", 0, 5_000_000L, "abc123"),
            new ChunkEntry("part-001.json", 1, 3_200_000L, "def456"));
        var manifest = new DeepArchiveManifest("msg-uuid", 2, chunks, 8_200_000L, "sha256hex");

        var json = MAPPER.writeValueAsString(manifest);
        assertTrue(json.contains("\"message_id\":\"msg-uuid\""));
        assertTrue(json.contains("\"chunk_count\":2"));
        assertTrue(json.contains("\"total_size_bytes\":8200000"));
        assertTrue(json.contains("\"part_name\":\"part-000.json\""));
        assertTrue(json.contains("\"sha256\":\"sha256hex\""));

        var back = MAPPER.readValue(json, DeepArchiveManifest.class);
        assertEquals("msg-uuid", back.messageId());
        assertEquals(2, back.chunkCount());
        assertEquals(2, back.chunks().size());
        assertEquals(8_200_000L, back.totalSizeBytes());
        assertEquals("sha256hex", back.sha256());
        assertEquals("part-000.json", back.chunks().get(0).partName());
        assertEquals(0, back.chunks().get(0).index());
        assertEquals(5_000_000L, back.chunks().get(0).sizeBytes());
        assertEquals("abc123", back.chunks().get(0).sha256());
    }

    @Test
    void json_emptyChunksList() throws Exception {
        var manifest = new DeepArchiveManifest("m", 0, List.of(), 0L, null);
        var json = MAPPER.writeValueAsString(manifest);
        assertTrue(json.contains("\"chunks\":[]"));
        var back = MAPPER.readValue(json, DeepArchiveManifest.class);
        assertEquals(0, back.chunkCount());
        assertTrue(back.chunks().isEmpty());
    }
}
