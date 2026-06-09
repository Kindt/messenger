package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcOrganizationRepositoryAdapterH2Test {

    private HikariDataSource ds;
    private JdbcOrganizationRepositoryAdapter adapter;
    private UUID orgId;

    @BeforeEach
    void init() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:hex_org_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (
                  id UUID PRIMARY KEY,
                  name VARCHAR(256) NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO organizations (id, name, created_at) VALUES (?, ?, ?)")) {
            ps.setObject(1, orgId);
            ps.setString(2, "Hex Org");
            ps.setTimestamp(3, java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            ps.executeUpdate();
        }
        adapter = new JdbcOrganizationRepositoryAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void exists_and_findById() {
        assertTrue(adapter.exists(OrganizationId.of(orgId)));
        var org = adapter.findById(OrganizationId.of(orgId)).orElseThrow();
        assertEquals("Hex Org", org.name());
    }

    @Test
    void findById_emptyWhenMissing() {
        assertTrue(adapter.findById(OrganizationId.of(UUID.randomUUID())).isEmpty());
    }
}
