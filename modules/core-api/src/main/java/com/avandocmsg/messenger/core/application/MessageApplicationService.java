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

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository) {
        this(messageRepositoryPort, chatRepository, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository) {
        this(messageRepositoryPort, chatRepository, blockRepository, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository chatRepository,
                                     BlockRepository blockRepository, MessageSendCoordinator sendCoordinator) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatRepository = chatRepository;
        this.blockRepository = blockRepository;
        this.sendCoordinator = sendCoordinator;
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
