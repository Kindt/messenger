package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatBanAdapter;
import com.avandocmsg.messenger.core.port.ChatBanPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for chat ban JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcChatBanAdapter}.
 */
public class ChatBanRepository {
    private final ChatBanPort port;

    public ChatBanRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.port = new JdbcChatBanAdapter(dataSource, clock, uuidGenerator);
    }

    ChatBanRepository(ChatBanPort port) {
        this.port = port;
    }

    public ChatBanResponse ban(UUID chatId, UUID userId, UUID bannedBy, String reason) {
        return port.ban(chatId, userId, bannedBy, reason);
    }

    public Optional<ChatBanResponse> findById(UUID id) {
        return port.findById(id);
    }

    public List<ChatBanResponse> findByChatId(UUID chatId) {
        return port.findByChatId(chatId);
    }

    public boolean unban(UUID chatId, UUID userId) {
        return port.unban(chatId, userId);
    }

    public boolean isBanned(UUID chatId, UUID userId) {
        return port.isBanned(chatId, userId);
    }
}
