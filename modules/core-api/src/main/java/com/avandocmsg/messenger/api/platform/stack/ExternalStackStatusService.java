package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExternalStackStatusService {

    public ExternalStackStatusResponse status(List<ManifestObservation> observations) {
        var components = new LinkedHashMap<String, ComponentStatus>();
        for (var observation : observations) {
            var desired = observation.desiredManifest();
            var observed = observation.observedManifest();
            var component = desired.component();
            var validation = observation.validationResult();
            var observedEndpoint = observed != null ? observed.endpoint() : null;
            if (validation != null && validation.metadata().containsKey(component + ".endpoint")) {
                observedEndpoint = validation.metadata().get(component + ".endpoint");
            }
            components.put(component, new ComponentStatus(
                component,
                desired.connector(),
                observed != null ? observed.connector() : null,
                desired.role().name(),
                observed != null ? observed.role().name() : null,
                observation.healthStatus(),
                observation.degradedReason(),
                supportScope(observed != null ? observed.supportBoundary() : desired.supportBoundary()),
                observedEndpoint,
                observed != null && !desired.connector().equals(observed.connector()),
                validation != null && validation.passed() ? "passed" : "failed",
                validation != null ? validation.failures() : List.of(),
                validation != null ? validation.warnings() : List.of()
            ));
        }
        return new ExternalStackStatusResponse(components);
    }

    public ExternalStackProfileStatusResponse profileStatus(List<ConnectorProfile> profiles) {
        var profileRows = new LinkedHashMap<String, ProfileStatus>();
        for (var profile : profiles) {
            var pack = compatibilityPack(profile.profileId());
            profileRows.put(profile.profileId(), new ProfileStatus(
                profile.profileId(),
                profile.lifecycleStatus().name(),
                profile.deploymentModes().stream().map(Enum::name).toList(),
                profile.lifecycleStatus() == LifecycleStatus.supported_bundled
                    || profile.lifecycleStatus() == LifecycleStatus.supported_external_byo,
                supportScope(profile.supportBoundary()),
                pack != null ? pack.requiredChecks() : List.of(),
                pack != null ? pack.promotionEvidence() : List.of(),
                pack != null ? pack.unsupportedModes() : List.of()
            ));
        }
        return new ExternalStackProfileStatusResponse(profileRows);
    }

    private static ConnectorCompatibilityPack compatibilityPack(String profileId) {
        try {
            return ConnectorCompatibilityPacks.packFor(profileId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String supportScope(SupportBoundary supportBoundary) {
        return supportBoundary != null ? supportBoundary.korusSupportScope() : null;
    }

    public record ExternalStackStatusResponse(
        @JsonProperty("components") Map<String, ComponentStatus> components
    ) {}

    public record ComponentStatus(
        @JsonProperty("component") String component,
        @JsonProperty("desired_connector") String desiredConnector,
        @JsonProperty("observed_connector") String observedConnector,
        @JsonProperty("desired_role") String desiredRole,
        @JsonProperty("observed_role") String observedRole,
        @JsonProperty("health_status") String healthStatus,
        @JsonProperty("degraded_reason") String degradedReason,
        @JsonProperty("support_boundary") String supportBoundary,
        @JsonProperty("observed_endpoint") String observedEndpoint,
        @JsonProperty("mismatch") boolean mismatch,
        @JsonProperty("validation_status") String validationStatus,
        @JsonProperty("validation_failures") List<String> validationFailures,
        @JsonProperty("validation_warnings") List<String> validationWarnings
    ) {}

    public record ExternalStackProfileStatusResponse(
        @JsonProperty("profiles") Map<String, ProfileStatus> profiles
    ) {}

    public record ProfileStatus(
        @JsonProperty("profile_id") String profileId,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("deployment_modes") List<String> deploymentModes,
        @JsonProperty("supported") boolean supported,
        @JsonProperty("support_boundary") String supportBoundary,
        @JsonProperty("required_checks") List<String> requiredChecks,
        @JsonProperty("promotion_evidence") List<String> promotionEvidence,
        @JsonProperty("unsupported_modes") List<String> unsupportedModes
    ) {}
}
