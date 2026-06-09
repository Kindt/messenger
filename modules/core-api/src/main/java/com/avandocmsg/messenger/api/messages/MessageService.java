package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsMessageTypes;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.common.dto.MessageChangeEvent;
import com.avandocmsg.messenger.common.dto.PinChangeEvent;
import com.avandocmsg.messenger.common.dto.ReactionChangeEvent;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final BlockRepository blockRepository;
    private final MlsService mlsService;
    private final MlsMigrationService mlsMigrationService;
    private final NatsOutboundPort natsOutbound;
    private final UuidGenerator uuidGenerator;
    private final BooleanSupplier indexerAvailable;
    private final Deque<MessageWorkerEvent> pendingIndexEvents = new ArrayDeque<>();
    private static final int MAX_PENDING_INDEX_EVENTS = 2048;

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
                          BlockRepository blockRepository,
                          MlsService mlsService, NatsOutboundPort natsOutbound, UuidGenerator uuidGenerator) {
        this(messageRepository, chatRepository, blockRepository, mlsService, null, natsOutbound, uuidGenerator, () -> true);
    }

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
                          BlockRepository blockRepository,
                          MlsService mlsService, NatsOutboundPort natsOutbound, UuidGenerator uuidGenerator,
                          BooleanSupplier indexerAvailable) {
        this(messageRepository, chatRepository, blockRepository, mlsService, null, natsOutbound, uuidGenerator,
            indexerAvailable);
    }

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
                          BlockRepository blockRepository,
                          MlsService mlsService, MlsMigrationService mlsMigrationService,
                          NatsOutboundPort natsOutbound, UuidGenerator uuidGenerator,
                          BooleanSupplier indexerAvailable) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.blockRepository = blockRepository;
        this.mlsService = mlsService;
        this.mlsMigrationService = mlsMigrationService;
        this.natsOutbound = natsOutbound;
        this.uuidGenerator = uuidGenerator;
        this.indexerAvailable = indexerAvailable != null ? indexerAvailable : () -> true;
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
        var attachmentFileId = parseAttachmentFileId(request.type(), request.content());
        var content = request.content();
        if (usesMlsScheme(request)) {
            if (mlsMigrationService != null) {
                mlsMigrationService.migrateToMls(chatId);
            }
        }
        var encrypted = shouldServerEncrypt(request)
            ? mlsService.encrypt(chatId, senderId, content)
            : null;
        if (encrypted != null) {
            content = combinedCiphertextBase64(encrypted);
        }
        var id = uuidGenerator.randomUuid();
        var msg = messageRepository.insert(id, chatId, senderId, typeForEncrypted(request.type(), encrypted),
            content, replyToMsgId, request.clientMsgId(), request.visibilityTtlSeconds(), attachmentFileId);
        if (msg != null) {
            publishSendEvent(msg, request.clientMsgId());
        }
        return msg;
    }

    private String typeForEncrypted(String type, com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypted) {
        if (encrypted == null) return type != null ? type : "text";
        return "e2ee-" + (type != null ? type : "text");
    }

    static boolean usesMlsScheme(SendMessageRequest request) {
        return request != null
            && request.e2eeScheme() != null
            && MlsMessageTypes.SCHEME_MLS.equalsIgnoreCase(request.e2eeScheme());
    }

    static boolean shouldServerEncrypt(SendMessageRequest request) {
        if (request == null) {
            return true;
        }
        if (request.e2eeScheme() == null || request.e2eeScheme().isBlank()) {
            return true;
        }
        if (MlsMessageTypes.SCHEME_LEGACY.equalsIgnoreCase(request.e2eeScheme())) {
            return false;
        }
        return MlsMessageTypes.SCHEME_MLS.equalsIgnoreCase(request.e2eeScheme());
    }

    static boolean isE2eeType(String type) {
        return type != null && type.startsWith("e2ee-");
    }

    static UUID parseAttachmentFileId(String type, String content) {
        if (content == null || content.isBlank() || type == null) {
            return null;
        }
        var base = type.startsWith("e2ee-") ? type.substring(5) : type;
        if (!"file".equals(base) && !"image".equals(base) && !"video".equals(base)) {
            return null;
        }
        try {
            return UUID.fromString(content.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String combinedCiphertextBase64(com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypted) {
        var nonce = Base64.getDecoder().decode(encrypted.nonceBase64());
        var ct = Base64.getDecoder().decode(encrypted.ciphertextBase64());
        var combined = new byte[nonce.length + ct.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(ct, 0, combined, nonce.length, ct.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    /** Серверная расшифровка для веб-превью (MLS-контур на сервере, не полный RFC 9420). */
    public String plaintextPreview(UUID chatId, UUID msgId, UUID userId) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) {
            return null;
        }
        var msg = messageRepository.findById(msgId).orElse(null);
        if (msg == null || !msg.chatId().equals(chatId.toString()) || !isE2eeType(msg.type())) {
            return null;
        }
        return mlsService.decryptContentBase64(chatId, msg.content());
    }

    private void publishSendEvent(MessageResponse msg, String clientMsgId) {
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

    private void publishMessageChange(MessageChangeEvent event) {
        try {
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publish(NatsSubjects.MSG_CHANGE, data);
        } catch (Exception e) {
            log.warn("Failed to publish {} for message {}", NatsSubjects.MSG_CHANGE, event.messageId(), e);
        }
    }

    private void publishReactionChange(ReactionChangeEvent event) {
        try {
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publish(NatsSubjects.MSG_REACTION, data);
        } catch (Exception e) {
            log.warn("Failed to publish {} for message {}", NatsSubjects.MSG_REACTION, event.messageId(), e);
        }
    }

    private void publishPinChange(PinChangeEvent event) {
        try {
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publish(NatsSubjects.MSG_PIN, data);
        } catch (Exception e) {
            log.warn("Failed to publish {} for message {}", NatsSubjects.MSG_PIN, event.messageId(), e);
        }
    }

    /** Синхронизация Solr через indexer: правка / мягкое удаление вне pipeline {@code msg.send}. */
    private void publishIndexEvent(MessageWorkerEvent event) {
        if (!indexerAvailable.getAsBoolean()) {
            enqueuePendingIndexEvent(event);
            log.info("Indexer service unavailable; queued {}", NatsSubjects.MSG_EVENT_INDEX);
            return;
        }
        flushPendingIndexEvents();
        try {
            var data = MAPPER.writeValueAsBytes(event);
            natsOutbound.publish(NatsSubjects.MSG_EVENT_INDEX, data);
        } catch (Exception e) {
            log.warn("Failed to publish {} for message {}", NatsSubjects.MSG_EVENT_INDEX, event.messageId(), e);
        }
    }

    private void flushPendingIndexEvents() {
        while (true) {
            MessageWorkerEvent pending;
            synchronized (pendingIndexEvents) {
                pending = pendingIndexEvents.pollFirst();
            }
            if (pending == null) {
                return;
            }
            try {
                var data = MAPPER.writeValueAsBytes(pending);
                natsOutbound.publish(NatsSubjects.MSG_EVENT_INDEX, data);
            } catch (Exception e) {
                log.warn("Failed to publish queued {} for message {}", NatsSubjects.MSG_EVENT_INDEX, pending.messageId(), e);
                enqueuePendingIndexEvent(pending);
                return;
            }
        }
    }

    private void enqueuePendingIndexEvent(MessageWorkerEvent event) {
        synchronized (pendingIndexEvents) {
            if (pendingIndexEvents.size() >= MAX_PENDING_INDEX_EVENTS) {
                pendingIndexEvents.pollFirst();
            }
            pendingIndexEvents.addLast(event);
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
        publishMessageChange(new MessageChangeEvent(
            "delete",
            msg.id(),
            msg.chatId(),
            msg.senderId(),
            msg.type(),
            null,
            msg.createdAt() != null ? msg.createdAt().toEpochMilli() : null,
            null));
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
        if (!messageRepository.addReaction(msgId, userId, reaction)) {
            return false;
        }
        publishReactionChange(new ReactionChangeEvent(
            "add", msgId.toString(), chatId.toString(), userId.toString(), reaction));
        return true;
    }

    public boolean removeReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) return false;
        if (!messageRepository.removeReaction(msgId, userId, reaction)) {
            return false;
        }
        publishReactionChange(new ReactionChangeEvent(
            "remove", msgId.toString(), chatId.toString(), userId.toString(), reaction));
        return true;
    }

    public List<ReactionResponse> getReactions(UUID chatId, UUID msgId, UUID userId) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) {
            return List.of();
        }
        return messageRepository.getReactions(msgId);
    }

    public boolean pinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (messageVisibleToViewer(chatId, msgId, userId).isEmpty()) return false;
        if (!messageRepository.pinMessage(chatId, msgId, userId)) {
            return false;
        }
        publishPinChange(new PinChangeEvent(
            "pin",
            chatId.toString(),
            msgId.toString(),
            userId.toString(),
            System.currentTimeMillis()));
        return true;
    }

    public boolean unpinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (!canAccessChat(chatId, userId)) return false;
        if (!messageRepository.unpinMessage(chatId, msgId)) {
            return false;
        }
        publishPinChange(new PinChangeEvent(
            "unpin",
            chatId.toString(),
            msgId.toString(),
            userId.toString(),
            null));
        return true;
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
        UUID attachmentFileId = null;
        if (msg.attachmentFileId() != null && !msg.attachmentFileId().isBlank()) {
            attachmentFileId = UUID.fromString(msg.attachmentFileId());
        }
        var inserted = messageRepository.insert(newId, targetChatId, userId, msg.type(), msg.content(),
            null, null, null, attachmentFileId);
        if (inserted != null) {
            publishSendEvent(inserted, null);
        }
        return inserted;
    }
}
