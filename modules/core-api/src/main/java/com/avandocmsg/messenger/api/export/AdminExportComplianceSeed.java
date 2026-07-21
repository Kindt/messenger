package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepResponse;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsMessageTypes;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Seeds hot-body retention candidates and compliance-friendly chat retention policy. */
public final class AdminExportComplianceSeed {

    /** Plaintext retention/export smokes must not use server MLS (export-replay skips e2ee-* types). */
    private static final String COMPLIANCE_PLAINTEXT_SCHEME = MlsMessageTypes.SCHEME_LEGACY;

    private final ChatService chatService;
    private final MessageApplicationService messageApplicationService;
    private final FileService fileService;
    private final ChatPersistencePort chatPersistencePort;
    private final ChatRetentionPolicyPort chatRetentionPolicyPort;

    public AdminExportComplianceSeed(
        ChatService chatService,
        MessageApplicationService messageApplicationService,
        FileService fileService,
        ChatPersistencePort chatPersistencePort,
        ChatRetentionPolicyPort chatRetentionPolicyPort
    ) {
        this.chatService = chatService;
        this.messageApplicationService = messageApplicationService;
        this.fileService = fileService;
        this.chatPersistencePort = chatPersistencePort;
        this.chatRetentionPolicyPort = chatRetentionPolicyPort;
    }

    public PrepResult prepare(UUID actorId, AdminExportCompliancePrepRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("body_required");
        }
        int count = resolveMessageCount(body);
        UUID chatId = resolveChatId(actorId, body);
        applyRetention(chatId, actorId);

        var messageIds = seedTextMessages(chatId, actorId, count);
        String fileId = null;
        String fileMessageId = null;
        if (Boolean.TRUE.equals(body.includeFile())) {
            var seeded = seedFileAttachment(chatId, actorId, body.fileName());
            fileId = seeded.fileId();
            fileMessageId = seeded.fileMessageId();
            messageIds.add(fileMessageId);
        }

        return new PrepResult(
            new AdminExportCompliancePrepResponse(
                chatId.toString(),
                List.copyOf(messageIds),
                true,
                fileId,
                fileMessageId
            )
        );
    }

    public record PrepResult(AdminExportCompliancePrepResponse response) {}

    private static int resolveMessageCount(AdminExportCompliancePrepRequest body) {
        int count = body.messageCount() != null ? body.messageCount() : 3;
        if (count < 1 || count > 20) {
            throw new IllegalArgumentException("message_count_range");
        }
        return count;
    }

    private UUID resolveChatId(UUID actorId, AdminExportCompliancePrepRequest body) {
        UUID chatId = parseOptionalUuid(body.chatId());
        boolean createGroup = body.createGroup() == null || body.createGroup();
        if (chatId != null) {
            if (!chatPersistencePort.chatExists(chatId)) {
                throw new IllegalArgumentException("chat_not_found");
            }
            return chatId;
        }
        if (!createGroup) {
            throw new IllegalArgumentException("chat_id_or_create_group");
        }
        var chat = chatService.createGroup("export-compliance-smoke", actorId, List.of());
        if (chat == null || chat.id() == null) {
            throw new IllegalStateException("create_group_failed");
        }
        return UUID.fromString(chat.id());
    }

    private void applyRetention(UUID chatId, UUID actorId) {
        boolean retentionOk = chatRetentionPolicyPort.upsert(
            chatId,
            0,
            null,
            false,
            true,
            false,
            actorId
        );
        if (!retentionOk) {
            throw new IllegalStateException("retention_patch_failed");
        }
    }

    private List<String> seedTextMessages(UUID chatId, UUID actorId, int count) {
        var messageIds = new ArrayList<String>();
        for (int i = 1; i <= count; i++) {
            var content = "export-compliance seed " + i + " " + Instant.now();
            var sent = messageApplicationService.sendMessage(
                chatId,
                actorId,
                new SendMessageRequest("text", content, null, null, null, null, null, COMPLIANCE_PLAINTEXT_SCHEME, null),
                null
            );
            if (sent == null) {
                throw new IllegalStateException("message_send_failed");
            }
            messageIds.add(sent.id());
        }
        return messageIds;
    }

    private record SeededFile(String fileId, String fileMessageId) {}

    private SeededFile seedFileAttachment(UUID chatId, UUID actorId, String fileName) {
        var filename = fileName != null && !fileName.isBlank()
            ? fileName.trim()
            : "compliance-smoke.txt";
        var payload = ("export-compliance attachment " + Instant.now())
            .getBytes(StandardCharsets.UTF_8);
        var uploaded = fileService.upload(
            new ByteArrayInputStream(payload),
            filename,
            "text/plain",
            payload.length,
            actorId
        );
        if (uploaded == null || uploaded.id() == null) {
            throw new IllegalStateException("file_upload_failed");
        }
        var fileMsg = messageApplicationService.sendMessage(
            chatId,
            actorId,
            new SendMessageRequest("file", uploaded.id(), null, null, null, null, null, COMPLIANCE_PLAINTEXT_SCHEME, null),
            null
        );
        if (fileMsg == null) {
            throw new IllegalStateException("file_message_failed");
        }
        return new SeededFile(uploaded.id(), fileMsg.id());
    }

    private static UUID parseOptionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid_chat_id");
        }
    }
}
