package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for org retention policy JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcRetentionPolicyAdapter}.
 */
public class RetentionPolicyRepository {
    private final RetentionPolicyPort port;

    public RetentionPolicyRepository(DataSource dataSource) {
        this.port = new JdbcRetentionPolicyAdapter(dataSource);
    }

    RetentionPolicyRepository(RetentionPolicyPort port) {
        this.port = port;
    }

    public Optional<StoredRow> findByOrgId(UUID orgId) {
        return port.findByOrgId(orgId).map(RetentionPolicyRepository::map);
    }

    public boolean upsert(UUID orgId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        return port.upsert(orgId, hotMessageBodyMaxAgeDays, hotMetadataMinAgeDays,
            archiveMetadataEnabled, deepArchiveEnabled, legalHold, updatedBy);
    }

    private static StoredRow map(RetentionPolicyPort.StoredRow row) {
        return new StoredRow(row.orgId(), row.hotMessageBodyMaxAgeDays(), row.hotMetadataMinAgeDays(),
            row.archiveMetadataEnabled(), row.deepArchiveEnabled(), row.legalHold(), row.updatedAt(), row.updatedBy());
    }

    public record StoredRow(
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
