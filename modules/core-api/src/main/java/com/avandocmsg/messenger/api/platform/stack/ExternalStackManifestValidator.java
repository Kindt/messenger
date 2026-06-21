package com.avandocmsg.messenger.api.platform.stack;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ExternalStackManifestValidator {

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
            var component = entry.getKey();
            var componentManifests = entry.getValue();
            long activeCount = componentManifests.stream()
                .filter(m -> m.role() == ExternalStackRole.active)
                .count();
            if (activeCount == 0) {
                failures.add("component " + component + " has no active manifest");
            } else if (activeCount > 1) {
                failures.add("component " + component + " has " + activeCount + " active manifests");
            }

            for (var manifest : componentManifests) {
                if (manifest.role() != ExternalStackRole.active
                    && "true".equalsIgnoreCase(manifest.metadata().get("serve_traffic"))) {
                    failures.add("component " + component + " role " + manifest.role() + " cannot serve active traffic");
                }
                if (ambiguousAuto(manifest)) {
                    failures.add("component " + component + " uses ambiguous production auto profile");
                }
                validateCompatibilityProfile(manifest, failures, warnings);
                validateRequiredCheckEvidence(manifest, warnings);
                var redactedEndpoint = redactEndpoint(manifest.endpoint());
                if (!Objects.equals(redactedEndpoint, manifest.endpoint())) {
                    redacted = true;
                }
                if (redactedEndpoint != null) {
                    var metadataKey = component + ".endpoint";
                    if (!Objects.equals(redactedEndpoint, manifest.endpoint()) || !metadata.containsKey(metadataKey)) {
                        metadata.put(metadataKey, redactedEndpoint);
                    }
                }
            }
        }

        return new ValidationResult(false, failures, warnings, redacted, metadata);
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
            var componentPrefix = "component " + component + " ";
            var activeCount = (int) componentManifests.stream()
                .filter(m -> m.role() == ExternalStackRole.active)
                .count();
            components.put(component, new ExternalStackManifestPreflightReport.ComponentSummary(
                componentManifests.size(),
                activeCount,
                validation.failures().stream()
                    .filter(failure -> failure.startsWith(componentPrefix))
                    .toList(),
                validation.warnings().stream()
                    .filter(warning -> warning.startsWith(componentPrefix))
                    .toList(),
                missingRequiredChecks(component, componentManifests),
                validation.metadata().get(component + ".endpoint")
            ));
        }
        var missingRequiredCheckCount = components.values().stream()
            .mapToInt(component -> component.missingRequiredChecks().size())
            .sum();
        return new ExternalStackManifestPreflightReport(
            validation.passed(),
            severity(validation, missingRequiredCheckCount),
            validation.failures().size(),
            validation.warnings().size(),
            missingRequiredCheckCount,
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
            if ((profile.lifecycleStatus() == LifecycleStatus.candidate
                || profile.lifecycleStatus() == LifecycleStatus.integration_candidate)
                && profile.deploymentModes().contains(DeploymentMode.bundled)) {
                failures.add("profile " + profile.profileId() + " is candidate but declares bundled deployment");
            }
            if ((profile.lifecycleStatus() == LifecycleStatus.supported_bundled
                || profile.lifecycleStatus() == LifecycleStatus.supported_external_byo)
                && (profile.impactModel() == null || !profile.impactModel().complete())) {
                failures.add("profile " + profile.profileId() + " is supported but has no impact model");
            }
            if (profile.deploymentModes().contains(DeploymentMode.external_byo)
                && profile.supportBoundary() == null) {
                failures.add("profile " + profile.profileId() + " external_byo has no support boundary");
            }
            if (profile.lifecycleStatus() == LifecycleStatus.rejected
                && profile.deploymentModes().contains(DeploymentMode.bundled)) {
                failures.add("profile " + profile.profileId() + " is rejected but declares bundled deployment");
            }
        }
        return new ValidationResult(false, failures, warnings, false, Map.of());
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
            failures.add("component " + manifest.component()
                + " references unknown compatibility profile " + profileId);
            return;
        }
        if (!manifest.component().equals(pack.component())) {
            failures.add("component " + manifest.component()
                + " profile " + profileId + " belongs to component " + pack.component());
            return;
        }
        if (manifest.role() == ExternalStackRole.active && !pack.supported()) {
            failures.add("component " + manifest.component()
                + " profile " + profileId + " is not production-supported");
        }
        if (manifest.role() == ExternalStackRole.active
            && pack.lifecycleStatus() == LifecycleStatus.supported_external_byo) {
            warnings.add("component " + manifest.component()
                + " profile " + profileId + " requires customer support boundary evidence");
        }
        if (manifest.role() == ExternalStackRole.active) {
            pack.unsupportedModes().forEach(mode -> warnings.add("component " + manifest.component()
                + " profile " + profileId + " unsupported mode: " + mode));
        }
    }

    private static void validateRequiredCheckEvidence(ComponentBackendManifest manifest, List<String> warnings) {
        if (manifest.role() != ExternalStackRole.active) {
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
                warnings.add("component " + manifest.component() + " missing required check evidence: " + check);
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
            .filter(manifest -> manifest.role() == ExternalStackRole.active)
            .flatMap(manifest -> manifest.capabilities().stream())
            .collect(Collectors.toSet());
        return contract.requiredChecks().stream()
            .filter(check -> !provided.contains(check))
            .toList();
    }

    private static String redactEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        if (endpoint.matches(".*//[^/@]+@.*")) {
            return endpoint.replaceFirst("//[^/@]+@", "//<redacted>@");
        }
        try {
            var uri = new URI(endpoint);
            if (uri.getUserInfo() == null || uri.getUserInfo().isBlank()) {
                return endpoint;
            }
            return endpoint.replaceFirst("//[^/@]+@", "//<redacted>@");
        } catch (URISyntaxException e) {
            return endpoint.replaceFirst("//[^/@]+@", "//<redacted>@");
        }
    }
}
