package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.UiBrandingPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUiBrandingAdapterH2Test {
    private HikariDataSource ds;
    private UiBrandingPort adapter;
    private UUID orgId;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:branding_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (
                    id UUID PRIMARY KEY,
                    name VARCHAR(256) NOT NULL
                )
                """);
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'org-1')");
            st.execute("""
                CREATE TABLE platform_ui_branding (
                    id BIGINT PRIMARY KEY,
                    palette VARCHAR(32) NOT NULL,
                    token_overrides JSON NOT NULL DEFAULT '{}',
                    custom_css TEXT,
                    brand_title VARCHAR(256),
                    demo_skins_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    revision BIGINT NOT NULL DEFAULT 1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE org_ui_branding (
                    org_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
                    palette VARCHAR(32),
                    token_overrides JSON NOT NULL DEFAULT '{}',
                    custom_css TEXT,
                    brand_title VARCHAR(256),
                    revision BIGINT NOT NULL DEFAULT 1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                INSERT INTO platform_ui_branding (id, palette, token_overrides, custom_css, brand_title, demo_skins_enabled, revision)
                VALUES (1, 'korus', '{}', NULL, 'Korus', FALSE, 1)
                """);
        }
        adapter = new JdbcUiBrandingAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void upsertAndMergeBranding() {
        var platform = adapter.upsertPlatform(
            "vtb",
            Map.of("--ui-bg", "#001122"),
            ".layout{padding:8px;}",
            "Platform",
            true
        );
        assertEquals("vtb", platform.palette());
        assertTrue(platform.revision() >= 2);

        var org = adapter.upsertOrg(
            orgId,
            "alfa",
            Map.of("--ui-bg", "#ffeeaa"),
            ".org{margin:0;}",
            "Org title"
        );
        assertEquals("alfa", org.palette());
        assertTrue(org.revision() >= 1);

        var merged = adapter.mergeForOrg(orgId, "https://cdn/logo.svg");
        assertEquals("alfa", merged.palette());
        assertEquals("#ffeeaa", merged.tokenOverrides().get("--ui-bg"));
        assertEquals(".org{margin:0;}", merged.customCss());
        assertEquals("Org title", merged.brandTitle());
        assertEquals("https://cdn/logo.svg", merged.logoUrl());
        assertTrue(merged.demoSkinsEnabled());
    }
}
