package com.avandocmsg.messenger.api.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public static List<String> resolveInstalledAddons(
        ProductModulesCatalog catalog,
        String explicitAddonsCsv,
        String legacyDeployProfile
    ) {
        if (explicitAddonsCsv != null && !explicitAddonsCsv.isBlank()) {
            return parseCsv(explicitAddonsCsv);
        }
        if (legacyDeployProfile != null && !legacyDeployProfile.isBlank()
            && catalog.legacyDeployProfileMap() != null) {
            var entry = catalog.legacyDeployProfileMap().get(legacyDeployProfile.trim().toLowerCase());
            if (entry != null && entry.addons() != null) {
                return List.copyOf(entry.addons());
            }
        }
        return List.of();
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
}
