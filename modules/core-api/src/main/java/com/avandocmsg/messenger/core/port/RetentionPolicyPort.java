package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Org-level retention policy ({@code org_retention_policy}). */
public interface RetentionPolicyPort {
    Optional<StoredRow> findByOrgId(UUID orgId);

    boolean upsert(UUID orgId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                   boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                   UUID updatedBy);

    record StoredRow(
        UUID orgId,
        Integer hotMessageBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold,
        Instant updatedAt,
        String updatedBy
    ) {}
}
