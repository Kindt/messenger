package com.avandocmsg.messenger.api.auth.policy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyRepositoryH2Test {

    private HikariDataSource ds;
    private AuthPolicyRepository repo;
    private UUID orgId;

    @BeforeEach
    void init() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:authpolicy_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
                CREATE TABLE org_auth_policy (
                  org_id UUID PRIMARY KEY,
                  allow_local_password BOOLEAN NOT NULL DEFAULT TRUE,
                  allow_self_registration BOOLEAN NOT NULL DEFAULT FALSE,
                  providers_json VARCHAR(10000) NOT NULL DEFAULT '[]',
                  last_apply_status VARCHAR(32),
                  last_apply_error VARCHAR(2000),
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_by UUID
                )
                """);
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test Org')");
        }
        repo = new AuthPolicyRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void defaultPolicy_whenMissing() {
        assertTrue(repo.findByOrgId(orgId).isEmpty());
        var d = repo.defaultPolicy(orgId);
        assertTrue(d.allowLocalPassword());
        assertFalse(d.allowSelfRegistration());
        assertTrue(d.providers().isEmpty());
    }

    @Test
    void upsert_roundTrip() {
        var provider = new AuthProviderEntry(
            "ldap1", "ldap", "corp-ldap", "Corporate LDAP", 0, true, null, "draft", null,
            java.util.Map.of("connection_url", "ldap://ldap:389"));
        var row = new OrgAuthPolicyRow(orgId, false, true, List.of(provider), "ok", null,
            java.time.Instant.EPOCH, null);
        repo.upsert(row);
        var loaded = repo.findByOrgId(orgId).orElseThrow();
        assertFalse(loaded.allowLocalPassword());
        assertTrue(loaded.allowSelfRegistration());
        assertEquals(1, loaded.providers().size());
        assertEquals("corp-ldap", loaded.providers().get(0).alias());
    }
}
