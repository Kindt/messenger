package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final BlockRepository blockRepository;
    private final MlsService mlsService;
    private final NatsOutboundPort natsOutbound;
    private final UuidGenerator uuidGenerator;

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
                          BlockRepository blockRepository,
                          MlsService mlsService, NatsOutboundPort natsOutbound, UuidGenerator uuidGenerator) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.blockRepository = blockRepository;
        this.mlsService = mlsService;
        this.natsOutbound = natsOutbound;
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * Причина запрета отправки для HTTP 403; пусто если можно отправлять.
     */
    /** Bundle keys {@code error.message.send_denied.*} for {@link com.avandocmsg.messenger.common.i18n.UserMessageSource}. */
    public Optional<String> sendBlockedReason(UUID chatId, UUID senderId) {
        if (chatRepository.getMemberRole(chatId, senderId) == null) {
            return Optional.of("error.message.send_denied.not_member");
        }
        if (chatRepository.isMemberBanned(chatId, senderId)) {
            return Optional.of("error.message.send_denied.banned");
        }
        if (isP2PMessagingBlocked(chatId, senderId)) {
            return Optional.of("error.message.send_denied.blocked");
        }
        return Optional.empty();
    }

    /** Читатель состоит в чате и не забанен в нём. */
    public boolean canAccessChat(UUID chatId, UUID readerId) {
        if (chatRepository.getMemberRole(chatId, readerId) == null) {
            return false;
        }
        return !chatRepository.isMemberBanned(chatId, readerId);
    }

    private boolean isMutuallyBlocked(UUID a, UUID b) {
        return blockRepository.exists(a, b) || blockRepository.exists(b, a);
    }

    private boolean isP2PMessagingBlocked(UUID chatId, UUID senderId) {
        return chatRepository.getChatType(chatId).filter("p2p"::equals).isPresent()
            && chatRepository.findOtherP2PMember(chatId, senderId)
            .map(peer -> isMutuallyBlocked(senderId, peer))
            .orElse(false);
    }

    /**
     * Сообщение существует в чате и доступно читателю (в т.ч. правила блокировок с отправителем).
     */
    private Optional<MessageResponse> messageVisibleToViewer(UUID chatId, UUID msgId, UUID viewerId) {
        if (!canAccessChat(chatId, viewerId)) {
            return Optional.empty();
        }
        var msg = messageRepository.findById(msgId).orElse(null);
        if (msg == null || !msg.chatId().equals(chatId.toString())) {
            return Optional.empty();
        }
        try {
            var senderId = UUID.fromString(msg.senderId());
            if (isMutuallyBlocked(viewerId, senderId)) {
                return Optional.empty();
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(msg);
    }

    public MessageResponse sendMessage(UUID chatId, UUID senderId, SendMessageRequest request, UUID replyToMsgId) {
        if (sendBlockedReason(chatId, senderId).isPresent()) {
            log.warn("Send denied for user {} in chat {}", senderId, chatId);
            return null;
        }
        var content = request.content();
        var encrypted = mlsService.encrypt(chatId, senderId, content);
        if (encrypted != null) {
            content = encrypted.ciphertextBase64();
        }
        var id = uuidGenerator.randomUuid();
        var msg = messageRepository.insert(id, chatId, senderId, typeForEncrypted(request.type(), encrypted),
            content, replyToMsgId, request.clientMsgId(), request.ttlSeconds());
        if (msg != null) {
            publishSendEvent(msg, request.clientMsgId());
        }
        return msg;
    }

    private String typeForEncrypted(String type, com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypted) {
        if (encrypted == null) return type != null ? type : "text";
        return "e2ee-" + (type != null ? type : "text");
    }

    private void publishSendEvent(MessageResponse msg, String clientMsgId) {
        try {
            var event = new MessageSendEvent(
                msg.id(), msg.chatId(), msg.senderId(), msg.type(),
                msg.content(), clientMsgId,
                msg.createdAt() != null ? msg.createdAt().toEpochMilli() : null);
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publishPipelineMessageSend(data);
        } catch (Exception e) {
            log.warn("Failed to publish msg.send event for {}", msg.id(), e);
        }
    }

    /** Синхронизация Solr через indexer: правка / мягкое удаление вне pipeline {@code msg.send}. */
    private void publishIndexEvent(MessageWorkerEvent event) {
        try {
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publish(NatsSubjects.MSG_EVENT_INDEX, data);
        } catch (Exception e) {
            log.warn("Failed to publish {} for message {}", NatsSubjects.MSG_EVENT_INDEX, event.messageId(), e);
        }
    }

    public List<MessageResponse> listMessages(UUID chatId, UUID userId, int limit, UUID before) {
        if (!canAccessChat(chatId, userId)) {
            return Collections.emptyList();
        }
        if (limit <= 0 || limit > 100) limit = 50;
        return messageRepository.findByChatId(chatId, limit, before, userId);
    }

    public MessageResponse getMessage(UUID chatId, UUID msgId, UUID userId) {
        return messageVisibleToViewer(chatId, msgId, userId).orElse(null);
    }

    public MessageResponse editMessage(UUID chatId, UUID msgId, UUID userId, String newContent) {
        if (newContent == null || newContent.isBlank()) return null;
        if (!canAccessChat(chatId, userId)) return null;
        if (chatRepository.getChatType(chatId).filter("saved"::equals).isPresent()) {
            return null;
        }
        var msg = messageRepository.findById(msgId).orElse(null);
        if (msg == null) return null;
        if (!msg.chatId().equals(chatId.toString())) return null;
        if (!msg.senderId().equals(userId.toString())) return null;
        if (msg.deleted()) return null;
        var ok = messageRepository.updateContent(msgId, userId, newContent);
        if (!ok) {
            return null;
        }
        var updated = messageRepository.findById(msgId).orElse(null);
        if (updated != null) {
            publishIndexEvent(MessageWorkerEvent.fromPersistedMessage(
                updated.id(),
                updated.chatId(),
                updated.senderId(),
                null,
                updated.createdAt() != null ? updated.createdAt().toEpochMilli() : null,
                updated.type(),
                updated.content(),
                "update"));
        }
        return updated;
    }

    public boolean deleteMessage(UUID chatId, UUID msgId, UUID userId) {
        if (!canAccessChat(chatId, userId)) return false;
        var msg = messageRepository.findById(msgId).orElse(null);
        if (msg == null || !msg.chatId().equals(chatId.toString())) return false;
        if (!msg.senderId().equals(userId.toString())) return false;
        if (!messageRepository.delete(msgId)) {
            return false;
        }
        publishIndexEvent(MessageWorkerEvent.forIndexDelete(msgId.toString()));
        return true;
    }

    public List<MessageVersionResponse> getMessageVersions(UUID chatId, UUID msgId, UUID userId) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) {
            return List.of();
        }
        return messageRepository.findVersions(msgId);
    }

    public boolean addReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (reaction == null || reaction.isBlank()) return false;
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) return false;
        return messageRepository.addReaction(msgId, userId, reaction);
    }

    public boolean removeReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) return false;
        return messageRepository.removeReaction(msgId, userId, reaction);
    }

    public List<ReactionResponse> getReactions(UUID chatId, UUID msgId, UUID userId) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) {
            return List.of();
        }
        return messageRepository.getReactions(msgId);
    }

    public boolean pinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) return false;
        return messageRepository.pinMessage(chatId, msgId, userId);
    }

    public boolean unpinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (!canAccessChat(chatId, userId)) return false;
        return messageRepository.unpinMessage(chatId, msgId);
    }

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId, UUID userId) {
        if (!canAccessChat(chatId, userId)) return List.of();
        return messageRepository.getPinnedMessages(chatId);
    }

    /**
     * Пересылка копии сообщения в другой чат (в т.ч. «Хранилище», ТЗ п. 30).
     */
    public MessageResponse forwardMessage(UUID sourceChatId, UUID msgId, UUID userId, UUID targetChatId) {
        if (sendBlockedReason(sourceChatId, userId).isPresent() || sendBlockedReason(targetChatId, userId).isPresent()) {
            return null;
        }
        var msg = messageRepository.findById(msgId).orElse(null);
        if (msg == null || !msg.chatId().equals(sourceChatId.toString()) || msg.deleted()) {
            return null;
        }
        var newId = uuidGenerator.randomUuid();
        var inserted = messageRepository.insert(newId, targetChatId, userId, msg.type(), msg.content(),
            null, null, null);
        if (inserted != null) {
            publishSendEvent(inserted, null);
        }
        return inserted;
    }
}
