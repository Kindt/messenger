package com.avandocmsg.messenger.api.plugins;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPolicyServiceH2Test {

    private HikariDataSource ds;
    private PluginRepository repository;
    private PluginPolicyService service;
    private UUID orgId;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:plugin_policy_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (id UUID PRIMARY KEY, name VARCHAR(128) NOT NULL)
                """);
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'test')");
            st.execute("""
                CREATE TABLE org_plugin_policies (
                  org_id UUID PRIMARY KEY,
                  allowed_preset_ids VARCHAR NOT NULL,
                  llm_mode VARCHAR(32) NOT NULL,
                  ocr_on_prem_only BOOLEAN NOT NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        repository = new PluginRepository(ds);
        service = new PluginPolicyService(repository);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void defaultsWhenMissing() {
        var policy = service.getOrDefault(orgId);
        assertEquals("on_prem_only", policy.llmMode());
        assertTrue(policy.ocrOnPremOnly());
        assertTrue(service.isPresetAllowed(orgId, "jira-connector"));
    }

    @Test
    void enforceAllowlistFromStoredRow() throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO org_plugin_policies (org_id, allowed_preset_ids, llm_mode, ocr_on_prem_only, updated_at)
                 VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                 """)) {
            ps.setObject(1, orgId);
            ps.setString(2, "[\"echo-sidecar\",\"1c-bridge\"]");
            ps.setString(3, "hybrid");
            ps.setBoolean(4, false);
            ps.executeUpdate();
        }
        assertFalse(service.isPresetAllowed(orgId, "jira-connector"));
        assertTrue(service.isPresetAllowed(orgId, "echo-sidecar"));
        var snap = new java.util.HashMap<String, Object>();
        service.applyPolicyToSnapshot(snap, orgId);
        assertEquals("hybrid", snap.get("org_llm_mode"));
        assertEquals(false, snap.get("ocr_on_prem_only"));
    }

    @Test
    void updateRejectsInvalidLlmMode() {
        var updated = service.update(orgId, new PluginPolicyService.UpdatePolicyRequest(
            List.of("echo-sidecar"), "invalid_mode", true
        ));
        assertTrue(updated.isEmpty());
    }
}
