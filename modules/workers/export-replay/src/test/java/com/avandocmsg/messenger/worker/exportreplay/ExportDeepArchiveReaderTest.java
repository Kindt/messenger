package com.avandocmsg.messenger.worker.exportreplay;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportDeepArchiveReaderTest {

    @Test
    void objectKeyForMessage_matchesDeepArchiverLayout() {
        var id = UUID.randomUUID().toString();
        assertEquals("messages/" + id + ".json", ExportDeepArchiveReader.objectKeyForMessage(id));
    }
}
