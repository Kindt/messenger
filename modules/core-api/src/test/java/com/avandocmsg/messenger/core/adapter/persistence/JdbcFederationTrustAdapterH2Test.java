package com.avandocmsg.messenger.core.adapter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcFederationTrustAdapterH2Test {

    private HikariDataSource ds;
    private JdbcFederationTrustAdapter adapter;
    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void setUp() throws Exception {
        orgA = UUID.randomUUID();
        orgB = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:fed_trust_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (id UUID PRIMARY KEY, name VARCHAR(128) NOT NULL)
                """);
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgA + "', 'A')");
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgB + "', 'B')");
            st.execute("""
                CREATE TABLE federation_trust (
                  id UUID PRIMARY KEY,
                  org_id UUID NOT NULL,
                  partner_org_id UUID NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'active',
                  expires_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT uq_federation_trust_pair UNIQUE (org_id, partner_org_id)
                )
                """);
        }
        adapter = new JdbcFederationTrustAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insertAndTrustCheck() {
        var id = adapter.insert(orgA, orgB, "active", null);
        assertTrue(id != null);
        assertTrue(adapter.isTrusted(orgA, orgB));
        assertTrue(adapter.anyActiveTrust());
        assertEquals(1, adapter.listActiveForOrg(orgA).size());
    }

    @Test
    void listActiveForOrg_respectsLimitCap() throws Exception {
        for (int i = 0; i < 3; i++) {
            var partner = UUID.randomUUID();
            try (var c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("INSERT INTO organizations (id, name) VALUES ('" + partner + "', 'P" + i + "')");
            }
            adapter.insert(orgA, partner, "active", null);
        }
        assertEquals(3, adapter.listActiveForOrg(orgA).size());
    }
}
