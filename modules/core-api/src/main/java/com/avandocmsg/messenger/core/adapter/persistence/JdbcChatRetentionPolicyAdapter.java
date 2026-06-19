package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class JdbcChatRetentionPolicyAdapter implements ChatRetentionPolicyPort {
    private final ChatRetentionPolicyRepository delegate;

    public JdbcChatRetentionPolicyAdapter(ChatRetentionPolicyRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcChatRetentionPolicyAdapter(DataSource dataSource) {
        this.delegate = new ChatRetentionPolicyRepository(dataSource);
    }

    @Override
    public Optional<StoredRow> findByChatId(UUID chatId) {
        return delegate.findByChatId(chatId).map(JdbcChatRetentionPolicyAdapter::map);
    }

    @Override
    public boolean upsert(UUID chatId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        return delegate.upsert(chatId, hotMessageBodyMaxAgeDays, hotMetadataMinAgeDays,
            archiveMetadataEnabled, deepArchiveEnabled, legalHold, updatedBy);
    }

    private static StoredRow map(ChatRetentionPolicyRepository.StoredRow row) {
        return new StoredRow(row.chatId(), row.hotMessageBodyMaxAgeDays(), row.hotMetadataMinAgeDays(),
            row.archiveMetadataEnabled(), row.deepArchiveEnabled(), row.legalHold(), row.updatedAt(), row.updatedBy());
    }
}
