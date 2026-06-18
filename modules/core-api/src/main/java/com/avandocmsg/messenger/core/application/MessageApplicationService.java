package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;

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

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository) {
        this(messageRepositoryPort, chatRepository, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository) {
        this(messageRepositoryPort, chatRepository, blockRepository, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, editCoordinator, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator) {
        this(messageRepositoryPort, chatRepository, blockRepository, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator,
                                     MessagePinCoordinator pinCoordinator) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatRepository = chatRepository;
        this.blockRepository = blockRepository;
        this.sendCoordinator = sendCoordinator;
        this.editCoordinator = editCoordinator;
        this.deleteCoordinator = deleteCoordinator;
        this.reactionCoordinator = reactionCoordinator;
        this.pinCoordinator = pinCoordinator;
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
