package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RetentionHotBodyJanitorNoChunkingTest {

    @Test
    void shouldWriteChunkedSnapshot_falseWhenThresholdDisabledOrNotExceeded() {
        assertFalse(RetentionHotBodyJanitor.shouldWriteChunkedSnapshot(0, 10_000));
        assertFalse(RetentionHotBodyJanitor.shouldWriteChunkedSnapshot(-1, 10_000));
        assertFalse(RetentionHotBodyJanitor.shouldWriteChunkedSnapshot(2048, 2048));
        assertFalse(RetentionHotBodyJanitor.shouldWriteChunkedSnapshot(2048, 1000));
    }
}
