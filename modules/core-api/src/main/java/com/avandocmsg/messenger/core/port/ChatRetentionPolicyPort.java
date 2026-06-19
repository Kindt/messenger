package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Chat-level retention overlay ({@code chat_retention_policy}). */
public interface ChatRetentionPolicyPort {
    Optional<StoredRow> findByChatId(UUID chatId);

    boolean upsert(UUID chatId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                   boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                   UUID updatedBy);

    record StoredRow(
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
