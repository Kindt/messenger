package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
import com.avandocmsg.messenger.api.repository.ChatBanRepository;
import com.avandocmsg.messenger.core.port.ChatBanPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcChatBanAdapter implements ChatBanPort {
    private final ChatBanRepository delegate;

    public JdbcChatBanAdapter(ChatBanRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcChatBanAdapter(DataSource dataSource, Clock clock,
                              com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.delegate = new ChatBanRepository(dataSource, clock, uuidGenerator);
    }

    @Override
    public ChatBanResponse ban(UUID chatId, UUID userId, UUID bannedBy, String reason) {
        return delegate.ban(chatId, userId, bannedBy, reason);
    }

    @Override
    public Optional<ChatBanResponse> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public List<ChatBanResponse> findByChatId(UUID chatId) {
        return delegate.findByChatId(chatId);
    }

    @Override
    public boolean unban(UUID chatId, UUID userId) {
        return delegate.unban(chatId, userId);
    }

    @Override
    public boolean isBanned(UUID chatId, UUID userId) {
        return delegate.isBanned(chatId, userId);
    }
}
