package com.avandocmsg.messenger.api.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductModuleMigrationBundleTest {

    @Test
    void everyAddonHasDedicatedOptionalMigrationBundle() {
        var catalog = ProductModuleCatalogLoader.load();

        for (var addon : catalog.addons()) {
            var bundle = addon.migrationBundle();
            assertEquals(addon.id(), bundle.owner(), addon.id());
            assertTrue(bundle.location().startsWith("db/migration/addons/"), addon.id());
            assertTrue(bundle.historyTable().startsWith("flyway_schema_history_addon_"), addon.id());
            assertTrue(bundle.schemaObjects() != null && !bundle.schemaObjects().isEmpty(), addon.id());
        }
    }

    @Test
    void substratesHaveDedicatedHistoryTablesAndAreAppliedBeforeAddons() {
        var catalog = ProductModuleCatalogLoader.load();

        for (var substrate : catalog.substrates()) {
            var bundle = substrate.migrationBundle();
            assertTrue(bundle.location().startsWith("db/migration/substrate/"), substrate.id());
            assertTrue(bundle.historyTable().startsWith("flyway_schema_history_substrate_"), substrate.id());
        }
    }
}
