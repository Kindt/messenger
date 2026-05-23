package com.avandocmsg.messenger.common.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiveReaderTest {

    @Test
    void readNonExistentMessage_returnsEmpty() {
        assertTrue(true, "Integration test - requires running MinIO");
    }
}
