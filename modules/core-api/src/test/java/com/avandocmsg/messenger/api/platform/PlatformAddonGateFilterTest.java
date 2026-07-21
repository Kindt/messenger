package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlatformAddonGateFilterTest {

    @Test
    void disabledAddonApiGateResolvesByFeatureKey() {
        var registry = registry("");

        var gate = registry.apiGateFor("/api/v1/export/jobs", "POST");
        var feature = registry.resolveFeature(gate.feature());

        assertNotNull(gate);
        assertEquals("export.job.create", gate.feature());
        assertEquals(PlatformModuleState.DISABLED, feature.state());
        assertEquals(PlatformModuleReason.NOT_SELECTED, feature.reason());
        assertEquals("hide_export_panels", feature.uiBehavior());
    }

    @Test
    void selectedAddonCanBeInstallingBeforeBundleIsInstalled() {
        var registry = registry(new AppConfig() {
            @Override
            public String korusProductAddons() {
                return "addon-export";
            }

            @Override
            public String korusProductInstalledAddons() {
                return "addon-search";
            }

            @Override
            public String korusProductInstallingAddons() {
                return "addon-export";
            }
        });

        var state = registry.resolveAddon("addon-export");

        assertEquals(PlatformModuleState.INSTALLING, state.state());
        assertEquals(PlatformModuleReason.INSTALL_REQUESTED, state.reason());
    }

    @Test
    void selectedAddonWithMissingSchemaIsDegraded() {
        var registry = registry(new AppConfig() {
            @Override
            public String korusProductAddons() {
                return "addon-export";
            }

            @Override
            public String korusProductInstalledAddons() {
                return "addon-export";
            }

            @Override
            public String korusProductSchemaInstalledAddons() {
                return "addon-search";
            }
        });

        var state = registry.resolveAddon("addon-export");

        assertEquals(PlatformModuleState.DEGRADED, state.state());
        assertEquals(PlatformModuleReason.SCHEMA_MISSING, state.reason());
    }

    @Test
    void readyAddonOpensFeatureGate() {
        var registry = registry("addon-export");

        var feature = registry.resolveFeature("export.job.create");

        assertEquals(PlatformModuleState.ENABLED, feature.state());
    }

    @Test
    void jobAndHookPoliciesComeFromCatalog() {
        var registry = registry("");

        assertEquals("pause", registry.jobGate("retention-purge").disabledBehavior());
        assertEquals("skip", registry.hookGate("message-send-dlp").disabledBehavior());
    }

    @Test
    void adminDisablementClosesAndReenableRestoresWithoutChangingInstallDimensions() {
        var registry = registry("addon-export");
        var addon = ProductModuleCatalogLoader.indexAddons(ProductModuleCatalogLoader.load()).get("addon-export");

        var disabled = registry.resolveEffectiveState(addon, true, new PlatformModuleOverrideRow(
            "addon-export",
            true,
            "admin_override",
            false,
            Instant.now(),
            UUID.randomUUID()
        ));
        var enabled = registry.resolveEffectiveState(addon, true, null);

        assertEquals(PlatformModuleState.DISABLED, disabled.state());
        assertEquals(PlatformModuleReason.ADMIN_OVERRIDE, disabled.reason());
        assertEquals(true, disabled.installed());
        assertEquals(true, disabled.schemaInstalled());
        assertEquals(PlatformModuleState.ENABLED, enabled.state());
    }

    private static PlatformModuleRegistry registry(String addons) {
        return registry(new AppConfig() {
            @Override
            public String korusProductAddons() {
                return addons;
            }
        });
    }

    private static PlatformModuleRegistry registry(AppConfig config) {
        return new PlatformModuleRegistry(
            ProductModuleCatalogLoader.load(),
            ProductModuleCatalogLoader.resolveSelectedAddons(
                ProductModuleCatalogLoader.load(),
                config.korusProductAddons()
            ),
            new PlatformModuleOverrideRepository(null),
            config
        );
    }
}
