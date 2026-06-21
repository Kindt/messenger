package com.avandocmsg.messenger.api.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminProductModulesResponse(
    @JsonProperty("base") BaseRow base,
    @JsonProperty("addons") List<AddonRow> addons
) {
    public record BaseRow(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("state") String state
    ) {}

    public record AddonRow(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("selected") boolean selected,
        @JsonProperty("installed") boolean installed,
        @JsonProperty("schema_installed") boolean schemaInstalled,
        @JsonProperty("runtime_ready") boolean runtimeReady,
        @JsonProperty("admin_enabled") boolean adminEnabled,
        @JsonProperty("state") String state,
        @JsonProperty("reason") String reason,
        @JsonProperty("admin_disabled") boolean adminDisabled,
        @JsonProperty("force_enabled") boolean forceEnabled,
        @JsonProperty("internal_infra") List<String> internalInfra,
        @JsonProperty("services") List<String> services,
        @JsonProperty("workers") List<String> workers,
        @JsonProperty("required_secrets") List<String> requiredSecrets,
        @JsonProperty("schema_bundle") String schemaBundle,
        @JsonProperty("schema_history_table") String schemaHistoryTable,
        @JsonProperty("api_gate_count") int apiGateCount,
        @JsonProperty("ui_gate_count") int uiGateCount,
        @JsonProperty("job_gate_count") int jobGateCount,
        @JsonProperty("hook_gate_count") int hookGateCount,
        @JsonProperty("acceptance_positive") List<String> acceptancePositive,
        @JsonProperty("acceptance_disabled") List<String> acceptanceDisabled,
        @JsonProperty("acceptance_degraded") List<String> acceptanceDegraded
    ) {}
}
