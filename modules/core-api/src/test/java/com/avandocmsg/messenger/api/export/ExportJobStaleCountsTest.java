package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAdminStatsJdbcRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportJobStaleCountsTest {

    private HikariDataSource ds;
    private JdbcAdminStatsJdbcRepository metrics;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:stale_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        metrics = new JdbcAdminStatsJdbcRepository(ds);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE export_jobs (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  requested_by UUID NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
                """);
            var chat = UUID.randomUUID();
            var user = UUID.randomUUID();
            var fresh = UUID.randomUUID();
            var stale = UUID.randomUUID();
            st.execute("INSERT INTO export_jobs (id, chat_id, requested_by, status, updated_at) VALUES ('"
                + fresh + "', '" + chat + "', '" + user + "', 'processing', CURRENT_TIMESTAMP)");
            var old = Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS));
            try (var ps = c.prepareStatement(
                "INSERT INTO export_jobs (id, chat_id, requested_by, status, updated_at) VALUES (?, ?, ?, 'processing', ?)")) {
                ps.setObject(1, stale);
                ps.setObject(2, chat);
                ps.setObject(3, user);
                ps.setTimestamp(4, old);
                ps.executeUpdate();
            }
            st.execute("INSERT INTO export_jobs (id, chat_id, requested_by, status) VALUES ('"
                + UUID.randomUUID() + "', '" + chat + "', '" + user + "', 'queued')");
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void countProcessingStale_ignoresFreshAndNonProcessing() {
        assertEquals(1, metrics.countProcessingStaleExportJobs(30));
        assertEquals(0, metrics.countProcessingStaleExportJobs(180));
    }
}
