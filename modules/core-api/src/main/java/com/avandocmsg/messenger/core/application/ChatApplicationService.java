package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for chat reads (Phase 2a). */
public final class ChatApplicationService {
    private final ChatRepositoryPort chatRepositoryPort;

    public ChatApplicationService(ChatRepositoryPort chatRepositoryPort) {
        this.chatRepositoryPort = chatRepositoryPort;
    }

    public Optional<Chat> getChatForMember(ChatId chatId, UserId viewerId) {
        if (!chatRepositoryPort.isMember(chatId, viewerId)) {
            return Optional.empty();
        }
        return chatRepositoryPort.findById(chatId);
    }
}
