package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcMlsGroupStateJdbcRepository;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for MLS group state JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcMlsGroupStateJdbcRepository}.
 */
public class MlsGroupStateRepository {
    private final JdbcMlsGroupStateJdbcRepository jdbc;

    public MlsGroupStateRepository(DataSource dataSource, Clock clock) {
        this.jdbc = new JdbcMlsGroupStateJdbcRepository(dataSource, clock);
    }

    public JdbcMlsGroupStateJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public boolean save(MlsGroupState state) {
        return jdbc.save(state);
    }

    public Optional<MlsGroupState> findByGroupId(UUID groupId) {
        return jdbc.findByGroupId(groupId);
    }

    public Optional<MlsGroupState> findByChatId(UUID chatId) {
        return jdbc.findByChatId(chatId);
    }

    public long countAll() {
        return jdbc.countAll();
    }

    public boolean deleteByGroupId(UUID groupId) {
        return jdbc.deleteByGroupId(groupId);
    }
}
