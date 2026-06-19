package com.avandocmsg.messenger.api.directory;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcDirectorySyncRunJdbcRepository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for directory sync run JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcDirectorySyncRunJdbcRepository}.
 */
public class DirectorySyncRunRepository {
    private final JdbcDirectorySyncRunJdbcRepository jdbc;

    public DirectorySyncRunRepository(DataSource dataSource) {
        this.jdbc = new JdbcDirectorySyncRunJdbcRepository(dataSource);
    }

    public JdbcDirectorySyncRunJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public DirectorySyncRunRow startRun(UUID orgId) {
        return jdbc.startRun(orgId);
    }

    public void finishRun(UUID runId, String status, int usersUpserted, String error) {
        jdbc.finishRun(runId, status, usersUpserted, error);
    }

    public Optional<DirectorySyncRunRow> findLatestByOrg(UUID orgId) {
        return jdbc.findLatestByOrg(orgId);
    }
}
