package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcOrganizationLookupAdapterFindByIdsH2Test {

    private HikariDataSource ds;
    private JdbcOrganizationLookupAdapter adapter;
    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void setUp() throws Exception {
        orgA = UUID.randomUUID();
        orgB = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:org_lookup_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (
                  id UUID PRIMARY KEY,
                  name VARCHAR(128) NOT NULL,
                  slug VARCHAR(64),
                  created_at TIMESTAMP NOT NULL,
                  logo_file_id UUID,
                  avatar_policy VARCHAR(32) NOT NULL DEFAULT 'visible'
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO organizations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, orgA);
            ps.setString(2, "Org A");
            ps.setString(3, "a");
            ps.setTimestamp(4, java.sql.Timestamp.from(Instant.EPOCH));
            ps.executeUpdate();
            ps.setObject(1, orgB);
            ps.setString(2, "Org B");
            ps.setString(3, "b");
            ps.setTimestamp(4, java.sql.Timestamp.from(Instant.EPOCH));
            ps.executeUpdate();
        }
        adapter = new JdbcOrganizationLookupAdapter(ds, java.time.Clock.systemUTC(), UuidGenerator.standard());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findByIds_returnsAllRequestedInOneQuery() {
        var found = adapter.findByIds(List.of(orgA, orgB, UUID.randomUUID()));
        assertEquals(2, found.size());
        assertEquals("Org A", found.get(orgA).name());
        assertEquals("Org B", found.get(orgB).name());
        assertTrue(adapter.findByIds(List.of()).isEmpty());
    }
}
