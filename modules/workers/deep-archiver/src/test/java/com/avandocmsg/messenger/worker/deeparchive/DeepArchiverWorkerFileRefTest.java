package com.avandocmsg.messenger.worker.deeparchive;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiverWorkerFileRefTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void resolveCandidateText_prefersSearchTextFromEvent() {
        var event = new MessageWorkerEvent(
            "m1", "c1", "u1", "cl1", 1L, "text", 0, false, 10,
            "from-event",
            null
        );
        ObjectNode root = MAPPER.createObjectNode();
        root.put("searchText", "from-json");
        root.put("content", "file://aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        assertEquals("from-event", DeepArchiverWorker.resolveCandidateText(event, root));
    }

    @Test
    void resolveCandidateText_fallsBackToJsonSearchTextThenContent() {
        var event = new MessageWorkerEvent(
            "m1", "c1", "u1", "cl1", 1L, "text", 0, false, 10,
            null,
            null
        );
        ObjectNode root = MAPPER.createObjectNode();
        root.put("searchText", "from-json");
        root.put("content", "from-content");
        assertEquals("from-json", DeepArchiverWorker.resolveCandidateText(event, root));

        root.remove("searchText");
        assertEquals("from-content", DeepArchiverWorker.resolveCandidateText(event, root));
    }

    @Test
    void shouldSkipDeepArchiveForContent_trueForFileReference() {
        assertTrue(DeepArchiverWorker.shouldSkipDeepArchiveForContent("file://aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        assertFalse(DeepArchiverWorker.shouldSkipDeepArchiveForContent("plain text body"));
        assertFalse(DeepArchiverWorker.shouldSkipDeepArchiveForContent(null));
    }

    @Test
    void parseChunkSize_supportsEdgeValues() {
        assertEquals(0, DeepArchiverWorker.parseChunkSize("0"));
        assertEquals(1, DeepArchiverWorker.parseChunkSize("1"));
    }
}
