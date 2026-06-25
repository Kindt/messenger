package com.avandocmsg.messenger.core.adapter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcMlsGroupStateCountCapH2Test {

    private HikariDataSource ds;
    private JdbcMlsGroupStateJdbcRepository repo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:mls_cap_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE mls_group_state (
                  group_id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  epoch BIGINT NOT NULL DEFAULT 0,
                  tree_data VARBINARY NOT NULL DEFAULT X'00',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        var clock = Clock.fixed(Instant.parse("2026-06-25T12:00:00Z"), ZoneOffset.UTC);
        repo = new JdbcMlsGroupStateJdbcRepository(ds, clock);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void countAll_capsAtAdminLimit() throws Exception {
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("""
                INSERT INTO mls_group_state (group_id, chat_id, epoch, tree_data)
                SELECT RANDOM_UUID(), RANDOM_UUID(), 0, X'00' FROM SYSTEM_RANGE(1, %d)
                """.formatted(JdbcListLimits.COUNT_CAP_ADMIN + 1));
        }
        assertEquals(JdbcListLimits.COUNT_CAP_ADMIN, repo.countAll());
    }

    @Test
    void countAll_belowCap_returnsExactCount() throws Exception {
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("""
                INSERT INTO mls_group_state (group_id, chat_id, epoch, tree_data)
                SELECT RANDOM_UUID(), RANDOM_UUID(), 0, X'00' FROM SYSTEM_RANGE(1, 3)
                """);
        }
        assertEquals(3L, repo.countAll());
    }
}
