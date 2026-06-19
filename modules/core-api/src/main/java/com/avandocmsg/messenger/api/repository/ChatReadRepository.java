package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatReadStateAdapter;
import com.avandocmsg.messenger.core.port.ChatReadStatePort;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Legacy façade for chat read state JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcChatReadStateAdapter}.
 */
public class ChatReadRepository {
    private final ChatReadStatePort port;

    public ChatReadRepository(DataSource dataSource) {
        this.port = new JdbcChatReadStateAdapter(dataSource);
    }

    ChatReadRepository(ChatReadStatePort port) {
        this.port = port;
    }

    public boolean upsertLastRead(UUID userId, UUID chatId, UUID lastReadMessageId) {
        return port.upsertLastRead(userId, chatId, lastReadMessageId);
    }

    public int countUnreadFromOthers(UUID userId, UUID chatId) {
        return port.countUnreadFromOthers(userId, chatId);
    }
}
