package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationRepositoryH2Test {

    private HikariDataSource ds;
    private OrganizationRepository repo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:orgrepo_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (
                  id UUID PRIMARY KEY,
                  name VARCHAR(256) NOT NULL,
                  slug VARCHAR(64),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  org_id UUID REFERENCES organizations(id)
                )
                """);
        }
        repo = new OrganizationRepository(ds, Clock.systemUTC(), UuidGenerator.standard());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findById_emptyWhenMissing() {
        assertTrue(repo.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findById_returnsInsertedRow() throws Exception {
        var id = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, "Acme Ltd");
            ps.executeUpdate();
        }
        var row = repo.findById(id).orElseThrow();
        assertEquals(id.toString(), row.id());
        assertEquals("Acme Ltd", row.name());
        assertNotNull(row.createdAt());
    }

    @Test
    void create_then_findById_matchesName() {
        var fixedId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UuidGenerator gen = () -> fixedId;
        var clock = Clock.fixed(Instant.parse("2020-01-02T03:04:05Z"), ZoneOffset.UTC);
        var r = new OrganizationRepository(ds, clock, gen);
        var created = r.create("  TrimMe  ");
        assertNotNull(created);
        assertEquals("TrimMe", created.name());
        assertEquals(fixedId.toString(), created.id());
        assertEquals(Instant.parse("2020-01-02T03:04:05Z"), created.createdAt());
        var fromDb = r.findById(fixedId).orElseThrow();
        assertEquals("TrimMe", fromDb.name());
        assertEquals(fixedId.toString(), fromDb.id());
    }

    @Test
    void deleteIfUnused_removesOrgWhenNoMembers() throws Exception {
        var id = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, "solo");
            ps.executeUpdate();
        }
        assertTrue(repo.deleteIfUnused(id));
        assertTrue(repo.findById(id).isEmpty());
    }

    @Test
    void deleteIfUnused_falseWhenUserReferencesOrg() throws Exception {
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        try (var c = ds.getConnection()) {
            try (var ps = c.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)")) {
                ps.setObject(1, orgId);
                ps.setString(2, "busy");
                ps.executeUpdate();
            }
            try (var ps = c.prepareStatement("INSERT INTO users (id, org_id) VALUES (?, ?)")) {
                ps.setObject(1, userId);
                ps.setObject(2, orgId);
                ps.executeUpdate();
            }
        }
        assertFalse(repo.deleteIfUnused(orgId));
        assertTrue(repo.findById(orgId).isPresent());
    }
}
