package com.avandocmsg.messenger.common.dto;

import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionAppliedEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void json_roundTrip_snakeCaseAndOmitNullStorageKey() throws Exception {
        var evt = RetentionAppliedEvent.hotBodyCleared("mid", "cid", null, 42, 1_700_000_000_000L);
        var json = MAPPER.writeValueAsString(evt);
        assertTrue(json.contains("\"message_id\":\"mid\""));
        assertTrue(json.contains("\"action\":\"hot_body_cleared\""));
        assertTrue(json.contains("\"snapshot_version\":" + ArchiveSnapshotFormat.SNAPSHOT_VERSION));
        assertFalse(json.contains("storage_object_key"));
        assertFalse(json.contains("pass_id"));
        assertFalse(json.contains("snapshot_sha256"));
        var back = MAPPER.readValue(json, RetentionAppliedEvent.class);
        assertEquals("mid", back.messageId());
        assertEquals(RetentionAppliedEvent.ACTION_HOT_BODY_CLEARED, back.action());
        assertEquals(42, back.clearedContentUtf8Bytes());
        assertEquals(ArchiveSnapshotFormat.SNAPSHOT_VERSION, back.snapshotVersion());
        assertNull(back.passId());
        assertNull(back.snapshotSha256());
    }

    @Test
    void json_deserialize_missingSnapshotVersion_defaultsToOne() throws Exception {
        var json =
            "{\"message_id\":\"m\",\"chat_id\":\"c\",\"action\":\"hot_body_cleared\","
                + "\"applied_at_epoch_ms\":0,\"cleared_content_utf8_bytes\":5}";
        var back = MAPPER.readValue(json, RetentionAppliedEvent.class);
        assertEquals(ArchiveSnapshotFormat.SNAPSHOT_VERSION, back.snapshotVersion());
        assertNull(back.passId());
        assertNull(back.snapshotSha256());
    }

    @Test
    void json_roundTrip_withPassId_snakeCase() throws Exception {
        var evt = RetentionAppliedEvent.hotBodyCleared(
            "mid",
            "cid",
            "key/obj.json",
            10,
            1_700_000_000_000L,
            ArchiveSnapshotFormat.SNAPSHOT_VERSION,
            "9b2e4f1a-6c0d-4a8e-9f3b-1a2b3c4d5e6f"
        );
        var json = MAPPER.writeValueAsString(evt);
        assertTrue(json.contains("\"pass_id\":\"9b2e4f1a-6c0d-4a8e-9f3b-1a2b3c4d5e6f\""));
        var back = MAPPER.readValue(json, RetentionAppliedEvent.class);
        assertEquals("9b2e4f1a-6c0d-4a8e-9f3b-1a2b3c4d5e6f", back.passId());
        assertEquals("mid", back.messageId());
        assertNull(back.snapshotSha256());
    }

    @Test
    void json_roundTrip_withSnapshotSha256_snakeCase() throws Exception {
        var digest =
            "015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862";
        var evt = RetentionAppliedEvent.hotBodyCleared(
            "mid",
            "cid",
            "retention/body/mid.json",
            10,
            1_700_000_000_000L,
            ArchiveSnapshotFormat.SNAPSHOT_VERSION,
            "9b2e4f1a-6c0d-4a8e-9f3b-1a2b3c4d5e6f",
            digest
        );
        var json = MAPPER.writeValueAsString(evt);
        assertTrue(json.contains("\"snapshot_sha256\":\"" + digest + "\""));
        var back = MAPPER.readValue(json, RetentionAppliedEvent.class);
        assertEquals(digest, back.snapshotSha256());
        assertEquals("9b2e4f1a-6c0d-4a8e-9f3b-1a2b3c4d5e6f", back.passId());
    }

    @Test
    void json_deserialize_missingSnapshotSha256_defaultsToNull() throws Exception {
        var json =
            "{\"message_id\":\"m\",\"chat_id\":\"c\",\"action\":\"hot_body_cleared\","
                + "\"applied_at_epoch_ms\":0,\"cleared_content_utf8_bytes\":5,\"snapshot_version\":1,"
                + "\"pass_id\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}";
        var back = MAPPER.readValue(json, RetentionAppliedEvent.class);
        assertNull(back.snapshotSha256());
    }

    @Test
    void json_deserialize_explicitZero() throws Exception {
        var json =
            "{\"message_id\":\"m\",\"chat_id\":\"c\",\"action\":\"hot_body_cleared\","
                + "\"applied_at_epoch_ms\":0,\"cleared_content_utf8_bytes\":5,\"snapshot_version\":0}";
        var back = MAPPER.readValue(json, RetentionAppliedEvent.class);
        assertEquals(0, back.snapshotVersion());
        assertNull(back.passId());
        assertNull(back.snapshotSha256());
    }
}
