package com.avandocmsg.messenger.common.retention;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentAnalyzerTest {

    @Test
    void isFileReference_nullContent_returnsFalse() {
        assertFalse(ContentAnalyzer.isFileReference(null));
    }

    @Test
    void isFileReference_plainText_returnsFalse() {
        assertFalse(ContentAnalyzer.isFileReference("hello world"));
    }

    @Test
    void isFileReference_filePrefix_returnsTrue() {
        assertTrue(ContentAnalyzer.isFileReference("file://abc-def"));
    }

    @Test
    void extractFileId_nullContent_returnsEmpty() {
        assertTrue(ContentAnalyzer.extractFileId(null).isEmpty());
    }

    @Test
    void extractFileId_plainText_returnsEmpty() {
        assertTrue(ContentAnalyzer.extractFileId("hello").isEmpty());
    }

    @Test
    void extractFileId_validUuid_returnsId() {
        var uuid = UUID.randomUUID().toString();
        var result = ContentAnalyzer.extractFileId("file://" + uuid);
        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().toString());
    }

    @Test
    void extractFileId_invalidUuid_returnsEmpty() {
        var result = ContentAnalyzer.extractFileId("file://not-a-uuid");
        assertTrue(result.isEmpty());
    }

    @Test
    void extractFileId_withWhitespace_trimmed() {
        var uuid = UUID.randomUUID().toString();
        var result = ContentAnalyzer.extractFileId("file://" + uuid + "  ");
        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().toString());
    }
}
