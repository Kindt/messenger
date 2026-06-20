package com.avandocmsg.messenger.api.platform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductModulesCatalog(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("base") BaseEntry base,
    @JsonProperty("addons") List<AddonEntry> addons,
    @JsonProperty("legacy_deploy_profile_map") Map<String, LegacyProfileEntry> legacyDeployProfileMap
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaseEntry(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("core_infra") List<String> coreInfra
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddonEntry(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("internal_infra") List<String> internalInfra,
        @JsonProperty("degradation_mode") String degradationMode,
        @JsonProperty("network_profile") String networkProfile,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("successor_addon_id") String successorAddonId,
        @JsonProperty("secrets") List<SecretEntry> secrets,
        @JsonProperty("env_flags") Map<String, String> envFlags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretEntry(
        @JsonProperty("name") String name,
        @JsonProperty("env_public") String envPublic,
        @JsonProperty("env_private") String envPrivate,
        @JsonProperty("env_key") String envKey,
        @JsonProperty("env_secret") String envSecret,
        @JsonProperty("env_url") String envUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LegacyProfileEntry(
        @JsonProperty("addons") List<String> addons
    ) {}
}
