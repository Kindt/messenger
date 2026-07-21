package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformModuleRegistryTest {

    @Test
    void emptyAddonsResolvesBaseOnly() {
        var catalog = ProductModuleCatalogLoader.load();
        var installed = ProductModuleCatalogLoader.resolveInstalledAddons(catalog, "");
        assertTrue(installed.isEmpty());
    }

    @Test
    void explicitAddonsOverrideEmpty() {
        var catalog = ProductModuleCatalogLoader.load();
        var installed = ProductModuleCatalogLoader.resolveInstalledAddons(
            catalog, "addon-search,addon-engage");
        assertEquals(2, installed.size());
        assertTrue(installed.contains("addon-search"));
        assertTrue(installed.contains("addon-engage"));
    }

    @Test
    void notSelectedAddonIsDisabledWithNotSelectedReason() {
        var cfg = baseOnlyConfig();
        var registry = new PlatformModuleRegistry(
            ProductModuleCatalogLoader.load(),
            java.util.List.of(),
            new PlatformModuleOverrideRepository(null),
            cfg
        );
        var state = registry.resolveAddon("addon-search");
        assertEquals(PlatformModuleState.disabled, state.state());
        assertEquals(PlatformModuleReason.not_selected, state.reason());
        assertTrue(!state.selected());
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
            baseOnlyConfig()
        );

        var response = registry.toCapabilitiesResponse();

        var db = response.externalStack().get("relational-db-hot");
        assertEquals("postgres-16", db.desiredConnector());
        assertEquals("passed", db.validationStatus());
        assertTrue(response.externalStack().containsKey("object-storage"));
        assertTrue(response.externalStack().containsKey("messaging"));
        assertEquals("enabled", response.features().get("message.send").state());
    }

    @Test
    void capabilitiesMapModulesToExternalStackRequirements() {
        var cfg = new AppConfig() {
            @Override
            public String korusProductAddons() {
                return "addon-search";
            }
        };
        var registry = PlatformModuleRegistry.create(cfg, new PlatformModuleOverrideRepository(null));

        var response = registry.toCapabilitiesResponse();

        assertTrue(response.product().base().externalStackComponents().contains("relational-db-hot"));
        assertTrue(response.product().base().externalStackProfiles().contains("postgres-16-bundled"));
        var search = response.modules().get("addon-search");
        assertTrue(search.externalStackComponents().contains("search"));
        assertTrue(search.externalStackProfiles().contains("solr-bundled"));
        assertEquals("fallback", search.degradationMode());
        assertEquals("fallback_badge", response.features().get("search.fulltext.messages").uiBehavior());
    }

    @Test
    void productModuleCatalogReferencesKnownExternalStackProfilesAndComponents() {
        var errors = ProductModuleCatalogLoader.validateExternalStackReferences(ProductModuleCatalogLoader.load());

        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    @Test
    void capabilitiesWarnWhenRequiredExternalStackComponentIsDegraded() {
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

        var response = registry.toCapabilitiesResponse();

        assertTrue(response.modules().get("addon-search").externalStackWarnings()
            .contains("required external stack component search is degraded"));
    }

    @Test
    void exportWildcardGateDoesNotMatchExportCompliancePrep() {
        var registry = new PlatformModuleRegistry(
            ProductModuleCatalogLoader.load(),
            java.util.List.of(),
            new PlatformModuleOverrideRepository(null),
            baseOnlyConfig()
        );
        assertEquals(null, registry.apiGateFor("v1/admin/export-compliance-prep", "POST"));
        var jobsGate = registry.apiGateFor("v1/admin/export/jobs", "GET");
        assertTrue(jobsGate != null);
        assertEquals("export.admin.suggest", jobsGate.feature());
    }

    private static AppConfig baseOnlyConfig() {
        return new AppConfig() {
            @Override
            public String korusProductAddons() {
                return "";
            }
        };
    }
}
