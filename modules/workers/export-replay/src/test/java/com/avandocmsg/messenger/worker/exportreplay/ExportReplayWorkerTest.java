package com.avandocmsg.messenger.worker.exportreplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportReplayWorkerTest {

    @Test
    void isE2eeEnvelopeType_detectsMlsStoredTypes() {
        assertTrue(ExportReplayWorker.isE2eeEnvelopeType("e2ee-text"));
        assertTrue(ExportReplayWorker.isE2eeEnvelopeType("e2ee-image"));
        assertFalse(ExportReplayWorker.isE2eeEnvelopeType("text"));
        assertFalse(ExportReplayWorker.isE2eeEnvelopeType(null));
        assertFalse(ExportReplayWorker.isE2eeEnvelopeType("e2ee"));
    }

    @Test
    void collectFileIdsFromText_findsUuidInUrlAndPlain() {
        var id1 = "aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee";
        var id2 = "11111111-2222-4333-8444-555555555555";
        Set<UUID> sink = new LinkedHashSet<>();
        var text = "See https://x/api/v1/files/" + id1 + "/download and " + id2;
        assertFalse(ExportReplayWorker.collectFileIdsFromText(text, sink, 100));
        assertEquals(2, sink.size());
        assertTrue(sink.contains(UUID.fromString(id1)));
        assertTrue(sink.contains(UUID.fromString(id2)));
    }

    @Test
    void collectFileIdsFromText_truncatesWhenCapReached() {
        var id1 = "aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee";
        var id2 = "11111111-2222-4333-8444-555555555555";
        Set<UUID> sink = new LinkedHashSet<>();
        var text = id1 + " " + id2;
        assertTrue(ExportReplayWorker.collectFileIdsFromText(text, sink, 1));
        assertEquals(1, sink.size());
    }

    @Test
    void tryAddUuidString_respectsCap() {
        Set<UUID> sink = new LinkedHashSet<>();
        var id = UUID.randomUUID();
        assertFalse(ExportReplayWorker.tryAddUuidString(id.toString(), sink, 2));
        assertEquals(1, sink.size());
        assertTrue(ExportReplayWorker.tryAddUuidString(UUID.randomUUID().toString(), sink, 1));
        assertEquals(1, sink.size());
    }

    @Test
    void collectFromJson_findsNestedFileUuid() throws Exception {
        var id = "aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee";
        var tree = new ObjectMapper().readTree("{\"body\":{\"url\":\"/api/v1/files/" + id + "/download\"}}");
        Set<UUID> sink = new LinkedHashSet<>();
        assertFalse(ExportSnapshotFileIdCollector.collectFromJson(tree, sink, 50));
        assertEquals(1, sink.size());
        assertTrue(sink.contains(UUID.fromString(id)));
    }

    @Test
    void buildMessagesSql_appliesTtlWhenEnabled() {
        assertTrue(ExportReplayWorker.buildMessagesSql(true).contains(ExportReplayWorker.SQL_MSG_VISIBILITY_TTL_VISIBLE));
        assertFalse(ExportReplayWorker.buildMessagesSql(false).contains(ExportReplayWorker.SQL_MSG_VISIBILITY_TTL_VISIBLE));
    }
}
