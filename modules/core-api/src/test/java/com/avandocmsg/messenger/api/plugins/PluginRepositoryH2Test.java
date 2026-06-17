package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRepositoryH2Test {

    private HikariDataSource ds;
    private PluginRepository repository;
    private UUID orgId;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:plugin_repo_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE organizations (id UUID PRIMARY KEY, name VARCHAR(128) NOT NULL)");
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'test')");
            st.execute("""
                CREATE TABLE plugin_presets (
                  id VARCHAR(64) PRIMARY KEY,
                  plugin_class VARCHAR(8) NOT NULL,
                  runtime_kind VARCHAR(16) NOT NULL,
                  config_schema_version INT NOT NULL DEFAULT 1,
                  capabilities VARCHAR NOT NULL DEFAULT '[]'
                )
                """);
            st.execute("INSERT INTO plugin_presets (id, plugin_class, runtime_kind) VALUES ('l0-faq-menu', 'L0', 'config')");
            st.execute("""
                CREATE TABLE plugin_instances (
                  id UUID PRIMARY KEY,
                  org_id UUID NOT NULL,
                  preset_id VARCHAR(64) NOT NULL,
                  bot_name VARCHAR(64) NOT NULL,
                  display_name VARCHAR(128) NOT NULL,
                  enabled BOOLEAN NOT NULL DEFAULT true,
                  plugin_class VARCHAR(8) NOT NULL,
                  runtime_endpoint VARCHAR(512),
                  config_json VARCHAR NOT NULL DEFAULT '{}',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  outbound_target_chat_id UUID,
                  outbound_actor_user_id UUID,
                  outbound_token_hash VARCHAR(128),
                  CONSTRAINT uq_plugin_instances_org_bot UNIQUE (org_id, bot_name)
                )
                """);
        }
        repository = new PluginRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findByOrgBotAndPagination() {
        for (int i = 0; i < 5; i++) {
            var row = new PluginRepository.InstanceRow(
                UUID.randomUUID(),
                orgId,
                "l0-faq-menu",
                "faq-" + i,
                "FAQ " + i,
                true,
                "L0",
                null,
                JsonNodeFactory.instance.objectNode(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null
            );
            assertTrue(repository.insertInstance(row));
        }
        assertEquals(5, repository.countInstances(orgId));
        assertTrue(repository.findInstanceByOrgAndBotName(orgId, "faq-2").isPresent());
        assertFalse(repository.findInstanceByOrgAndBotName(orgId, "missing").isPresent());

        var page = repository.listInstances(orgId, 2, 1);
        assertEquals(5, page.total());
        assertEquals(2, page.rows().size());
        assertEquals("faq-1", page.rows().get(0).botName());
    }

    @Test
    void setInstanceEnabled() {
        var id = UUID.randomUUID();
        var row = new PluginRepository.InstanceRow(
            id, orgId, "l0-faq-menu", "hr-faq", "HR FAQ", true, "L0", null,
            JsonNodeFactory.instance.objectNode(), Instant.now(), Instant.now(), null, null, null
        );
        assertTrue(repository.insertInstance(row));
        assertTrue(repository.setInstanceEnabled(id, false));
        assertFalse(repository.findInstance(id).orElseThrow().enabled());
    }

    @Test
    void crossTenantInstancesAreIsolated() {
        var otherOrg = UUID.randomUUID();
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + otherOrg + "', 'other')");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var orgBot = new PluginRepository.InstanceRow(
            UUID.randomUUID(), orgId, "l0-faq-menu", "shared-name", "Org A", true, "L0", null,
            JsonNodeFactory.instance.objectNode(), Instant.now(), Instant.now(), null, null, null
        );
        var otherBot = new PluginRepository.InstanceRow(
            UUID.randomUUID(), otherOrg, "l0-faq-menu", "shared-name", "Org B", true, "L0", null,
            JsonNodeFactory.instance.objectNode(), Instant.now(), Instant.now(), null, null, null
        );
        assertTrue(repository.insertInstance(orgBot));
        assertTrue(repository.insertInstance(otherBot));
        assertEquals(1, repository.countInstances(orgId));
        assertEquals(1, repository.countInstances(otherOrg));
        assertTrue(repository.findInstanceByOrgAndBotName(orgId, "shared-name").isPresent());
        assertTrue(repository.findInstanceByOrgAndBotName(otherOrg, "shared-name").isPresent());
        assertNotEquals(
            repository.findInstanceByOrgAndBotName(orgId, "shared-name").orElseThrow().id(),
            repository.findInstanceByOrgAndBotName(otherOrg, "shared-name").orElseThrow().id());
    }
}
