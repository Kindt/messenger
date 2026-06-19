package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.DirectorySyncRunRepositoryPort;

import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link DirectorySyncRunRepositoryPort}. */
public final class JdbcDirectorySyncRunRepositoryAdapter implements DirectorySyncRunRepositoryPort {

    private final JdbcDirectorySyncRunJdbcRepository repository;

    public JdbcDirectorySyncRunRepositoryAdapter(JdbcDirectorySyncRunJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public DirectorySyncRunRow startRun(UUID orgId) {
        var row = repository.startRun(orgId);
        return toPort(row);
    }

    @Override
    public void finishRun(UUID runId, String status, int usersUpserted, String error) {
        repository.finishRun(runId, status, usersUpserted, error);
    }

    @Override
    public Optional<DirectorySyncRunRow> findLatestByOrg(UUID orgId) {
        return repository.findLatestByOrg(orgId).map(JdbcDirectorySyncRunRepositoryAdapter::toPort);
    }

    private static DirectorySyncRunRow toPort(com.avandocmsg.messenger.api.directory.DirectorySyncRunRow row) {
        return new DirectorySyncRunRow(
            row.id(),
            row.orgId(),
            row.status(),
            row.usersUpserted(),
            row.error(),
            row.startedAt(),
            row.finishedAt());
    }
}
