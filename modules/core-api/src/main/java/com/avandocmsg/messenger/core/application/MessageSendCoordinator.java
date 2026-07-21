package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Hexagonal write-path: persist message + pipeline NATS + unread cache invalidation.
 * ACL gates live in {@link MessageApplicationService#sendBlockedReason}.
 */
public final class MessageSendCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MessageSendCoordinator.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatRepositoryPort chatRepositoryPort;
    private final MlsService mlsService;
    private final MlsMigrationService mlsMigrationService;
    private final NatsOutboundPort natsOutbound;
    private final UuidGenerator uuidGenerator;
    private final ReadCachePort readCachePort;
    private final MessageMentionCoordinator mentionCoordinator;

    /** Persistence / MLS ports for send path. */
    public record Ports(
        MessageRepositoryPort messageRepositoryPort,
        ChatRepositoryPort chatRepositoryPort,
        MlsService mlsService,
        MlsMigrationService mlsMigrationService
    ) {}

    /** Outbound / cache collaborators for send path. */
    public record SideEffects(
        NatsOutboundPort natsOutbound,
        UuidGenerator uuidGenerator,
        ReadCachePort readCachePort,
        MessageMentionCoordinator mentionCoordinator
    ) {}

    /** Constructor dependencies for {@link MessageSendCoordinator}. */
    public record Dependencies(Ports ports, SideEffects sideEffects) {}

    public MessageSendCoordinator(Dependencies deps) {
        var ports = deps.ports();
        var side = deps.sideEffects();
        this.messageRepositoryPort = ports.messageRepositoryPort();
        this.chatRepositoryPort = ports.chatRepositoryPort();
        this.mlsService = ports.mlsService();
        this.mlsMigrationService = ports.mlsMigrationService();
        this.natsOutbound = side.natsOutbound();
        this.uuidGenerator = side.uuidGenerator();
        this.readCachePort = side.readCachePort() != null
            ? side.readCachePort()
            : com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter.INSTANCE;
        this.mentionCoordinator = side.mentionCoordinator();
    }

    public MessageSendCoordinator(
        MessageRepositoryPort messageRepositoryPort,
        ChatRepositoryPort chatRepositoryPort,
        MlsService mlsService,
        MlsMigrationService mlsMigrationService,
        NatsOutboundPort natsOutbound,
        UuidGenerator uuidGenerator,
        ReadCachePort readCachePort
    ) {
        this(new Dependencies(
            new Ports(messageRepositoryPort, chatRepositoryPort, mlsService, mlsMigrationService),
            new SideEffects(natsOutbound, uuidGenerator, readCachePort, null)));
    }

    /** Kept for composition roots that still pass flat args. */
    public MessageSendCoordinator( // NOSONAR java:S107 — flat overload for CoreApiComposition; prefer Dependencies
        MessageRepositoryPort messageRepositoryPort,
        ChatRepositoryPort chatRepositoryPort,
        MlsService mlsService,
        MlsMigrationService mlsMigrationService,
        NatsOutboundPort natsOutbound,
        UuidGenerator uuidGenerator,
        ReadCachePort readCachePort,
        MessageMentionCoordinator mentionCoordinator
    ) {
        this(new Dependencies(
            new Ports(messageRepositoryPort, chatRepositoryPort, mlsService, mlsMigrationService),
            new SideEffects(natsOutbound, uuidGenerator, readCachePort, mentionCoordinator)));
    }

    public MessageResponse send(UUID chatId, UUID senderId, SendMessageRequest request, UUID replyToMsgId) {
        var attachmentFileId = MessageSendSupport.parseAttachmentFileId(request.type(), request.content());
        var plainForMentions = request.content();
        var content = request.content();
        if (MessageSendSupport.usesMlsScheme(request) && mlsMigrationService != null) {
            mlsMigrationService.migrateToMls(chatId);
        }
        var encrypted = MessageSendSupport.shouldServerEncrypt(request)
            ? mlsService.encrypt(chatId, content)
            : null;
        if (encrypted != null) {
            content = MessageSendSupport.combinedCiphertextBase64(encrypted);
        }
        var id = uuidGenerator.randomUuid();
        UUID threadId = null;
        if (request.threadId() != null && !request.threadId().isBlank()) {
            try {
                threadId = UUID.fromString(request.threadId().trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        var inserted = messageRepositoryPort.insert(new MessageInsert(
            MessageId.of(id),
            ChatId.of(chatId),
            UserId.of(senderId),
            MessageSendSupport.typeForSend(request, encrypted),
            content,
            replyToMsgId,
            threadId,
            request.clientMsgId(),
            request.visibilityTtlSeconds(),
            attachmentFileId,
            voiceDurationMs(request)));
        if (inserted.isEmpty()) {
            return null;
        }
        var msg = MessageDomainMapper.toResponse(inserted.get());
        publishSendEvent(msg, request.clientMsgId());
        if (mentionCoordinator != null) {
            var createdMs = msg.createdAt() != null ? msg.createdAt().toEpochMilli() : System.currentTimeMillis();
            mentionCoordinator.afterMessageSent(chatId, id, senderId, plainForMentions, createdMs);
        }
        invalidateUnreadForChatMembers(chatId, senderId);
        return msg;
    }

    /**
     * Copy an existing hot-row message into another chat (forward / vault).
     * ACL gates live in {@link MessageApplicationService#forwardMessage}.
     */
    public MessageResponse forward(UUID sourceChatId, UUID msgId, UUID userId, UUID targetChatId) {
        var source = messageRepositoryPort.findById(MessageId.of(msgId)).orElse(null);
        if (source == null || !source.chatId().equals(ChatId.of(sourceChatId)) || source.deleted()) {
            return null;
        }
        UUID attachmentFileId = null;
        if (source.attachmentFileId() != null && !source.attachmentFileId().isBlank()) {
            try {
                attachmentFileId = UUID.fromString(source.attachmentFileId());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        var newId = uuidGenerator.randomUuid();
        var inserted = messageRepositoryPort.insert(new MessageInsert(
            MessageId.of(newId),
            ChatId.of(targetChatId),
            UserId.of(userId),
            source.type(),
            source.content(),
            null,
            null,
            null,
            null,
            attachmentFileId,
            null));
        if (inserted.isEmpty()) {
            return null;
        }
        var msg = MessageDomainMapper.toResponse(inserted.get());
        publishSendEvent(msg, null);
        invalidateUnreadForChatMembers(targetChatId, userId);
        return msg;
    }

    private static Integer voiceDurationMs(SendMessageRequest request) {
        if (request == null || request.type() == null || !"voice".equalsIgnoreCase(request.type())) {
            return null;
        }
        return request.durationMs();
    }

    public void publishSendEvent(MessageResponse msg, String clientMsgId) {
        try {
            var event = new MessageSendEvent(
                msg.id(), msg.chatId(), msg.senderId(), msg.type(),
                msg.content(), clientMsgId,
                msg.createdAt() != null ? msg.createdAt().toEpochMilli() : null,
                msg.replyToMsgId(),
                msg.attachmentFileId(),
                msg.visibilityTtlSeconds(),
                msg.threadId());
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publishPipelineMessageSend(data, msg.senderId());
        } catch (Exception e) {
            log.warn("Failed to publish msg.send event for {}", msg.id(), e);
        }
    }

    private void invalidateUnreadForChatMembers(UUID chatId, UUID senderId) {
        if (!readCachePort.enabled()) {
            return;
        }
        for (var memberId : chatRepositoryPort.listMemberUserIds(ChatId.of(chatId))) {
            if (!memberId.value().equals(senderId)) {
                ReadCacheCoordinator.invalidateChatUnread(readCachePort, memberId.value());
            }
        }
    }
}
