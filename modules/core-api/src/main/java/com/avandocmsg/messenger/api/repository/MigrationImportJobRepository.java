package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcMigrationImportJobAdapter;
import com.avandocmsg.messenger.core.port.MigrationImportJobPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for migration import job JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcMigrationImportJobAdapter}.
 */
public class MigrationImportJobRepository {
    private final MigrationImportJobPort port;

    public MigrationImportJobRepository(DataSource dataSource) {
        this.port = new JdbcMigrationImportJobAdapter(dataSource);
    }

    MigrationImportJobRepository(MigrationImportJobPort port) {
        this.port = port;
    }

    public record JobRow(
        UUID id,
        UUID orgId,
        String source,
        String status,
        String configJson,
        String resultJson,
        UUID createdBy
    ) {}

    public UUID insert(UUID orgId, String source, String configJson, UUID createdBy) {
        return port.insert(orgId, source, configJson, createdBy);
    }

    public Optional<JobRow> findById(UUID id) {
        return port.findById(id).map(MigrationImportJobRepository::map);
    }

    public List<JobRow> listForOrg(UUID orgId, int limit) {
        return port.listForOrg(orgId, limit).stream().map(MigrationImportJobRepository::map).toList();
    }

    public List<JobRow> listPending(int limit) {
        return port.listPending(limit).stream().map(MigrationImportJobRepository::map).toList();
    }

    public boolean updateStatus(UUID id, String status, String resultJson) {
        return port.updateStatus(id, status, resultJson);
    }

    private static JobRow map(MigrationImportJobPort.JobRow row) {
        return new JobRow(row.id(), row.orgId(), row.source(), row.status(), row.configJson(), row.resultJson(),
            row.createdBy());
    }
}
