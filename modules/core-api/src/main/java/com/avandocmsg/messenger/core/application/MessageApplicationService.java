package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for message reads (Phase 2b). */
public final class MessageApplicationService {
    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatRepository legacyChatRepository;

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepository legacyChatRepository) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.legacyChatRepository = legacyChatRepository;
    }

    public Optional<Message> getMessageForMember(ChatId chatId, MessageId messageId, UserId viewerId) {
        if (legacyChatRepository.getMemberRole(chatId.value(), viewerId.value()) == null) {
            return Optional.empty();
        }
        return messageRepositoryPort.findById(messageId)
            .filter(m -> m.chatId().equals(chatId));
    }

    /** Phase 2b: membership gate before write-path (send stays in MessageService until port insert). */
    public boolean isChatMember(ChatId chatId, UserId userId) {
        return legacyChatRepository.getMemberRole(chatId.value(), userId.value()) != null;
    }
}
