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
        @JsonProperty("installed") boolean installed,
        @JsonProperty("state") String state,
        @JsonProperty("reason") String reason,
        @JsonProperty("admin_disabled") boolean adminDisabled,
        @JsonProperty("force_enabled") boolean forceEnabled,
        @JsonProperty("internal_infra") List<String> internalInfra
    ) {}
}
