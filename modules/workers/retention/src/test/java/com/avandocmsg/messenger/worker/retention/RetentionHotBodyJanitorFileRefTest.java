package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionHotBodyJanitorFileRefTest {

    @Test
    void shouldSkipSnapshotForContent_detectsFileReference() {
        assertTrue(RetentionHotBodyJanitor.shouldSkipSnapshotForContent("file://aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        assertFalse(RetentionHotBodyJanitor.shouldSkipSnapshotForContent("not-a-file-ref"));
        assertFalse(RetentionHotBodyJanitor.shouldSkipSnapshotForContent(null));
    }
}

