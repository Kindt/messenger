package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for chat reads (Phase 2a). */
public final class ChatApplicationService {
    private final ChatRepositoryPort chatRepositoryPort;
    private final ChatRepository legacyChatRepository;

    public ChatApplicationService(ChatRepositoryPort chatRepositoryPort, ChatRepository legacyChatRepository) {
        this.chatRepositoryPort = chatRepositoryPort;
        this.legacyChatRepository = legacyChatRepository;
    }

    public Optional<Chat> getChatForMember(ChatId chatId, UserId viewerId) {
        if (legacyChatRepository.getMemberRole(chatId.value(), viewerId.value()) == null) {
            return Optional.empty();
        }
        return chatRepositoryPort.findById(chatId);
    }
}
