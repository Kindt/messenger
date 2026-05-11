package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyRepositoryH2Test {

    private HikariDataSource ds;
    private RetentionPolicyRepository repo;
    private UUID orgId;

    @BeforeEach
    void init() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:retpol_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (
                  id UUID PRIMARY KEY,
                  name VARCHAR(256) NOT NULL
                )
                """);
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY
                )
                """);
            st.execute("""
                CREATE TABLE org_retention_policy (
                  org_id UUID PRIMARY KEY REFERENCES organizations(id),
                  hot_message_body_max_age_days INT,
                  hot_metadata_min_age_days INT,
                  archive_metadata_enabled BOOLEAN NOT NULL DEFAULT true,
                  deep_archive_enabled BOOLEAN NOT NULL DEFAULT true,
                  legal_hold BOOLEAN NOT NULL DEFAULT false,
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_by UUID REFERENCES users(id)
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, 't')")) {
            ps.setObject(1, orgId);
            ps.executeUpdate();
        }
        repo = new RetentionPolicyRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findByOrgId_emptyWhenNoRow() {
        assertTrue(repo.findByOrgId(orgId).isEmpty());
    }

    @Test
    void upsert_insertsThenUpdates() throws Exception {
        var userId = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
        assertTrue(repo.upsert(orgId, 10, 2, true, false, true, userId));
        var first = repo.findByOrgId(orgId).orElseThrow();
        assertEquals(10, first.hotMessageBodyMaxAgeDays());
        assertEquals(2, first.hotMetadataMinAgeDays());
        assertTrue(first.archiveMetadataEnabled());
        assertFalse(first.deepArchiveEnabled());
        assertTrue(first.legalHold());
        assertEquals(userId.toString(), first.updatedBy());

        assertTrue(repo.upsert(orgId, null, null, false, true, false, userId));
        var second = repo.findByOrgId(orgId).orElseThrow();
        assertNull(second.hotMessageBodyMaxAgeDays());
        assertNull(second.hotMetadataMinAgeDays());
        assertFalse(second.archiveMetadataEnabled());
        assertTrue(second.deepArchiveEnabled());
        assertFalse(second.legalHold());
    }

    @Test
    void findByOrgId_returnsRow() throws Exception {
        var userId = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
        var ts = Instant.parse("2025-01-02T00:00:00Z");
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO org_retention_policy (
                   org_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                   archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
                 ) VALUES (?, 30, NULL, false, true, false, ?, ?)
                 """)) {
            ps.setObject(1, orgId);
            ps.setObject(2, java.sql.Timestamp.from(ts));
            ps.setObject(3, userId);
            ps.executeUpdate();
        }
        var opt = repo.findByOrgId(orgId);
        assertTrue(opt.isPresent());
        var r = opt.get();
        assertEquals(orgId, r.orgId());
        assertEquals(30, r.hotMessageBodyMaxAgeDays());
        assertNull(r.hotMetadataMinAgeDays());
        assertFalse(r.archiveMetadataEnabled());
        assertTrue(r.deepArchiveEnabled());
        assertFalse(r.legalHold());
        assertEquals(userId.toString(), r.updatedBy());
    }
}
