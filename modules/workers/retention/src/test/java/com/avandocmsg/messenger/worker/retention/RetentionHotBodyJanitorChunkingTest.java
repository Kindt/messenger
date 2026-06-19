package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionHotBodyJanitorChunkingTest {

    @Test
    void shouldWriteChunkedSnapshot_whenPayloadExceedsPositiveThreshold() {
        assertTrue(RetentionHotBodyJanitor.shouldWriteChunkedSnapshot(2048, 2049));
        assertTrue(RetentionHotBodyJanitor.shouldWriteChunkedSnapshot(1, 2));
    }

    @Test
    void shouldSkipSnapshotForContent_whenFileReference() {
        assertTrue(RetentionHotBodyJanitor.shouldSkipSnapshotForContent("file://aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        assertFalse(RetentionHotBodyJanitor.shouldSkipSnapshotForContent("hello world"));
    }
}
