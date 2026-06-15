package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatRepository chatRepository;
    private final MlsService mlsService;
    private final MlsMigrationService mlsMigrationService;
    private final NatsOutboundPort natsOutbound;
    private final UuidGenerator uuidGenerator;
    private final ReadCachePort readCachePort;

    public MessageSendCoordinator(
        MessageRepositoryPort messageRepositoryPort,
        ChatRepository chatRepository,
        MlsService mlsService,
        MlsMigrationService mlsMigrationService,
        NatsOutboundPort natsOutbound,
        UuidGenerator uuidGenerator,
        ReadCachePort readCachePort
    ) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatRepository = chatRepository;
        this.mlsService = mlsService;
        this.mlsMigrationService = mlsMigrationService;
        this.natsOutbound = natsOutbound;
        this.uuidGenerator = uuidGenerator;
        this.readCachePort = readCachePort != null ? readCachePort : com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter.INSTANCE;
    }

    public MessageResponse send(UUID chatId, UUID senderId, SendMessageRequest request, UUID replyToMsgId) {
        var attachmentFileId = MessageSendSupport.parseAttachmentFileId(request.type(), request.content());
        var content = request.content();
        if (MessageSendSupport.usesMlsScheme(request) && mlsMigrationService != null) {
            mlsMigrationService.migrateToMls(chatId);
        }
        var encrypted = MessageSendSupport.shouldServerEncrypt(request)
            ? mlsService.encrypt(chatId, senderId, content)
            : null;
        if (encrypted != null) {
            content = MessageSendSupport.combinedCiphertextBase64(encrypted);
        }
        var id = uuidGenerator.randomUuid();
        var inserted = messageRepositoryPort.insert(new MessageInsert(
            MessageId.of(id),
            ChatId.of(chatId),
            UserId.of(senderId),
            MessageSendSupport.typeForEncrypted(request.type(), encrypted),
            content,
            replyToMsgId,
            request.clientMsgId(),
            request.visibilityTtlSeconds(),
            attachmentFileId));
        if (inserted.isEmpty()) {
            return null;
        }
        var msg = MessageDomainMapper.toResponse(inserted.get());
        publishSendEvent(msg, request.clientMsgId());
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
            attachmentFileId));
        if (inserted.isEmpty()) {
            return null;
        }
        var msg = MessageDomainMapper.toResponse(inserted.get());
        publishSendEvent(msg, null);
        invalidateUnreadForChatMembers(targetChatId, userId);
        return msg;
    }

    public void publishSendEvent(MessageResponse msg, String clientMsgId) {
        try {
            var event = new MessageSendEvent(
                msg.id(), msg.chatId(), msg.senderId(), msg.type(),
                msg.content(), clientMsgId,
                msg.createdAt() != null ? msg.createdAt().toEpochMilli() : null,
                msg.replyToMsgId(),
                msg.attachmentFileId(),
                msg.visibilityTtlSeconds());
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publishPipelineMessageSend(data);
        } catch (Exception e) {
            log.warn("Failed to publish msg.send event for {}", msg.id(), e);
        }
    }

    private void invalidateUnreadForChatMembers(UUID chatId, UUID senderId) {
        if (!readCachePort.enabled()) {
            return;
        }
        for (var member : chatRepository.listMembers(chatId)) {
            try {
                var memberId = UUID.fromString(member.userId());
                if (!memberId.equals(senderId)) {
                    ReadCacheCoordinator.invalidateChatUnread(readCachePort, memberId);
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed member id
            }
        }
    }
}
