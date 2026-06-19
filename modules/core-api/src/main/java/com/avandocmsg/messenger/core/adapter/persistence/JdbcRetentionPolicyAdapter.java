package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class JdbcRetentionPolicyAdapter implements RetentionPolicyPort {
    private final RetentionPolicyRepository delegate;

    public JdbcRetentionPolicyAdapter(RetentionPolicyRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcRetentionPolicyAdapter(DataSource dataSource) {
        this.delegate = new RetentionPolicyRepository(dataSource);
    }

    @Override
    public Optional<StoredRow> findByOrgId(UUID orgId) {
        return delegate.findByOrgId(orgId).map(JdbcRetentionPolicyAdapter::map);
    }

    @Override
    public boolean upsert(UUID orgId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        return delegate.upsert(orgId, hotMessageBodyMaxAgeDays, hotMetadataMinAgeDays,
            archiveMetadataEnabled, deepArchiveEnabled, legalHold, updatedBy);
    }

    private static StoredRow map(RetentionPolicyRepository.StoredRow row) {
        return new StoredRow(row.orgId(), row.hotMessageBodyMaxAgeDays(), row.hotMetadataMinAgeDays(),
            row.archiveMetadataEnabled(), row.deepArchiveEnabled(), row.legalHold(), row.updatedAt(), row.updatedBy());
    }
}
