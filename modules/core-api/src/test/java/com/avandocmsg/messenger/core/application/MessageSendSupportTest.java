package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageSendSupportTest {

    @Test
    void parseAttachmentFileId_acceptsFileTypesAndE2eePrefix() {
        var fileId = UUID.randomUUID();
        assertEquals(fileId, MessageSendSupport.parseAttachmentFileId("file", fileId.toString()));
        assertEquals(fileId, MessageSendSupport.parseAttachmentFileId("e2ee-image", " " + fileId + " "));
        assertNull(MessageSendSupport.parseAttachmentFileId("text", fileId.toString()));
        assertNull(MessageSendSupport.parseAttachmentFileId("file", "not-a-uuid"));
    }
}
