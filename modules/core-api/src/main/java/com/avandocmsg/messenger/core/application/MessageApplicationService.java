package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hexagonal application service for message reads and write-path (Phase 2b+). */
public final class MessageApplicationService {
    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatRepository chatRepository;
    private final BlockRepository blockRepository;
    private final MessageSendCoordinator sendCoordinator;
    private final MessageEditCoordinator editCoordinator;
    private final MessageDeleteCoordinator deleteCoordinator;
    private final MessageReactionCoordinator reactionCoordinator;
    private final MessagePinCoordinator pinCoordinator;
    private final MessageRepository legacyMessageRepository;
    private final MlsService mlsService;

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository) {
        this(messageRepositoryPort, chatRepository, null, null, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository) {
        this(messageRepositoryPort, chatRepository, blockRepository, null, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, editCoordinator, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator,
                                     MessagePinCoordinator pinCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, pinCoordinator, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator,
                                     MessagePinCoordinator pinCoordinator,
                                     MessageRepository legacyMessageRepository,
                                     MlsService mlsService) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatRepository = chatRepository;
        this.blockRepository = blockRepository;
        this.sendCoordinator = sendCoordinator;
        this.editCoordinator = editCoordinator;
        this.deleteCoordinator = deleteCoordinator;
        this.reactionCoordinator = reactionCoordinator;
        this.pinCoordinator = pinCoordinator;
        this.legacyMessageRepository = legacyMessageRepository;
        this.mlsService = mlsService;
    }

    public Optional<Message> getMessageForMember(ChatId chatId, MessageId messageId, UserId viewerId) {
        if (chatRepository.getMemberRole(chatId.value(), viewerId.value()) == null) {
            return Optional.empty();
        }
        return messageRepositoryPort.findById(messageId)
            .filter(m -> m.chatId().equals(chatId));
    }

    public boolean isChatMember(ChatId chatId, UserId userId) {
        return chatRepository.getMemberRole(chatId.value(), userId.value()) != null;
    }

    /** Reader is a chat member and not banned. */
    public boolean canAccessChat(UUID chatId, UUID readerId) {
        if (chatRepository.getMemberRole(chatId, readerId) == null) {
            return false;
        }
        return !chatRepository.isMemberBanned(chatId, readerId);
    }

    public List<MessageResponse> listMessages(UUID chatId, UUID userId, int limit, UUID before) {
        return listMessages(chatId, userId, limit, before, null);
    }

    public List<MessageResponse> listMessages(UUID chatId, UUID userId, int limit, UUID before, UUID threadId) {
        if (legacyMessageRepository == null || !canAccessChat(chatId, userId)) {
            return List.of();
        }
        if (limit <= 0 || limit > 100) {
            limit = 50;
        }
        return legacyMessageRepository.findByChatId(chatId, limit, before, userId, threadId);
    }

    /**
     * Bundle key {@code error.message.thread_invalid} for {@link com.avandocmsg.messenger.common.i18n.UserMessageSource}.
     */
    public Optional<String> threadInvalidReason(UUID chatId, SendMessageRequest request) {
        if (request == null || request.threadId() == null || request.threadId().isBlank()) {
            return Optional.empty();
        }
        try {
            var threadId = UUID.fromString(request.threadId().trim());
            var root = messageRepositoryPort.findById(MessageId.of(threadId));
            if (root.isEmpty()) {
                return Optional.of("error.message.thread_invalid");
            }
            var message = root.get();
            if (!message.chatId().value().equals(chatId) || message.deleted()) {
                return Optional.of("error.message.thread_invalid");
            }
            if (message.threadId() != null) {
                return Optional.of("error.message.thread_invalid");
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of("error.message.thread_invalid");
        }
    }

    public List<MessageVersionResponse> getMessageVersions(UUID chatId, UUID msgId, UUID userId) {
        if (legacyMessageRepository == null) {
            return List.of();
        }
        if (messageVisibleToMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return List.of();
        }
        return legacyMessageRepository.findVersions(msgId);
    }

    public List<ReactionResponse> getReactions(UUID chatId, UUID msgId, UUID userId) {
        if (legacyMessageRepository == null) {
            return List.of();
        }
        if (messageVisibleToMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return List.of();
        }
        return legacyMessageRepository.getReactions(msgId);
    }

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId, UUID userId) {
        if (legacyMessageRepository == null || !canAccessChat(chatId, userId)) {
            return List.of();
        }
        return legacyMessageRepository.getPinnedMessages(chatId);
    }

    /** Server-side decrypt for e2ee-* messages (MLS stub on server). */
    public String plaintextPreview(UUID chatId, UUID msgId, UUID userId) {
        if (mlsService == null) {
            return null;
        }
        var visible = messageVisibleToMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId));
        if (visible.isEmpty()) {
            return null;
        }
        var message = visible.get();
        if (!MessageSendSupport.isE2eeType(message.type())) {
            return null;
        }
        return mlsService.decryptContentBase64(chatId, message.content());
    }

    /**
     * Bundle keys {@code error.message.send_denied.*} for {@link com.avandocmsg.messenger.common.i18n.UserMessageSource}.
     */
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

    public MessageResponse sendMessage(UUID chatId, UUID senderId, SendMessageRequest request, UUID replyToMsgId) {
        if (sendCoordinator == null) {
            throw new IllegalStateException("message write-path not wired");
        }
        if (sendBlockedReason(chatId, senderId).isPresent()) {
            return null;
        }
        return sendCoordinator.send(chatId, senderId, request, replyToMsgId);
    }

    public MessageResponse forwardMessage(UUID sourceChatId, UUID msgId, UUID userId, UUID targetChatId) {
        if (sendCoordinator == null) {
            throw new IllegalStateException("message write-path not wired");
        }
        if (sendBlockedReason(sourceChatId, userId).isPresent()
            || sendBlockedReason(targetChatId, userId).isPresent()) {
            return null;
        }
        return sendCoordinator.forward(sourceChatId, msgId, userId, targetChatId);
    }

    public MessageResponse editMessage(UUID chatId, UUID msgId, UUID userId, String newContent) {
        if (editCoordinator == null || newContent == null || newContent.isBlank()) {
            return null;
        }
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return null;
        }
        if (chatRepository.getChatType(chatId).filter("saved"::equals).isPresent()) {
            return null;
        }
        var messageId = MessageId.of(msgId);
        var message = messageRepositoryPort.findById(messageId).orElse(null);
        if (message == null || !message.chatId().value().equals(chatId)) {
            return null;
        }
        if (!message.senderId().value().equals(userId) || message.deleted()) {
            return null;
        }
        return editCoordinator.edit(messageId, UserId.of(userId), newContent);
    }

    public boolean deleteMessage(UUID chatId, UUID msgId, UUID userId) {
        if (deleteCoordinator == null) {
            return false;
        }
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return false;
        }
        var messageId = MessageId.of(msgId);
        var message = messageRepositoryPort.findById(messageId).orElse(null);
        if (message == null || !message.chatId().value().equals(chatId)) {
            return false;
        }
        return deleteCoordinator.delete(messageId, UserId.of(userId));
    }

    public boolean addReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (reactionCoordinator == null || reaction == null || reaction.isBlank()) {
            return false;
        }
        if (getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        return reactionCoordinator.addReaction(chatId, MessageId.of(msgId), UserId.of(userId), reaction);
    }

    public boolean removeReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (reactionCoordinator == null) {
            return false;
        }
        if (getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        return reactionCoordinator.removeReaction(chatId, MessageId.of(msgId), UserId.of(userId), reaction);
    }

    public boolean pinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (pinCoordinator == null) {
            return false;
        }
        if (getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        return pinCoordinator.pin(chatId, msgId, userId);
    }

    public boolean unpinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (pinCoordinator == null) {
            return false;
        }
        if (!isChatMember(ChatId.of(chatId), UserId.of(userId))) {
            return false;
        }
        return pinCoordinator.unpin(chatId, msgId, userId);
    }

    private Optional<Message> messageVisibleToMember(ChatId chatId, MessageId messageId, UserId viewerId) {
        if (!canAccessChat(chatId.value(), viewerId.value())) {
            return Optional.empty();
        }
        var message = messageRepositoryPort.findById(messageId).orElse(null);
        if (message == null || !message.chatId().equals(chatId)) {
            return Optional.empty();
        }
        if (isMutuallyBlocked(viewerId.value(), message.senderId().value())) {
            return Optional.empty();
        }
        return Optional.of(message);
    }

    private boolean isMutuallyBlocked(UUID a, UUID b) {
        if (blockRepository == null) {
            return false;
        }
        return blockRepository.exists(a, b) || blockRepository.exists(b, a);
    }

    private boolean isP2PMessagingBlocked(UUID chatId, UUID senderId) {
        if (blockRepository == null) {
            return false;
        }
        return chatRepository.getChatType(chatId).filter("p2p"::equals).isPresent()
            && chatRepository.findOtherP2PMember(chatId, senderId)
            .map(peer -> blockRepository.exists(senderId, peer) || blockRepository.exists(peer, senderId))
            .orElse(false);
    }
}
