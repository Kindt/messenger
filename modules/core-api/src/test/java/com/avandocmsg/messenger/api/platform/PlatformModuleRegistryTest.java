package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformModuleRegistryTest {

    @Test
    void pilotProfileResolvesBaseOnly() {
        var catalog = ProductModuleCatalogLoader.load();
        var installed = ProductModuleCatalogLoader.resolveInstalledAddons(catalog, "", "pilot");
        assertTrue(installed.isEmpty());
    }

    @Test
    void explicitAddonsOverrideLegacyProfile() {
        var catalog = ProductModuleCatalogLoader.load();
        var installed = ProductModuleCatalogLoader.resolveInstalledAddons(
            catalog, "addon-search,addon-engage", "pilot");
        assertEquals(2, installed.size());
        assertTrue(installed.contains("addon-search"));
        assertTrue(installed.contains("addon-engage"));
    }

    @Test
    void notInstalledAddonIsDisabledWithInstallReason() {
        var cfg = pilotConfig();
        var registry = new PlatformModuleRegistry(
            ProductModuleCatalogLoader.load(),
            java.util.List.of(),
            new PlatformModuleOverrideRepository(null),
            cfg
        );
        var state = registry.resolveAddon("addon-search");
        assertEquals(PlatformModuleState.disabled, state.state());
        assertEquals(PlatformModuleReason.install, state.reason());
    }

    @Test
    void installedSearchDegradedWhenSqlMode() {
        var cfg = new AppConfig() {
            @Override
            public String korusProductAddons() {
                return "addon-search";
            }

            @Override
            public String searchMode() {
                return "sql";
            }
        };
        var registry = PlatformModuleRegistry.create(cfg, new PlatformModuleOverrideRepository(null));
        var state = registry.resolveAddon("addon-search");
        assertEquals(PlatformModuleState.degraded, state.state());
        assertEquals(PlatformModuleReason.health_stale, state.reason());
    }

    @Test
    void capabilitiesExposeExternalStackSummary() {
        var registry = new PlatformModuleRegistry(
            ProductModuleCatalogLoader.load(),
            java.util.List.of(),
            new PlatformModuleOverrideRepository(null),
            pilotConfig()
        );

        var response = registry.toCapabilitiesResponse();

        var db = response.externalStack().get("relational-db-hot");
        assertEquals("postgres-16", db.desiredConnector());
        assertEquals("passed", db.validationStatus());
        assertTrue(response.externalStack().containsKey("object-storage"));
        assertTrue(response.externalStack().containsKey("messaging"));
    }

    private static AppConfig pilotConfig() {
        return new AppConfig() {
            @Override
            public String deployProfile() {
                return "pilot";
            }

            @Override
            public String korusDeployProfile() {
                return "pilot";
            }

            @Override
            public String korusProductAddons() {
                return "";
            }
        };
    }
}
