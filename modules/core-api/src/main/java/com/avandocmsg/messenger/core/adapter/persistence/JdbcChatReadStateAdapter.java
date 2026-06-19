package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.ChatReadRepository;
import com.avandocmsg.messenger.core.port.ChatReadStatePort;

import javax.sql.DataSource;
import java.util.UUID;

public final class JdbcChatReadStateAdapter implements ChatReadStatePort {
    private final ChatReadRepository delegate;

    public JdbcChatReadStateAdapter(ChatReadRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcChatReadStateAdapter(DataSource dataSource) {
        this.delegate = new ChatReadRepository(dataSource);
    }

    @Override
    public boolean upsertLastRead(UUID userId, UUID chatId, UUID lastReadMessageId) {
        return delegate.upsertLastRead(userId, chatId, lastReadMessageId);
    }

    @Override
    public int countUnreadFromOthers(UUID userId, UUID chatId) {
        return delegate.countUnreadFromOthers(userId, chatId);
    }
}
