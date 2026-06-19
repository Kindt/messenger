package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for chat retention policy JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcChatRetentionPolicyAdapter}.
 */
public class ChatRetentionPolicyRepository {
    private final ChatRetentionPolicyPort port;

    public ChatRetentionPolicyRepository(DataSource dataSource) {
        this.port = new JdbcChatRetentionPolicyAdapter(dataSource);
    }

    ChatRetentionPolicyRepository(ChatRetentionPolicyPort port) {
        this.port = port;
    }

    public Optional<StoredRow> findByChatId(UUID chatId) {
        return port.findByChatId(chatId).map(ChatRetentionPolicyRepository::map);
    }

    public boolean upsert(UUID chatId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        return port.upsert(chatId, hotMessageBodyMaxAgeDays, hotMetadataMinAgeDays,
            archiveMetadataEnabled, deepArchiveEnabled, legalHold, updatedBy);
    }

    private static StoredRow map(ChatRetentionPolicyPort.StoredRow row) {
        return new StoredRow(row.chatId(), row.hotMessageBodyMaxAgeDays(), row.hotMetadataMinAgeDays(),
            row.archiveMetadataEnabled(), row.deepArchiveEnabled(), row.legalHold(), row.updatedAt(), row.updatedBy());
    }

    public record StoredRow(
        UUID chatId,
        Integer hotMessageBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold,
        Instant updatedAt,
        String updatedBy
    ) {}
}
