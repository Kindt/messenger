package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.platform.dto.PlatformCapabilitiesResponse;
import com.avandocmsg.messenger.api.platform.stack.ExternalStackRuntimeManifestProvider;
import com.avandocmsg.messenger.api.platform.stack.ExternalStackStatusService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlatformModuleRegistry {

    private final ProductModulesCatalog catalog;
    private final Map<String, ProductModulesCatalog.AddonEntry> addonsById;
    private final List<String> installedAddonIds;
    private final PlatformModuleOverrideRepository overrideRepository;
    private final AppConfig appConfig;

    public PlatformModuleRegistry(
        ProductModulesCatalog catalog,
        List<String> installedAddonIds,
        PlatformModuleOverrideRepository overrideRepository,
        AppConfig appConfig
    ) {
        this.catalog = catalog;
        this.addonsById = ProductModuleCatalogLoader.indexAddons(catalog);
        this.installedAddonIds = List.copyOf(installedAddonIds);
        this.overrideRepository = overrideRepository;
        this.appConfig = appConfig;
    }

    public static PlatformModuleRegistry create(AppConfig appConfig, PlatformModuleOverrideRepository overrideRepo) {
        var catalog = ProductModuleCatalogLoader.load();
        var installed = ProductModuleCatalogLoader.resolveInstalledAddons(
            catalog,
            appConfig.korusProductAddons(),
            appConfig.korusDeployProfile()
        );
        return new PlatformModuleRegistry(catalog, installed, overrideRepo, appConfig);
    }

    public List<String> installedAddonIds() {
        return installedAddonIds;
    }

    public ProductModulesCatalog catalog() {
        return catalog;
    }

    public ResolvedAddonState resolveAddon(String addonId) {
        var addon = addonsById.get(addonId);
        if (addon == null) {
            throw new IllegalArgumentException("Unknown add-on: " + addonId);
        }
        var overrides = overrideRepository.findAll();
        return resolveEffectiveState(addon, installedAddonIds.contains(addonId), overrides.get(addonId));
    }

    public Map<String, ResolvedAddonState> resolveAllAddons() {
        var overrides = overrideRepository.findAll();
        var result = new LinkedHashMap<String, ResolvedAddonState>();
        for (var addon : addonsById.values()) {
            result.put(addon.id(), resolveEffectiveState(
                addon,
                installedAddonIds.contains(addon.id()),
                overrides.get(addon.id())
            ));
        }
        return result;
    }

    public PlatformCapabilitiesResponse toCapabilitiesResponse() {
        var resolved = resolveAllAddons();
        var enabledIds = new ArrayList<String>();
        var modules = new LinkedHashMap<String, PlatformCapabilitiesResponse.ModuleSection>();
        var infra = new LinkedHashMap<String, PlatformCapabilitiesResponse.InfraSection>();
        var externalStack = externalStackSummary();

        for (var entry : resolved.entrySet()) {
            var addonId = entry.getKey();
            var state = entry.getValue();
            if (state.state() == PlatformModuleState.enabled) {
                enabledIds.add(addonId);
            }
            var addon = addonsById.get(addonId);
            String mode = null;
            if ("addon-search".equals(addonId)) {
                mode = appConfig.searchMode();
            }
            modules.put(addonId, new PlatformCapabilitiesResponse.ModuleSection(
                state.state().name(),
                state.reason() != null ? state.reason().name() : null,
                addon.label(),
                addon.degradationMode(),
                addon.networkProfile(),
                addon.lifecycleStatus(),
                addon.successorAddonId(),
                mode
            ));
            if (addon.internalInfra() != null) {
                for (var infraId : addon.internalInfra()) {
                    var infraState = installedAddonIds.contains(addonId)
                        && state.state() != PlatformModuleState.disabled
                        ? PlatformModuleState.enabled
                        : PlatformModuleState.disabled;
                    infra.put(infraId, new PlatformCapabilitiesResponse.InfraSection(infraState.name()));
                }
            }
        }

        var base = catalog.base();
        return new PlatformCapabilitiesResponse(
            new PlatformCapabilitiesResponse.ProductSection(
                new PlatformCapabilitiesResponse.BaseProductSection("required", base.label()),
                enabledIds
            ),
            modules,
            infra,
            new PlatformCapabilitiesResponse.BaseMediaSection(true, appConfig.jitsiMeetBaseUrl() != null
                && !appConfig.jitsiMeetBaseUrl().isBlank()),
            externalStack
        );
    }

    private Map<String, PlatformCapabilitiesResponse.ExternalStackSection> externalStackSummary() {
        var provider = new ExternalStackRuntimeManifestProvider(appConfig);
        var status = new ExternalStackStatusService().status(provider.observations());
        var result = new LinkedHashMap<String, PlatformCapabilitiesResponse.ExternalStackSection>();
        for (var entry : status.components().entrySet()) {
            var component = entry.getValue();
            result.put(entry.getKey(), new PlatformCapabilitiesResponse.ExternalStackSection(
                component.desiredConnector(),
                component.healthStatus(),
                component.validationStatus(),
                component.supportBoundary()
            ));
        }
        return result;
    }

    public boolean isAddonEffective(String addonId) {
        return resolveAddon(addonId).state() == PlatformModuleState.enabled;
    }

    ResolvedAddonState resolveEffectiveState(
        ProductModulesCatalog.AddonEntry addon,
        boolean installed,
        PlatformModuleOverrideRow override
    ) {
        if (!appConfig.coreAvailable()) {
            return new ResolvedAddonState(PlatformModuleState.degraded, PlatformModuleReason.core_unavailable);
        }
        if (!installed) {
            return new ResolvedAddonState(PlatformModuleState.disabled, PlatformModuleReason.install);
        }
        if (isEol(addon, override)) {
            return new ResolvedAddonState(PlatformModuleState.disabled, PlatformModuleReason.eol);
        }
        if (secretsMissing(addon)) {
            return new ResolvedAddonState(PlatformModuleState.degraded, PlatformModuleReason.secrets_missing);
        }
        if (override != null && override.disabled() && !override.forceEnabled()) {
            var reason = override.reasonEnum().orElse(PlatformModuleReason.admin_override);
            return new ResolvedAddonState(PlatformModuleState.disabled, reason);
        }
        if (healthStale(addon)) {
            return new ResolvedAddonState(PlatformModuleState.degraded, PlatformModuleReason.health_stale);
        }
        return new ResolvedAddonState(PlatformModuleState.enabled, null);
    }

    private boolean isEol(ProductModulesCatalog.AddonEntry addon, PlatformModuleOverrideRow override) {
        if (!"eol".equalsIgnoreCase(addon.lifecycleStatus())) {
            return false;
        }
        return override == null || !override.forceEnabled();
    }

    private boolean secretsMissing(ProductModulesCatalog.AddonEntry addon) {
        if (addon.secrets() == null || addon.secrets().isEmpty()) {
            return false;
        }
        for (var secret : addon.secrets()) {
            if ("vapid".equals(secret.name())) {
                if (isBlank(System.getenv("PUSH_VAPID_PUBLIC_KEY"))
                    && appConfig.webClientVapidPublicKey().isEmpty()) {
                    return true;
                }
                if (isBlank(System.getenv("PUSH_VAPID_PRIVATE_KEY"))) {
                    return true;
                }
            }
            if ("livekit".equals(secret.name())) {
                if (isBlank(System.getenv("LIVEKIT_API_KEY")) && isBlank(appConfig.livekitApiKey())) {
                    return true;
                }
                if (isBlank(System.getenv("LIVEKIT_API_SECRET")) && isBlank(appConfig.livekitApiSecret())) {
                    return true;
                }
                if (isBlank(System.getenv("LIVEKIT_URL")) && isBlank(appConfig.livekitUrl())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean healthStale(ProductModulesCatalog.AddonEntry addon) {
        if ("addon-search".equals(addon.id()) && !"solr".equalsIgnoreCase(appConfig.searchMode())) {
            return true;
        }
        if ("addon-live".equals(addon.id()) && !appConfig.liveStreamingEnabled()) {
            return true;
        }
        if ("addon-e2ee".equals(addon.id()) && !appConfig.mlsWireEnabled()) {
            return true;
        }
        if ("addon-integrations".equals(addon.id())) {
            var url = appConfig.integrationsBaseUrl();
            if (url == null || url.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record ResolvedAddonState(PlatformModuleState state, PlatformModuleReason reason) {}
}
