package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.platform.stack.ConnectorCompatibilityPacks;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProductModuleCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(ProductModuleCatalogLoader.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private ProductModuleCatalogLoader() {}

    public static ProductModulesCatalog load() {
        try (InputStream is = ProductModuleCatalogLoader.class.getClassLoader()
            .getResourceAsStream("product-modules.yaml")) {
            if (is == null) {
                throw new IllegalStateException("product-modules.yaml not found on classpath");
            }
            return YAML.readValue(is, ProductModulesCatalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load product-modules.yaml", e);
        }
    }

    public static Map<String, ProductModulesCatalog.AddonEntry> indexAddons(ProductModulesCatalog catalog) {
        if (catalog.addons() == null) {
            return Map.of();
        }
        Map<String, ProductModulesCatalog.AddonEntry> map = new LinkedHashMap<>();
        for (var addon : catalog.addons()) {
            map.put(addon.id(), addon);
        }
        return Collections.unmodifiableMap(map);
    }

    public static Map<String, ProductModulesCatalog.FeatureEntry> indexFeatures(ProductModulesCatalog catalog) {
        Map<String, ProductModulesCatalog.FeatureEntry> map = new LinkedHashMap<>();
        if (catalog.base() != null) {
            for (var feature : safeList(catalog.base().features())) {
                map.put(feature.key(), feature);
            }
        }
        for (var substrate : safeList(catalog.substrates())) {
            for (var feature : safeList(substrate.features())) {
                map.put(feature.key(), feature);
            }
        }
        for (var addon : safeList(catalog.addons())) {
            for (var feature : safeList(addon.features())) {
                map.put(feature.key(), feature);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static List<String> resolveInstalledAddons(
        ProductModulesCatalog catalog,
        String explicitAddonsCsv
    ) {
        if (explicitAddonsCsv != null && !explicitAddonsCsv.isBlank()) {
            return parseCsv(explicitAddonsCsv);
        }
        return List.of();
    }

    public static List<String> resolveSelectedAddons(
        ProductModulesCatalog catalog,
        String explicitAddonsCsv
    ) {
        return resolveInstalledAddons(catalog, explicitAddonsCsv);
    }

    private static List<String> parseCsv(String csv) {
        return java.util.Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public static Optional<ProductModulesCatalog.AddonEntry> findAddon(
        ProductModulesCatalog catalog,
        String addonId
    ) {
        return catalog.addons() == null ? Optional.empty()
            : catalog.addons().stream().filter(a -> a.id().equals(addonId)).findFirst();
    }

    public static List<String> validateExternalStackReferences(ProductModulesCatalog catalog) {
        var errors = new java.util.ArrayList<String>();
        var components = ConnectorCompatibilityPacks.catalog().stream()
            .map(pack -> pack.component())
            .collect(Collectors.toSet());
        validateReferences("base", safeList(catalog.base().externalStackComponents()),
            safeList(catalog.base().externalStackProfiles()), components, errors);
        for (var addon : safeList(catalog.addons())) {
            validateReferences(addon.id(), safeList(addon.externalStackComponents()),
                safeList(addon.externalStackProfiles()), components, errors);
        }
        return List.copyOf(errors);
    }

    public static List<String> validateConformance(ProductModulesCatalog catalog) {
        var errors = new java.util.ArrayList<String>();
        if (catalog.schemaVersion() != 2) {
            errors.add("schema_version must be 2");
        }
        var ownerByFeature = new LinkedHashMap<String, String>();
        if (catalog.base() == null || catalog.base().features() == null || catalog.base().features().isEmpty()) {
            errors.add("base must declare feature ownership");
        } else {
            addFeatureOwners("base", catalog.base().features(), ownerByFeature, errors);
        }
        for (var substrate : safeList(catalog.substrates())) {
            addFeatureOwners(substrate.id(), substrate.features(), ownerByFeature, errors);
            validateBundle(substrate.id(), substrate.migrationBundle(), "substrate", errors);
        }
        var addonIds = new HashSet<String>();
        for (var addon : safeList(catalog.addons())) {
            addonIds.add(addon.id());
            if (isBlank(addon.degradationMode())) {
                errors.add(addon.id() + ": degradation_mode is required");
            }
            if (isBlank(addon.disabledBehavior()) || isBlank(addon.degradedBehavior())
                || isBlank(addon.installingBehavior())) {
                errors.add(addon.id() + ": disabled/degraded/installing behavior is required");
            }
            addFeatureOwners(addon.id(), addon.features(), ownerByFeature, errors);
            validateBundle(addon.id(), addon.migrationBundle(), "addon", errors);
            validateGates(addon, ownerByFeature, errors);
            validateAcceptance(addon, errors);
        }
        errors.addAll(validateExternalStackReferences(catalog));
        return List.copyOf(errors);
    }

    private static void addFeatureOwners(
        String expectedOwner,
        List<ProductModulesCatalog.FeatureEntry> features,
        Map<String, String> ownerByFeature,
        List<String> errors
    ) {
        for (var feature : safeList(features)) {
            if (isBlank(feature.key())) {
                errors.add(expectedOwner + ": feature key is required");
                continue;
            }
            var owner = isBlank(feature.owner()) ? expectedOwner : feature.owner();
            if (!expectedOwner.equals(owner)) {
                errors.add(feature.key() + ": owner " + owner + " does not match " + expectedOwner);
            }
            var previous = ownerByFeature.putIfAbsent(feature.key(), owner);
            if (previous != null) {
                errors.add(feature.key() + ": duplicate owner " + previous + " and " + owner);
            }
        }
    }

    private static void validateBundle(
        String owner,
        ProductModulesCatalog.MigrationBundleEntry bundle,
        String kind,
        List<String> errors
    ) {
        if (bundle == null) {
            errors.add(owner + ": migration_bundle is required");
            return;
        }
        if (!owner.equals(bundle.owner())) {
            errors.add(owner + ": migration_bundle owner mismatch");
        }
        if (isBlank(bundle.historyTable())) {
            errors.add(owner + ": migration_bundle history_table is required");
        }
        if ("addon".equals(kind) && !bundle.historyTable().startsWith("flyway_schema_history_addon_")) {
            errors.add(owner + ": add-on bundle must use dedicated add-on history table");
        }
        if ("substrate".equals(kind) && !bundle.historyTable().startsWith("flyway_schema_history_substrate_")) {
            errors.add(owner + ": substrate bundle must use dedicated substrate history table");
        }
    }

    private static void validateGates(
        ProductModulesCatalog.AddonEntry addon,
        Map<String, String> ownerByFeature,
        List<String> errors
    ) {
        if (addon.gates() == null) {
            errors.add(addon.id() + ": gates are required");
            return;
        }
        for (var gate : safeList(addon.gates().api())) {
            validateGateFeature(addon.id(), gate.feature(), ownerByFeature, errors);
        }
        for (var gate : safeList(addon.gates().ui())) {
            validateGateFeature(addon.id(), gate.feature(), ownerByFeature, errors);
        }
        for (var gate : safeList(addon.gates().jobs())) {
            validateGateFeature(addon.id(), gate.feature(), ownerByFeature, errors);
        }
        for (var gate : safeList(addon.gates().hooks())) {
            validateGateFeature(addon.id(), gate.feature(), ownerByFeature, errors);
        }
    }

    private static void validateGateFeature(
        String addonId,
        String featureKey,
        Map<String, String> ownerByFeature,
        List<String> errors
    ) {
        if (isBlank(featureKey)) {
            errors.add(addonId + ": gate feature is required");
            return;
        }
        var owner = ownerByFeature.get(featureKey);
        if (!addonId.equals(owner)) {
            errors.add(addonId + ": gate feature " + featureKey + " is owned by " + owner);
        }
    }

    private static void validateAcceptance(ProductModulesCatalog.AddonEntry addon, List<String> errors) {
        if (addon.acceptance() == null
            || safeList(addon.acceptance().positive()).isEmpty()
            || (safeList(addon.acceptance().disabled()).isEmpty()
                && safeList(addon.acceptance().degraded()).isEmpty())) {
            errors.add(addon.id() + ": positive and disabled/degraded acceptance coverage is required");
        }
    }

    private static void validateReferences(
        String owner,
        List<String> componentIds,
        List<String> profileIds,
        Set<String> knownComponents,
        List<String> errors
    ) {
        for (var componentId : componentIds) {
            if (!knownComponents.contains(componentId)) {
                errors.add(owner + ": unknown external_stack_component " + componentId);
            }
        }
        for (var profileId : profileIds) {
            try {
                ConnectorCompatibilityPacks.packFor(profileId);
            } catch (IllegalArgumentException e) {
                errors.add(owner + ": unknown external_stack_profile " + profileId);
            }
        }
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
