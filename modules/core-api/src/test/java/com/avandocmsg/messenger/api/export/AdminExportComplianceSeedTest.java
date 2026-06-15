package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.api.files.dto.FileUploadResponse;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsMessageTypes;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminExportComplianceSeedTest {

    @Test
    void prepare_createsGroup_patchesRetention_sendsMessages() {
        var actor = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var chatService = mock(ChatService.class);
        var messageApplicationService = mock(MessageApplicationService.class);
        var chatRepository = mock(ChatRepository.class);
        var retentionRepo = mock(ChatRetentionPolicyRepository.class);

        when(chatService.createGroup(eq("export-compliance-smoke"), eq(actor), eq(List.of())))
            .thenReturn(new ChatResponse(chatId.toString(), "export-compliance-smoke", "group",
                actor.toString(), 1, false, false, null, Instant.now()));
        when(retentionRepo.upsert(eq(chatId), eq(0), isNull(), eq(false), eq(true), eq(false), eq(actor)))
            .thenReturn(true);
        when(messageApplicationService.sendMessage(eq(chatId), eq(actor), argThat(req ->
                req != null && MlsMessageTypes.SCHEME_LEGACY.equals(req.e2eeScheme())), isNull()))
            .thenReturn(new MessageResponse("m1", chatId.toString(), actor.toString(), "text", "x", null,
                false, Instant.now(), null, null, null))
            .thenReturn(new MessageResponse("m2", chatId.toString(), actor.toString(), "text", "x", null,
                false, Instant.now(), null, null, null))
            .thenReturn(new MessageResponse("m3", chatId.toString(), actor.toString(), "text", "x", null,
                false, Instant.now(), null, null, null));

        var seed = new AdminExportComplianceSeed(
            chatService, messageApplicationService, mock(FileService.class), chatRepository, retentionRepo);
        var result = seed.prepare(actor, new AdminExportCompliancePrepRequest(null, true, 3, false, null));

        assertEquals(chatId.toString(), result.response().chatId());
        assertEquals(3, result.response().messageIds().size());
        assertTrue(result.response().retentionPatched());
    }

    @Test
    void prepare_includeFile_uploadsAndSendsFileMessage() {
        var actor = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var fileId = UUID.randomUUID().toString();
        var chatService = mock(ChatService.class);
        var messageApplicationService = mock(MessageApplicationService.class);
        var fileService = mock(FileService.class);
        var chatRepository = mock(ChatRepository.class);
        var retentionRepo = mock(ChatRetentionPolicyRepository.class);

        when(chatService.createGroup(eq("export-compliance-smoke"), eq(actor), eq(List.of())))
            .thenReturn(new ChatResponse(chatId.toString(), "export-compliance-smoke", "group",
                actor.toString(), 1, false, false, null, Instant.now()));
        when(retentionRepo.upsert(eq(chatId), eq(0), isNull(), eq(false), eq(true), eq(false), eq(actor)))
            .thenReturn(true);
        when(messageApplicationService.sendMessage(eq(chatId), eq(actor), argThat(req ->
                req != null && MlsMessageTypes.SCHEME_LEGACY.equals(req.e2eeScheme())), isNull()))
            .thenReturn(new MessageResponse("m1", chatId.toString(), actor.toString(), "text", "x", null,
                false, Instant.now(), null, null, null))
            .thenReturn(new MessageResponse("mf", chatId.toString(), actor.toString(), "file", fileId, null,
                false, Instant.now(), null, null, fileId));
        when(fileService.upload(any(), eq("smoke.txt"), eq("text/plain"), anyLong(), eq(actor)))
            .thenReturn(new FileUploadResponse(fileId, "smoke.txt", "text/plain", 12, "/api/v1/files/" + fileId + "/download"));

        var seed = new AdminExportComplianceSeed(chatService, messageApplicationService, fileService, chatRepository, retentionRepo);
        var result = seed.prepare(actor, new AdminExportCompliancePrepRequest(null, true, 1, true, "smoke.txt"));

        assertEquals(fileId, result.response().fileId());
        assertEquals("mf", result.response().fileMessageId());
        assertEquals(2, result.response().messageIds().size());
    }

    @Test
    void prepare_existingChat_requiresExists() {
        var actor = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var chatRepository = mock(ChatRepository.class);
        when(chatRepository.chatExists(chatId)).thenReturn(false);

        var seed = new AdminExportComplianceSeed(
            mock(ChatService.class),
            mock(MessageApplicationService.class),
            mock(FileService.class),
            chatRepository,
            mock(ChatRetentionPolicyRepository.class));

        assertThrows(IllegalArgumentException.class,
            () -> seed.prepare(actor, new AdminExportCompliancePrepRequest(chatId.toString(), false, 3, null, null)));
    }

    @Test
    void prepare_rejectsMessageCountOutOfRange() {
        var seed = new AdminExportComplianceSeed(
            mock(ChatService.class),
            mock(MessageApplicationService.class),
            mock(FileService.class),
            mock(ChatRepository.class),
            mock(ChatRetentionPolicyRepository.class));

        assertThrows(IllegalArgumentException.class,
            () -> seed.prepare(UUID.randomUUID(), new AdminExportCompliancePrepRequest(null, true, 0, null, null)));
    }
}
