package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConnectorCompatibilityPack(
    @JsonProperty("profile_id") String profileId,
    @JsonProperty("component") String component,
    @JsonProperty("lifecycle_status") LifecycleStatus lifecycleStatus,
    @JsonProperty("required_checks") List<String> requiredChecks,
    @JsonProperty("promotion_evidence") List<String> promotionEvidence,
    @JsonProperty("unsupported_modes") List<String> unsupportedModes
) {
    public ConnectorCompatibilityPack {
        requiredChecks = requiredChecks == null ? List.of() : List.copyOf(requiredChecks);
        promotionEvidence = promotionEvidence == null ? List.of() : List.copyOf(promotionEvidence);
        unsupportedModes = unsupportedModes == null ? List.of() : List.copyOf(unsupportedModes);
    }

    public boolean supported() {
        return lifecycleStatus == LifecycleStatus.supported_bundled
            || lifecycleStatus == LifecycleStatus.supported_external_byo;
    }
}
