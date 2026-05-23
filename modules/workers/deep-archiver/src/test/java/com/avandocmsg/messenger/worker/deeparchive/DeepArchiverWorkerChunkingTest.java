package com.avandocmsg.messenger.worker.deeparchive;

import com.avandocmsg.messenger.common.dto.ChunkEntry;
import com.avandocmsg.messenger.common.dto.DeepArchiveManifest;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.avandocmsg.messenger.common.util.Sha256Hex;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiverWorkerChunkingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void chunkPartNames_followExpectedPattern() {
        assertEquals("part-000.json", String.format(ArchiveSnapshotFormat.CHUNK_PART_FORMAT, 0));
        assertEquals("part-001.json", String.format(ArchiveSnapshotFormat.CHUNK_PART_FORMAT, 1));
        assertEquals("part-099.json", String.format(ArchiveSnapshotFormat.CHUNK_PART_FORMAT, 99));
    }

    @Test
    void manifestJson_roundTrip() throws Exception {
        var chunks = List.of(
            new ChunkEntry("part-000.json", 0, 10, "aabb"),
            new ChunkEntry("part-001.json", 1, 20, "ccdd"));
        var manifest = new DeepArchiveManifest("msg-1", 2, chunks, 30, "eeff");

        var json = MAPPER.writeValueAsString(manifest);
        assertTrue(json.contains("\"message_id\":\"msg-1\""));
        assertTrue(json.contains("\"chunk_count\":2"));
        assertTrue(json.contains("\"total_size_bytes\":30"));

        var back = MAPPER.readValue(json, DeepArchiveManifest.class);
        assertEquals("msg-1", back.messageId());
        assertEquals(2, back.chunkCount());
        assertEquals(30, back.totalSizeBytes());
        assertEquals(2, back.chunks().size());
        assertEquals("part-000.json", back.chunks().get(0).partName());
        assertEquals("aabb", back.chunks().get(0).sha256());
    }

    @Test
    void sha256_ofJsonBytes_isConsistent() {
        var payload = "{\"messageId\":\"m1\",\"chatId\":\"c1\"}";
        var bytes = payload.getBytes(StandardCharsets.UTF_8);
        var sha = Sha256Hex.of(bytes);
        assertEquals(64, sha.length());
        var sha2 = Sha256Hex.of(bytes);
        assertEquals(sha, sha2);
    }
}
