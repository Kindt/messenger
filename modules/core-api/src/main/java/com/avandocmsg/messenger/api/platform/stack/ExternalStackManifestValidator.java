package com.avandocmsg.messenger.api.platform.stack;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ExternalStackManifestValidator {

    private static final String COMPONENT_PREFIX = "component ";
    private static final String PROFILE_PREFIX = "profile ";
    private static final String PROFILE_SEPARATOR = " profile ";
    private static final String USERINFO_PATTERN = "//[^/@]+@";
    private static final String USERINFO_REDACTED = "//<redacted>@";
    private static final Pattern USERINFO_IN_ENDPOINT = Pattern.compile(USERINFO_PATTERN);

    private ExternalStackManifestValidator() {
    }

    public static ValidationResult validateDesiredManifests(List<ComponentBackendManifest> manifests) {
        var failures = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        var metadata = new LinkedHashMap<String, String>();
        boolean redacted = false;

        var byComponent = manifests.stream()
            .collect(Collectors.groupingBy(ComponentBackendManifest::component, LinkedHashMap::new, Collectors.toList()));
        for (var entry : byComponent.entrySet()) {
            redacted = validateComponentGroup(entry.getKey(), entry.getValue(), failures, warnings, metadata)
                || redacted;
        }

        return new ValidationResult(failures.isEmpty(), failures, warnings, redacted, metadata);
    }

    private static boolean validateComponentGroup(
        String component,
        List<ComponentBackendManifest> componentManifests,
        List<String> failures,
        List<String> warnings,
        Map<String, String> metadata
    ) {
        boolean redacted = false;
        long activeCount = componentManifests.stream()
            .filter(m -> m.role() == ExternalStackRole.ACTIVE)
            .count();
        if (activeCount == 0) {
            failures.add(COMPONENT_PREFIX + component + " has no active manifest");
        } else if (activeCount > 1) {
            failures.add(COMPONENT_PREFIX + component + " has " + activeCount + " active manifests");
        }

        for (var manifest : componentManifests) {
            redacted = validateSingleManifest(component, manifest, failures, warnings, metadata) || redacted;
        }
        return redacted;
    }

    private static boolean validateSingleManifest(
        String component,
        ComponentBackendManifest manifest,
        List<String> failures,
        List<String> warnings,
        Map<String, String> metadata
    ) {
        if (manifest.role() != ExternalStackRole.ACTIVE
            && "true".equalsIgnoreCase(manifest.metadata().get("serve_traffic"))) {
            failures.add(COMPONENT_PREFIX + component + " role " + manifest.role().code() + " cannot serve active traffic");
        }
        if (ambiguousAuto(manifest)) {
            failures.add(COMPONENT_PREFIX + component + " uses ambiguous production auto profile");
        }
        validateCompatibilityProfile(manifest, failures, warnings);
        validateRequiredCheckEvidence(manifest, warnings);
        var redactedEndpoint = redactEndpoint(manifest.endpoint());
        boolean redacted = !Objects.equals(redactedEndpoint, manifest.endpoint());
        if (redactedEndpoint != null) {
            var metadataKey = component + ".endpoint";
            if (redacted) {
                metadata.put(metadataKey, redactedEndpoint);
            } else {
                metadata.putIfAbsent(metadataKey, redactedEndpoint);
            }
        }
        return redacted;
    }

    public static ExternalStackManifestPreflightReport report(List<ComponentBackendManifest> manifests) {
        var safeManifests = manifests == null ? List.<ComponentBackendManifest>of() : manifests;
        var validation = validateDesiredManifests(safeManifests);
        var components = new LinkedHashMap<String, ExternalStackManifestPreflightReport.ComponentSummary>();
        var byComponent = safeManifests.stream()
            .collect(Collectors.groupingBy(ComponentBackendManifest::component, LinkedHashMap::new, Collectors.toList()));
        for (var entry : byComponent.entrySet()) {
            var component = entry.getKey();
            var componentManifests = entry.getValue();
            var componentPrefix = COMPONENT_PREFIX + component + " ";
            var activeCount = (int) componentManifests.stream()
                .filter(m -> m.role() == ExternalStackRole.ACTIVE)
                .count();
            var failures = validation.failures().stream()
                .filter(failure -> failure.startsWith(componentPrefix))
                .toList();
            var warnings = validation.warnings().stream()
                .filter(warning -> warning.startsWith(componentPrefix))
                .toList();
            var missingRequiredChecks = missingRequiredChecks(component, componentManifests);
            components.put(component, new ExternalStackManifestPreflightReport.ComponentSummary(
                componentManifests.size(),
                activeCount,
                failures,
                warnings,
                missingRequiredChecks,
                remediationActions(component, failures, warnings, missingRequiredChecks),
                validation.metadata().get(component + ".endpoint")
            ));
        }
        var missingRequiredCheckCount = components.values().stream()
            .mapToInt(component -> component.missingRequiredChecks().size())
            .sum();
        var remediationActions = components.values().stream()
            .flatMap(component -> component.remediationActions().stream())
            .toList();
        return new ExternalStackManifestPreflightReport(
            validation.passed(),
            severity(validation, missingRequiredCheckCount),
            validation.failures().size(),
            validation.warnings().size(),
            missingRequiredCheckCount,
            remediationActions,
            validation,
            Map.copyOf(components)
        );
    }

    private static String severity(ValidationResult validation, int missingRequiredCheckCount) {
        if (!validation.passed()) {
            return "blocked";
        }
        if (!validation.warnings().isEmpty() || missingRequiredCheckCount > 0) {
            return "warning";
        }
        return "ok";
    }

    public static ValidationResult validateProfiles(List<ConnectorProfile> profiles) {
        var failures = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var profile : profiles) {
            validateOneProfile(profile, failures);
        }
        return new ValidationResult(failures.isEmpty(), failures, warnings, false, Map.of());
    }

    private static void validateOneProfile(ConnectorProfile profile, List<String> failures) {
        var id = PROFILE_PREFIX + profile.profileId();
        if ((profile.lifecycleStatus() == LifecycleStatus.CANDIDATE
            || profile.lifecycleStatus() == LifecycleStatus.INTEGRATION_CANDIDATE)
            && profile.deploymentModes().contains(DeploymentMode.BUNDLED)) {
            failures.add(id + " is candidate but declares bundled deployment");
        }
        if ((profile.lifecycleStatus() == LifecycleStatus.SUPPORTED_BUNDLED
            || profile.lifecycleStatus() == LifecycleStatus.SUPPORTED_EXTERNAL_BYO)
            && (profile.impactModel() == null || !profile.impactModel().complete())) {
            failures.add(id + " is supported but has no impact model");
        }
        if (profile.deploymentModes().contains(DeploymentMode.EXTERNAL_BYO)
            && profile.supportBoundary() == null) {
            failures.add(id + " external_byo has no support boundary");
        }
        if (profile.lifecycleStatus() == LifecycleStatus.REJECTED
            && profile.deploymentModes().contains(DeploymentMode.BUNDLED)) {
            failures.add(id + " is rejected but declares bundled deployment");
        }
    }

    private static boolean ambiguousAuto(ComponentBackendManifest manifest) {
        if (!"auto".equalsIgnoreCase(manifest.compatibilityProfile())) {
            return false;
        }
        long endpointHints = manifest.capabilities().stream()
            .filter(capability -> capability != null && capability.startsWith("endpoint:"))
            .count();
        return endpointHints > 1;
    }

    private static void validateCompatibilityProfile(
        ComponentBackendManifest manifest,
        List<String> failures,
        List<String> warnings
    ) {
        var profileId = manifest.compatibilityProfile();
        if (profileId == null || profileId.isBlank()
            || "explicit".equalsIgnoreCase(profileId)
            || "auto".equalsIgnoreCase(profileId)) {
            return;
        }
        ConnectorCompatibilityPack pack;
        try {
            pack = ConnectorCompatibilityPacks.packFor(profileId);
        } catch (IllegalArgumentException e) {
            failures.add(COMPONENT_PREFIX + manifest.component()
                + " references unknown compatibility profile " + profileId);
            return;
        }
        var profileLabel = COMPONENT_PREFIX + manifest.component() + PROFILE_SEPARATOR + profileId;
        if (!manifest.component().equals(pack.component())) {
            failures.add(profileLabel + " belongs to component " + pack.component());
            return;
        }
        if (manifest.role() == ExternalStackRole.ACTIVE && !pack.supported()) {
            failures.add(profileLabel + " is not production-supported");
        }
        if (manifest.role() == ExternalStackRole.ACTIVE
            && pack.lifecycleStatus() == LifecycleStatus.SUPPORTED_EXTERNAL_BYO) {
            warnings.add(profileLabel + " requires customer support boundary evidence");
        }
        if (manifest.role() == ExternalStackRole.ACTIVE) {
            pack.unsupportedModes().forEach(mode -> warnings.add(profileLabel + " unsupported mode: " + mode));
        }
    }

    private static void validateRequiredCheckEvidence(ComponentBackendManifest manifest, List<String> warnings) {
        if (manifest.role() != ExternalStackRole.ACTIVE) {
            return;
        }
        ComponentValidationContract contract;
        try {
            contract = ExternalStackComponentContracts.contractFor(manifest.component());
        } catch (IllegalArgumentException e) {
            return;
        }
        for (var check : contract.requiredChecks()) {
            if (!manifest.capabilities().contains(check)) {
                warnings.add(COMPONENT_PREFIX + manifest.component() + " missing required check evidence: " + check);
            }
        }
    }

    private static List<String> missingRequiredChecks(
        String component,
        List<ComponentBackendManifest> componentManifests
    ) {
        ComponentValidationContract contract;
        try {
            contract = ExternalStackComponentContracts.contractFor(component);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        var provided = componentManifests.stream()
            .filter(manifest -> manifest.role() == ExternalStackRole.ACTIVE)
            .flatMap(manifest -> manifest.capabilities().stream())
            .collect(Collectors.toSet());
        return contract.requiredChecks().stream()
            .filter(check -> !provided.contains(check))
            .toList();
    }

    private static List<String> remediationActions(
        String component,
        List<String> failures,
        List<String> warnings,
        List<String> missingRequiredChecks
    ) {
        var actions = new ArrayList<String>();
        for (var failure : failures) {
            if (failure.contains("has no active manifest") || failure.contains(" active manifests")) {
                actions.add(component + ": keep exactly one active manifest");
            } else if (failure.contains("cannot serve active traffic")) {
                actions.add("disable serve_traffic for non-active role");
            } else if (failure.contains("references unknown compatibility profile")) {
                actions.add(component + ": choose a profile from /compatibility-packs");
            } else if (failure.contains("belongs to component")) {
                actions.add(component + ": use a compatibility profile matching the component");
            } else if (failure.contains("is not production-supported")) {
                actions.add(component + ": switch active manifest to supported profile or keep candidate as migration_target");
            } else if (failure.contains("ambiguous production auto profile")) {
                actions.add(component + ": replace auto with an explicit compatibility_profile");
            }
        }
        for (var warning : warnings) {
            if (warning.contains("requires customer support boundary evidence")) {
                actions.add(component + ": attach customer support boundary evidence");
            } else if (warning.contains("unsupported mode:")) {
                actions.add(component + ": remove unsupported mode before production promotion");
            }
        }
        missingRequiredChecks.forEach(check -> actions.add("provide evidence for required check " + check));
        return actions.stream().distinct().toList();
    }

    private static String redactEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        if (USERINFO_IN_ENDPOINT.matcher(endpoint).find()) {
            return endpoint.replaceFirst(USERINFO_PATTERN, USERINFO_REDACTED);
        }
        try {
            var uri = new URI(endpoint);
            if (uri.getUserInfo() == null || uri.getUserInfo().isBlank()) {
                return endpoint;
            }
            return endpoint.replaceFirst(USERINFO_PATTERN, USERINFO_REDACTED);
        } catch (URISyntaxException e) {
            return endpoint.replaceFirst(USERINFO_PATTERN, USERINFO_REDACTED);
        }
    }
}
