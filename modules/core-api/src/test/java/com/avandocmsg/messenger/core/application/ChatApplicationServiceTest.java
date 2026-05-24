package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatApplicationServiceTest {

    @Test
    void getChatForMember_returnsEmptyWhenNotMember() {
        var chatId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var service = new ChatApplicationService(
            (ChatRepositoryPort) id -> Optional.of(sampleChat(id)),
            new ChatRepository(null, java.time.Clock.systemUTC(), com.avandocmsg.messenger.core.port.UuidGenerator.standard()) {
                @Override
                public String getMemberRole(UUID c, UUID u) {
                    return null;
                }
            });
        assertTrue(service.getChatForMember(ChatId.of(chatId), UserId.of(viewerId)).isEmpty());
    }

    @Test
    void getChatForMember_returnsChatWhenMember() {
        var chatId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var service = new ChatApplicationService(
            (ChatRepositoryPort) id -> Optional.of(sampleChat(id)),
            new ChatRepository(null, java.time.Clock.systemUTC(), com.avandocmsg.messenger.core.port.UuidGenerator.standard()) {
                @Override
                public String getMemberRole(UUID c, UUID u) {
                    return "member";
                }
            });
        var chat = service.getChatForMember(ChatId.of(chatId), UserId.of(viewerId));
        assertTrue(chat.isPresent());
        assertEquals(chatId, chat.get().id().value());
    }

    private static Chat sampleChat(ChatId id) {
        return new Chat(id, "Test", ChatType.GROUP, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
