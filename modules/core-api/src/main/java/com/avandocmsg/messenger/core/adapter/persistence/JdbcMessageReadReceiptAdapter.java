package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.chats.dto.ReadReceiptUserInfo;
import com.avandocmsg.messenger.api.repository.MessageReadReceiptRepository;
import com.avandocmsg.messenger.core.port.MessageReadReceiptPort;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JdbcMessageReadReceiptAdapter implements MessageReadReceiptPort {
    private final MessageReadReceiptRepository delegate;

    public JdbcMessageReadReceiptAdapter(MessageReadReceiptRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcMessageReadReceiptAdapter(DataSource dataSource) {
        this.delegate = new MessageReadReceiptRepository(dataSource);
    }

    @Override
    public boolean insert(UUID messageId, UUID userId, Instant readAt) {
        return delegate.insert(messageId, userId, readAt);
    }

    @Override
    public int insertBatch(List<UUID> messageIds, UUID userId, Instant readAt) {
        return delegate.insertBatch(messageIds, userId, readAt);
    }

    @Override
    public List<ReadReceiptUserInfo> findByMessageId(UUID messageId, int offset, int limit) {
        return delegate.findByMessageId(messageId, offset, limit);
    }

    @Override
    public long countAll() {
        return delegate.countAll();
    }

    @Override
    public int deleteOlderThanDays(int days) {
        return delegate.deleteOlderThanDays(days);
    }
}
