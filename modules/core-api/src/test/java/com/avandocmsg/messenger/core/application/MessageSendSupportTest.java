package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsMessageTypes;
import org.junit.jupiter.api.Test;

import java.util.Base64;
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

    @Test
    void typeForSend_marksClientEncryptedMlsAsE2eeType() {
        var ciphertext = Base64.getEncoder().encodeToString(new byte[40]);
        var request = new SendMessageRequest(
            "text", ciphertext, null, null, null, null, null, MlsMessageTypes.SCHEME_MLS, null);

        assertEquals("e2ee-text", MessageSendSupport.typeForSend(request, null));
    }
}
