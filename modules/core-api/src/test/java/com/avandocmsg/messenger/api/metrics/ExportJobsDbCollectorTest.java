package com.avandocmsg.messenger.api.metrics;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportJobsDbCollectorTest {

    private HikariDataSource ds;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:dbcol_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
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
            var old = Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS));
            try (var ps = c.prepareStatement(
                "INSERT INTO export_jobs (id, chat_id, requested_by, status, updated_at) VALUES (?, ?, ?, 'processing', ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, chat);
                ps.setObject(3, user);
                ps.setTimestamp(4, old);
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void collect_exposesStaleGauge() {
        var samples = new ExportJobsDbCollector(ds, 30).collect();
        var stale = samples.stream()
            .filter(m -> "export_jobs_processing_stale".equals(m.name))
            .findFirst()
            .orElseThrow();
        assertEquals(1.0, stale.samples.getFirst().value, 0.001);
        assertTrue(stale.samples.getFirst().labelNames.isEmpty());
    }
}
