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

    private static final String API_ALLOW = "allow";

    private final ProductModulesCatalog catalog;
    private final Map<String, ProductModulesCatalog.AddonEntry> addonsById;
    private final Map<String, ProductModulesCatalog.FeatureEntry> featuresByKey;
    private final List<String> selectedAddonIds;
    private final PlatformModuleOverrideRepository overrideRepository;
    private final AppConfig appConfig;

    public PlatformModuleRegistry(
        ProductModulesCatalog catalog,
        List<String> selectedAddonIds,
        PlatformModuleOverrideRepository overrideRepository,
        AppConfig appConfig
    ) {
        this.catalog = catalog;
        this.addonsById = ProductModuleCatalogLoader.indexAddons(catalog);
        this.featuresByKey = ProductModuleCatalogLoader.indexFeatures(catalog);
        this.selectedAddonIds = List.copyOf(selectedAddonIds);
        this.overrideRepository = overrideRepository;
        this.appConfig = appConfig;
    }

    public static PlatformModuleRegistry create(AppConfig appConfig, PlatformModuleOverrideRepository overrideRepo) {
        var catalog = ProductModuleCatalogLoader.load();
        var selected = ProductModuleCatalogLoader.resolveSelectedAddons(
            catalog,
            appConfig.korusProductAddons()
        );
        return new PlatformModuleRegistry(catalog, selected, overrideRepo, appConfig);
    }

    public List<String> installedAddonIds() {
        return selectedAddonIds;
    }

    public List<String> selectedAddonIds() {
        return selectedAddonIds;
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
        return resolveEffectiveState(addon, selectedAddonIds.contains(addonId), overrides.get(addonId));
    }

    public Map<String, ResolvedAddonState> resolveAllAddons() {
        var overrides = overrideRepository.findAll();
        var result = new LinkedHashMap<String, ResolvedAddonState>();
        for (var addon : addonsById.values()) {
            result.put(addon.id(), resolveEffectiveState(
                addon,
                selectedAddonIds.contains(addon.id()),
                overrides.get(addon.id())
            ));
        }
        return result;
    }

    public PlatformCapabilitiesResponse toCapabilitiesResponse() {
        var resolved = resolveAllAddons();
        var enabledIds = new ArrayList<String>();
        var modules = new LinkedHashMap<String, PlatformCapabilitiesResponse.ModuleSection>();
        var features = new LinkedHashMap<String, PlatformCapabilitiesResponse.FeatureSection>();
        var infra = new LinkedHashMap<String, PlatformCapabilitiesResponse.InfraSection>();
        var externalStack = externalStackSummary();

        for (var entry : resolved.entrySet()) {
            appendAddonCapabilities(entry.getKey(), entry.getValue(), enabledIds, modules, features, infra, externalStack);
        }

        var base = catalog.base();
        for (var feature : safeList(base.features())) {
            features.put(feature.key(), new PlatformCapabilitiesResponse.FeatureSection(
                "base",
                appConfig.coreAvailable() ? PlatformModuleState.ENABLED.code() : PlatformModuleState.DEGRADED.code(),
                appConfig.coreAvailable() ? null : PlatformModuleReason.CORE_UNAVAILABLE.code(),
                valueOr(feature.uiBehavior(), "show"),
                feature.apiBehavior() != null ? valueOr(feature.apiBehavior().mode(), API_ALLOW) : API_ALLOW
            ));
        }
        return new PlatformCapabilitiesResponse(
            new PlatformCapabilitiesResponse.ProductSection(
                new PlatformCapabilitiesResponse.BaseProductSection(
                    "required",
                    base.label(),
                    safeList(base.externalStackComponents()),
                    safeList(base.externalStackProfiles())
                ),
                enabledIds
            ),
            modules,
            features,
            infra,
            new PlatformCapabilitiesResponse.BaseMediaSection(true, appConfig.jitsiMeetBaseUrl() != null
                && !appConfig.jitsiMeetBaseUrl().isBlank()),
            externalStack
        );
    }


    private void appendAddonCapabilities(
        String addonId,
        ResolvedAddonState state,
        List<String> enabledIds,
        Map<String, PlatformCapabilitiesResponse.ModuleSection> modules,
        Map<String, PlatformCapabilitiesResponse.FeatureSection> features,
        Map<String, PlatformCapabilitiesResponse.InfraSection> infra,
        Map<String, PlatformCapabilitiesResponse.ExternalStackSection> externalStack
    ) {
        if (state.state() == PlatformModuleState.ENABLED) {
            enabledIds.add(addonId);
        }
        var addon = addonsById.get(addonId);
        String mode = "addon-search".equals(addonId) ? appConfig.searchMode() : null;
        modules.put(addonId, new PlatformCapabilitiesResponse.ModuleSection(
            state.selected(),
            state.installed(),
            state.schemaInstalled(),
            state.runtimeReady(),
            state.adminEnabled(),
            state.state().code(),
            state.reason() != null ? state.reason().code() : null,
            addon.label(),
            addon.degradationMode(),
            firstUiBehavior(addon),
            addon.networkProfile(),
            addon.lifecycleStatus(),
            addon.successorAddonId(),
            mode,
            safeList(addon.externalStackComponents()),
            safeList(addon.externalStackProfiles()),
            externalStackWarnings(addon, state, externalStack)
        ));
        if (addon.internalInfra() != null) {
            for (var infraId : addon.internalInfra()) {
                var infraState = selectedAddonIds.contains(addonId)
                    && state.state() != PlatformModuleState.DISABLED
                    ? PlatformModuleState.ENABLED
                    : PlatformModuleState.DISABLED;
                infra.put(infraId, new PlatformCapabilitiesResponse.InfraSection(infraState.code()));
            }
        }
        for (var feature : safeList(addon.features())) {
            features.put(feature.key(), featureSection(feature, state));
        }
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

    private List<String> externalStackWarnings(
        ProductModulesCatalog.AddonEntry addon,
        ResolvedAddonState state,
        Map<String, PlatformCapabilitiesResponse.ExternalStackSection> externalStack
    ) {
        var warnings = new ArrayList<String>();
        for (var componentId : safeList(addon.externalStackComponents())) {
            var component = externalStack.get(componentId);
            if (component == null
                || !"passed".equals(component.validationStatus())
                || "degraded".equals(component.healthStatus())
                || state.state() == PlatformModuleState.DEGRADED) {
                warnings.add("required external stack component " + componentId + " is degraded");
            }
        }
        return List.copyOf(warnings);
    }

    public boolean isAddonEffective(String addonId) {
        return resolveAddon(addonId).state() == PlatformModuleState.ENABLED;
    }

    public ResolvedFeatureState resolveFeature(String featureKey) {
        var feature = featuresByKey.get(featureKey);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature: " + featureKey);
        }
        var owner = feature.owner();
        if ("base".equals(owner)) {
            var state = appConfig.coreAvailable() ? PlatformModuleState.ENABLED : PlatformModuleState.DEGRADED;
            var reason = appConfig.coreAvailable() ? null : PlatformModuleReason.CORE_UNAVAILABLE;
            return new ResolvedFeatureState(featureKey, owner, state, reason, feature.uiBehavior(), apiMode(feature));
        }
        var addon = addonsById.get(owner);
        if (addon != null) {
            var addonState = resolveAddon(owner);
            return new ResolvedFeatureState(featureKey, owner, addonState.state(), addonState.reason(),
                feature.uiBehavior(), apiMode(feature));
        }
        return new ResolvedFeatureState(featureKey, owner, PlatformModuleState.ENABLED, null,
            feature.uiBehavior(), apiMode(feature));
    }

    public ProductModulesCatalog.ApiGateEntry apiGateFor(String path, String method) {
        var normalized = normalizePath(path);
        for (var addon : addonsById.values()) {
            var gate = findMatchingApiGate(addon, normalized, method);
            if (gate != null) {
                return gate;
            }
        }
        return null;
    }

    private static ProductModulesCatalog.ApiGateEntry findMatchingApiGate(
        ProductModulesCatalog.AddonEntry addon,
        String normalized,
        String method
    ) {
        var gates = addon.gates();
        if (gates == null) {
            return null;
        }
        for (var gate : safeList(gates.api())) {
            if (pathMatches(normalized, gate.path()) && methodAllowed(gate, method)) {
                return gate;
            }
        }
        return null;
    }

    private static boolean methodAllowed(ProductModulesCatalog.ApiGateEntry gate, String method) {
        var methods = safeList(gate.methods());
        return methods.isEmpty() || methods.stream().anyMatch(m -> m.equalsIgnoreCase(method));
    }

    public ProductModulesCatalog.JobGateEntry jobGate(String job) {
        for (var addon : addonsById.values()) {
            if (addon.gates() == null) {
                continue;
            }
            for (var gate : safeList(addon.gates().jobs())) {
                if (job.equals(gate.job())) {
                    return gate;
                }
            }
        }
        return null;
    }

    public ProductModulesCatalog.HookGateEntry hookGate(String hook) {
        for (var addon : addonsById.values()) {
            if (addon.gates() == null) {
                continue;
            }
            for (var gate : safeList(addon.gates().hooks())) {
                if (hook.equals(gate.hook())) {
                    return gate;
                }
            }
        }
        return null;
    }

    ResolvedAddonState resolveEffectiveState(
        ProductModulesCatalog.AddonEntry addon,
        boolean selected,
        PlatformModuleOverrideRow override
    ) {
        if (!appConfig.coreAvailable()) {
            return new ResolvedAddonState(selected, false, false, false, true,
                PlatformModuleState.DEGRADED, PlatformModuleReason.CORE_UNAVAILABLE);
        }
        if (!selected) {
            return new ResolvedAddonState(false, false, false, false, true,
                PlatformModuleState.DISABLED, PlatformModuleReason.NOT_SELECTED);
        }
        return resolveSelectedAddon(addon, override);
    }

    private ResolvedAddonState resolveSelectedAddon(
        ProductModulesCatalog.AddonEntry addon,
        PlatformModuleOverrideRow override
    ) {
        var installed = csvContains(appConfig.korusProductInstalledAddons(), addon.id(), true);
        var schemaInstalled = installed && csvContains(appConfig.korusProductSchemaInstalledAddons(), addon.id(), true);
        var runtimeReady = installed && schemaInstalled
            && csvContains(appConfig.korusProductRuntimeReadyAddons(), addon.id(), true);
        var adminEnabled = override == null || !override.disabled() || override.forceEnabled();
        if (csvContains(appConfig.korusProductInstallingAddons(), addon.id(), false) || !installed) {
            return new ResolvedAddonState(true, installed, schemaInstalled, false, adminEnabled,
                PlatformModuleState.INSTALLING, installed ? PlatformModuleReason.MIGRATION_RUNNING
                    : PlatformModuleReason.INSTALL_REQUESTED);
        }
        if (!schemaInstalled) {
            return new ResolvedAddonState(true, true, false, false, adminEnabled,
                PlatformModuleState.DEGRADED, PlatformModuleReason.SCHEMA_MISSING);
        }
        if (isEol(addon, override)) {
            return new ResolvedAddonState(true, true, true, runtimeReady, adminEnabled,
                PlatformModuleState.DISABLED, PlatformModuleReason.EOL);
        }
        if (secretsMissing(addon)) {
            return new ResolvedAddonState(true, true, true, false, adminEnabled,
                PlatformModuleState.DEGRADED, PlatformModuleReason.SECRETS_MISSING);
        }
        if (override != null && override.disabled() && !override.forceEnabled()) {
            var reason = override.reasonEnum().orElse(PlatformModuleReason.ADMIN_OVERRIDE);
            return new ResolvedAddonState(true, true, true, runtimeReady,
                false, PlatformModuleState.DISABLED, reason);
        }
        if (healthStale(addon)) {
            return new ResolvedAddonState(true, true, true, false, adminEnabled,
                PlatformModuleState.DEGRADED, PlatformModuleReason.HEALTH_STALE);
        }
        if (!runtimeReady) {
            return new ResolvedAddonState(true, true, true, false, adminEnabled,
                PlatformModuleState.DEGRADED, PlatformModuleReason.BACKEND_UNAVAILABLE);
        }
        return new ResolvedAddonState(true, true, true, true, adminEnabled,
            PlatformModuleState.ENABLED, null);
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
            if ("vapid".equals(secret.name()) && vapidSecretsMissing()) {
                return true;
            }
            if ("livekit".equals(secret.name()) && livekitSecretsMissing()) {
                return true;
            }
        }
        return false;
    }

    private boolean vapidSecretsMissing() {
        return (isBlank(System.getenv("PUSH_VAPID_PUBLIC_KEY")) && appConfig.webClientVapidPublicKey().isEmpty())
            || isBlank(System.getenv("PUSH_VAPID_PRIVATE_KEY"));
    }

    private boolean livekitSecretsMissing() {
        return (isBlank(System.getenv("LIVEKIT_API_KEY")) && isBlank(appConfig.livekitApiKey()))
            || (isBlank(System.getenv("LIVEKIT_API_SECRET")) && isBlank(appConfig.livekitApiSecret()))
            || (isBlank(System.getenv("LIVEKIT_URL")) && isBlank(appConfig.livekitUrl()));
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

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private PlatformCapabilitiesResponse.FeatureSection featureSection(
        ProductModulesCatalog.FeatureEntry feature,
        ResolvedAddonState state
    ) {
        return new PlatformCapabilitiesResponse.FeatureSection(
            feature.owner(),
            state.state().code(),
            state.reason() != null ? state.reason().code() : null,
            valueOr(feature.uiBehavior(), firstUiBehavior(addonsById.get(feature.owner()))),
            apiMode(feature)
        );
    }

    private static String firstUiBehavior(ProductModulesCatalog.AddonEntry addon) {
        if (addon == null) {
            return "show";
        }
        if (addon.gates() != null && !safeList(addon.gates().ui()).isEmpty()) {
            return valueOr(addon.gates().ui().getFirst().behavior(), addon.degradationMode());
        }
        return valueOr(addon.degradationMode(), "hide");
    }

    private static String apiMode(ProductModulesCatalog.FeatureEntry feature) {
        if (feature.apiBehavior() == null) {
            return API_ALLOW;
        }
        return valueOr(feature.apiBehavior().mode(), "reject");
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean csvContains(String csv, String id, boolean emptyMeansSelected) {
        if (csv == null || csv.isBlank()) {
            return emptyMeansSelected;
        }
        return java.util.Arrays.stream(csv.split(","))
            .map(String::trim)
            .anyMatch(id::equals);
    }

    private static boolean pathMatches(String path, String gatePath) {
        var normalizedGate = normalizePath(gatePath);
        if (normalizedGate.endsWith("/**")) {
            // Require path segment boundary: "v1/admin/export/**" must not match
            // "v1/admin/export-compliance-prep".
            var prefix = normalizedGate.substring(0, normalizedGate.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return path.equals(normalizedGate) || path.startsWith(normalizedGate + "/");
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        var p = path.startsWith("/") ? path.substring(1) : path;
        if (p.startsWith("api/")) {
            p = p.substring(4);
        }
        return p;
    }

    public record ResolvedAddonState(
        boolean selected,
        boolean installed,
        boolean schemaInstalled,
        boolean runtimeReady,
        boolean adminEnabled,
        PlatformModuleState state,
        PlatformModuleReason reason
    ) {
        public ResolvedAddonState(PlatformModuleState state, PlatformModuleReason reason) {
            this(state == PlatformModuleState.ENABLED, state == PlatformModuleState.ENABLED,
                state == PlatformModuleState.ENABLED, state == PlatformModuleState.ENABLED,
                true, state, reason);
        }
    }

    public record ResolvedFeatureState(
        String key,
        String owner,
        PlatformModuleState state,
        PlatformModuleReason reason,
        String uiBehavior,
        String apiBehavior
    ) {}
}
