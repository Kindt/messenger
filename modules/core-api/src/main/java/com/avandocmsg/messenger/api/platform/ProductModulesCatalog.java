package com.avandocmsg.messenger.api.platform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductModulesCatalog(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("base") BaseEntry base,
    @JsonProperty("substrates") List<SubstrateEntry> substrates,
    @JsonProperty("addons") List<AddonEntry> addons,
    @JsonProperty("legacy_deploy_profile_map") Map<String, LegacyProfileEntry> legacyDeployProfileMap
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaseEntry(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("state") String state,
        @JsonProperty("features") List<FeatureEntry> features,
        @JsonProperty("core_infra") List<String> coreInfra,
        @JsonProperty("external_stack_components") List<String> externalStackComponents,
        @JsonProperty("external_stack_profiles") List<String> externalStackProfiles
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubstrateEntry(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("features") List<FeatureEntry> features,
        @JsonProperty("migration_bundle") MigrationBundleEntry migrationBundle,
        @JsonProperty("db_objects") List<String> dbObjects
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddonEntry(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("features") List<FeatureEntry> features,
        @JsonProperty("internal_infra") List<String> internalInfra,
        @JsonProperty("degradation_mode") String degradationMode,
        @JsonProperty("disabled_behavior") String disabledBehavior,
        @JsonProperty("degraded_behavior") String degradedBehavior,
        @JsonProperty("installing_behavior") String installingBehavior,
        @JsonProperty("network_profile") String networkProfile,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("successor_addon_id") String successorAddonId,
        @JsonProperty("secrets") List<SecretEntry> secrets,
        @JsonProperty("env_flags") Map<String, String> envFlags,
        @JsonProperty("migration_bundle") MigrationBundleEntry migrationBundle,
        @JsonProperty("runtime") RuntimeEntry runtime,
        @JsonProperty("gates") GatesEntry gates,
        @JsonProperty("acceptance") AcceptanceEntry acceptance,
        @JsonProperty("external_stack_components") List<String> externalStackComponents,
        @JsonProperty("external_stack_profiles") List<String> externalStackProfiles
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeatureEntry(
        @JsonProperty("key") String key,
        @JsonProperty("label") String label,
        @JsonProperty("owner") String owner,
        @JsonProperty("ui_behavior") String uiBehavior,
        @JsonProperty("api_behavior") ApiBehaviorEntry apiBehavior,
        @JsonProperty("dependencies") List<String> dependencies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiBehaviorEntry(
        @JsonProperty("mode") String mode,
        @JsonProperty("http_code") Integer httpCode,
        @JsonProperty("message_key") String messageKey
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretEntry(
        @JsonProperty("name") String name,
        @JsonProperty("env_public") String envPublic,
        @JsonProperty("env_private") String envPrivate,
        @JsonProperty("env_key") String envKey,
        @JsonProperty("env_secret") String envSecret,
        @JsonProperty("env_url") String envUrl,
        @JsonProperty("secret_ref") String secretRef
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MigrationBundleEntry(
        @JsonProperty("id") String id,
        @JsonProperty("owner") String owner,
        @JsonProperty("location") String location,
        @JsonProperty("history_table") String historyTable,
        @JsonProperty("schema_objects") List<String> schemaObjects
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuntimeEntry(
        @JsonProperty("services") List<String> services,
        @JsonProperty("workers") List<String> workers,
        @JsonProperty("required_secrets") List<String> requiredSecrets,
        @JsonProperty("health_checks") List<String> healthChecks,
        @JsonProperty("job_policy") String jobPolicy,
        @JsonProperty("hook_policy") String hookPolicy
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GatesEntry(
        @JsonProperty("api") List<ApiGateEntry> api,
        @JsonProperty("ui") List<UiGateEntry> ui,
        @JsonProperty("jobs") List<JobGateEntry> jobs,
        @JsonProperty("hooks") List<HookGateEntry> hooks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiGateEntry(
        @JsonProperty("path") String path,
        @JsonProperty("methods") List<String> methods,
        @JsonProperty("feature") String feature,
        @JsonProperty("disabled_behavior") String disabledBehavior,
        @JsonProperty("degraded_behavior") String degradedBehavior,
        @JsonProperty("installing_behavior") String installingBehavior,
        @JsonProperty("http_code") Integer httpCode,
        @JsonProperty("message_key") String messageKey
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UiGateEntry(
        @JsonProperty("feature") String feature,
        @JsonProperty("control") String control,
        @JsonProperty("behavior") String behavior
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobGateEntry(
        @JsonProperty("job") String job,
        @JsonProperty("feature") String feature,
        @JsonProperty("disabled_behavior") String disabledBehavior,
        @JsonProperty("degraded_behavior") String degradedBehavior
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HookGateEntry(
        @JsonProperty("hook") String hook,
        @JsonProperty("feature") String feature,
        @JsonProperty("disabled_behavior") String disabledBehavior,
        @JsonProperty("degraded_behavior") String degradedBehavior
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AcceptanceEntry(
        @JsonProperty("positive") List<String> positive,
        @JsonProperty("disabled") List<String> disabled,
        @JsonProperty("degraded") List<String> degraded
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LegacyProfileEntry(
        @JsonProperty("addons") List<String> addons
    ) {}
}
