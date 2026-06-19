package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.chats.dto.ReadReceiptUserInfo;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageReadReceiptAdapter;
import com.avandocmsg.messenger.core.port.MessageReadReceiptPort;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Legacy façade for message read receipt JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcMessageReadReceiptAdapter}.
 */
public class MessageReadReceiptRepository {
    private final MessageReadReceiptPort port;

    public MessageReadReceiptRepository(DataSource dataSource) {
        this.port = new JdbcMessageReadReceiptAdapter(dataSource);
    }

    MessageReadReceiptRepository(MessageReadReceiptPort port) {
        this.port = port;
    }

    public boolean insert(UUID messageId, UUID userId, Instant readAt) {
        return port.insert(messageId, userId, readAt);
    }

    public int insertBatch(List<UUID> messageIds, UUID userId, Instant readAt) {
        return port.insertBatch(messageIds, userId, readAt);
    }

    public List<ReadReceiptUserInfo> findByMessageId(UUID messageId, int offset, int limit) {
        return port.findByMessageId(messageId, offset, limit);
    }

    public long countAll() {
        return port.countAll();
    }

    public int deleteOlderThanDays(int days) {
        return port.deleteOlderThanDays(days);
    }
}
