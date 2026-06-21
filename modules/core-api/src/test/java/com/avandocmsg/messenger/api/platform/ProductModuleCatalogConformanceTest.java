package com.avandocmsg.messenger.api.platform;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductModuleCatalogConformanceTest {

    private static final Set<String> SPEC_024_ADDONS = Set.of(
        "addon-productivity",
        "addon-engage",
        "addon-search",
        "addon-collaboration",
        "addon-ai",
        "addon-live",
        "addon-retention",
        "addon-archive",
        "addon-deep-archive",
        "addon-export",
        "addon-enterprise-auth",
        "addon-e2ee",
        "addon-bots",
        "addon-integrations",
        "addon-federation",
        "addon-dlp",
        "addon-migration-import"
    );

    @Test
    void catalogV2PassesStrictConformanceChecks() {
        var catalog = ProductModuleCatalogLoader.load();

        assertEquals(2, catalog.schemaVersion());
        assertTrue(ProductModuleCatalogLoader.validateConformance(catalog).isEmpty(),
            String.join("\n", ProductModuleCatalogLoader.validateConformance(catalog)));
    }

    @Test
    void catalogContainsExactlySpec024AddonsAndSubstratesAreNotAddons() {
        var catalog = ProductModuleCatalogLoader.load();
        var addonIds = ProductModuleCatalogLoader.indexAddons(catalog).keySet();

        assertEquals(SPEC_024_ADDONS, addonIds);
        assertTrue(catalog.substrates().stream().anyMatch(s -> s.id().equals("substrate-plugin-platform")));
        assertFalse(addonIds.contains("substrate-plugin-platform"));
    }

    @Test
    void baseAndAddonFeatureOwnersAreUnique() {
        var features = ProductModuleCatalogLoader.indexFeatures(ProductModuleCatalogLoader.load());

        assertEquals("base", features.get("message.send").owner());
        assertEquals("base", features.get("search.sql.messages").owner());
        assertEquals("addon-search", features.get("search.fulltext.messages").owner());
        assertEquals("addon-productivity", features.get("productivity.polls.create").owner());
        assertEquals("addon-collaboration", features.get("collaboration.whiteboard.open").owner());
        assertEquals("addon-ai", features.get("ai.captions.start").owner());
        assertEquals("addon-enterprise-auth", features.get("enterprise.directory_sync.run").owner());
        assertEquals("addon-dlp", features.get("dlp.message_check").owner());
    }
}
