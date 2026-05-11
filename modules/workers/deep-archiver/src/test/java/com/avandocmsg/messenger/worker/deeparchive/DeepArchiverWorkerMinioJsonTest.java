package com.avandocmsg.messenger.worker.deeparchive;

import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.avandocmsg.messenger.common.util.Sha256Hex;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiverWorkerMinioJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void minioSnapshotBytes_addsEnvelope_keepsEventFields() throws Exception {
        String nats = """
            {"messageId":"m1","chatId":"c1","senderId":"s1","clientMsgId":null,"createdAtEpochMs":1,"type":"text","flags":0,"encrypted":false}\
            """.trim();
        ObjectNode root = (ObjectNode) MAPPER.readTree(nats);
        root.put(ArchiveSnapshotFormat.JSON_SNAPSHOT_VERSION, ArchiveSnapshotFormat.SNAPSHOT_VERSION);
        root.put(ArchiveSnapshotFormat.JSON_PRODUCER, ArchiveSnapshotFormat.PRODUCER_DEEP_ARCHIVER);
        byte[] envelopeUtf8 = MAPPER.writeValueAsBytes(root);
        var expectedHex = Sha256Hex.of(envelopeUtf8);

        byte[] bytes = DeepArchiverWorker.minioSnapshotBytesFromNatsJson(nats, MAPPER);
        var tree = MAPPER.readTree(bytes);
        assertEquals(ArchiveSnapshotFormat.SNAPSHOT_VERSION, tree.get(ArchiveSnapshotFormat.JSON_SNAPSHOT_VERSION).asInt());
        assertEquals(ArchiveSnapshotFormat.PRODUCER_DEEP_ARCHIVER, tree.get(ArchiveSnapshotFormat.JSON_PRODUCER).asText());
        assertEquals("m1", tree.get("messageId").asText());
        assertEquals("c1", tree.get("chatId").asText());
        assertFalse(tree.get("encrypted").asBoolean());
        assertTrue(tree.has(ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256));
        assertEquals(expectedHex, tree.get(ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256).asText());
        var json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"snapshot_sha256\":\"" + expectedHex + "\""));
    }
}
