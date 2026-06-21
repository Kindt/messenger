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
                var redactedEndpoint = redactEndpoint(manifest.endpoint());
                if (!Objects.equals(redactedEndpoint, manifest.endpoint())) {
                    redacted = true;
                }
                if (redactedEndpoint != null) {
                    metadata.put(component + ".endpoint", redactedEndpoint);
                }
            }
        }

        return new ValidationResult(false, failures, warnings, redacted, metadata);
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

    private static String redactEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
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
