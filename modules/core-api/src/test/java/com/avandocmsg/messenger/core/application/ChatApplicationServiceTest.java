package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatApplicationServiceTest {

    @Test
    void getChatForMember_returnsEmptyWhenNotMember() {
        var chatId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var service = new ChatApplicationService(port(chatId, false));
        assertTrue(service.getChatForMember(ChatId.of(chatId), UserId.of(viewerId)).isEmpty());
    }

    @Test
    void getChatForMember_returnsChatWhenMember() {
        var chatId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var service = new ChatApplicationService(port(chatId, true));
        var chat = service.getChatForMember(ChatId.of(chatId), UserId.of(viewerId));
        assertTrue(chat.isPresent());
        assertEquals(chatId, chat.get().id().value());
    }

    private static ChatRepositoryPort port(UUID chatId, boolean member) {
        return new ChatRepositoryPort() {
            @Override
            public Optional<Chat> findById(ChatId id) {
                return Optional.of(sampleChat(id));
            }

            @Override
            public boolean isMember(ChatId c, UserId u) {
                return member && c.value().equals(chatId);
            }

            @Override
            public Optional<String> memberRole(ChatId c, UserId u) {
                return isMember(c, u) ? Optional.of("member") : Optional.empty();
            }

            @Override
            public boolean isMemberBanned(ChatId c, UserId u) {
                return false;
            }

            @Override
            public Optional<UserId> findOtherP2pMember(ChatId c, UserId u) {
                return Optional.empty();
            }

            @Override
            public List<UserId> listMemberUserIds(ChatId c) {
                return List.of();
            }
        };
    }

    private static Chat sampleChat(ChatId id) {
        return new Chat(id, "Test", ChatType.GROUP, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
