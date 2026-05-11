package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.retention.ArchiveSnapshotEnvelopeDigest;
import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.avandocmsg.messenger.common.util.Sha256Hex;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionMinioSnapshotPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void minioSnapshotPayload_includesSharedEnvelope() {
        var mid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var chat = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var sender = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        var node = RetentionHotBodyJanitor.minioSnapshotPayload(MAPPER, mid, chat, sender, "text", 99L, "body", null);
        assertFalse(node.has("pass_id"));
        assertEquals(ArchiveSnapshotFormat.SNAPSHOT_VERSION, node.get(ArchiveSnapshotFormat.JSON_SNAPSHOT_VERSION).asInt());
        assertEquals(ArchiveSnapshotFormat.PRODUCER_RETENTION, node.get(ArchiveSnapshotFormat.JSON_PRODUCER).asText());
        assertEquals(mid.toString(), node.get("message_id").asText());
        assertEquals(chat.toString(), node.get("chat_id").asText());
        assertEquals(sender.toString(), node.get("sender_id").asText());
        assertEquals("text", node.get("type").asText());
        assertEquals(99L, node.get("created_at_epoch_ms").asLong());
        assertEquals("body", node.get("content").asText());
    }

    @Test
    void minioSnapshotPayload_nullType_defaultsToText() {
        var id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        var node = RetentionHotBodyJanitor.minioSnapshotPayload(
            MAPPER,
            id,
            id,
            id,
            null,
            0L,
            "",
            null
        );
        assertEquals("text", node.get("type").asText());
    }

    @Test
    void minioSnapshotPayload_withPassId_includesPassIdInSerializedJson() throws Exception {
        var mid = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        var chat = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        var sender = UUID.fromString("11111111-2222-3333-4444-555555555555");
        var pass = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var node = RetentionHotBodyJanitor.minioSnapshotPayload(MAPPER, mid, chat, sender, "text", 1L, "x", pass);
        assertEquals(pass, node.get("pass_id").asText());
        var json = MAPPER.writeValueAsString(node);
        assertTrue(json.contains("\"pass_id\":\"" + pass + "\""));
    }

    @Test
    void minioSnapshotPayload_afterEnvelopeDigest_includesSnapshotSha256() throws Exception {
        var mid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var chat = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var sender = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        var node = RetentionHotBodyJanitor.minioSnapshotPayload(MAPPER, mid, chat, sender, "text", 99L, "body", null);
        byte[] envelopeUtf8 = MAPPER.writeValueAsBytes(node);
        var expectedHex = Sha256Hex.of(envelopeUtf8);
        var attached = ArchiveSnapshotEnvelopeDigest.computeAndAttach(MAPPER, node);
        assertEquals(expectedHex, attached);
        assertTrue(node.has(ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256));
        assertEquals(expectedHex, node.get(ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256).asText());
        var finalUtf8 = MAPPER.writeValueAsBytes(node);
        assertTrue(finalUtf8.length > envelopeUtf8.length);
        var json = new String(finalUtf8, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"snapshot_sha256\":\"" + expectedHex + "\""));
    }
}
