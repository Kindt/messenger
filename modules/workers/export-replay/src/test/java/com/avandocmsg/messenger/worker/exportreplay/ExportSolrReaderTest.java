package com.avandocmsg.messenger.worker.exportreplay;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportSolrReaderTest {

    @Test
    void chatIdQuery_filtersByChatId() {
        var id = UUID.randomUUID();
        var q = ExportSolrReader.chatIdQuery(id);
        assertTrue(q.startsWith("chat_id_s:"));
        assertTrue(q.contains(id.toString().replace("-", "\\-")) || q.contains(id.toString()));
    }
}
