package com.avandocmsg.messenger.api.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlatformCapabilitiesResponse(
    @JsonProperty("product") ProductSection product,
    @JsonProperty("modules") Map<String, ModuleSection> modules,
    @JsonProperty("infra") Map<String, InfraSection> infra,
    @JsonProperty("base_media") BaseMediaSection baseMedia,
    @JsonProperty("external_stack") Map<String, ExternalStackSection> externalStack
) {
    public record ProductSection(
        @JsonProperty("base") BaseProductSection base,
        @JsonProperty("addons_enabled") List<String> addonsEnabled
    ) {}

    public record BaseProductSection(
        @JsonProperty("state") String state,
        @JsonProperty("label") String label,
        @JsonProperty("external_stack_components") List<String> externalStackComponents,
        @JsonProperty("external_stack_profiles") List<String> externalStackProfiles
    ) {}

    public record ModuleSection(
        @JsonProperty("state") String state,
        @JsonProperty("reason") String reason,
        @JsonProperty("label") String label,
        @JsonProperty("degradation_mode") String degradationMode,
        @JsonProperty("network_profile") String networkProfile,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("successor_addon_id") String successorAddonId,
        @JsonProperty("mode") String mode,
        @JsonProperty("external_stack_components") List<String> externalStackComponents,
        @JsonProperty("external_stack_profiles") List<String> externalStackProfiles,
        @JsonProperty("external_stack_warnings") List<String> externalStackWarnings
    ) {}

    public record InfraSection(
        @JsonProperty("state") String state
    ) {}

    public record BaseMediaSection(
        @JsonProperty("mesh_webrtc") boolean meshWebrtc,
        @JsonProperty("jitsi_conference") boolean jitsiConference
    ) {}

    public record ExternalStackSection(
        @JsonProperty("desired_connector") String desiredConnector,
        @JsonProperty("health_status") String healthStatus,
        @JsonProperty("validation_status") String validationStatus,
        @JsonProperty("support_boundary") String supportBoundary
    ) {}
}
