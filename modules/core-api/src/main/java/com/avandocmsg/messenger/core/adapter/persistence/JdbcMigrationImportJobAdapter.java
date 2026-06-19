package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.MigrationImportJobRepository;
import com.avandocmsg.messenger.core.port.MigrationImportJobPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMigrationImportJobAdapter implements MigrationImportJobPort {
    private final MigrationImportJobRepository delegate;

    public JdbcMigrationImportJobAdapter(MigrationImportJobRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcMigrationImportJobAdapter(DataSource dataSource) {
        this.delegate = new MigrationImportJobRepository(dataSource);
    }

    @Override
    public UUID insert(UUID orgId, String source, String configJson, UUID createdBy) {
        return delegate.insert(orgId, source, configJson, createdBy);
    }

    @Override
    public Optional<JobRow> findById(UUID id) {
        return delegate.findById(id).map(JdbcMigrationImportJobAdapter::map);
    }

    @Override
    public List<JobRow> listForOrg(UUID orgId, int limit) {
        return delegate.listForOrg(orgId, limit).stream().map(JdbcMigrationImportJobAdapter::map).toList();
    }

    @Override
    public boolean updateStatus(UUID id, String status, String resultJson) {
        return delegate.updateStatus(id, status, resultJson);
    }

    private static JobRow map(MigrationImportJobRepository.JobRow row) {
        return new JobRow(row.id(), row.orgId(), row.source(), row.status(), row.configJson(), row.resultJson(),
            row.createdBy());
    }
}
