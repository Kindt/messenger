package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackProfilePreflightReport(
    @JsonProperty("passed") boolean passed,
    @JsonProperty("severity") String severity,
    @JsonProperty("profile_id") String profileId,
    @JsonProperty("component") String component,
    @JsonProperty("lifecycle_status") String lifecycleStatus,
    @JsonProperty("failures") List<String> failures,
    @JsonProperty("missing_promotion_evidence") List<String> missingPromotionEvidence,
    @JsonProperty("unsupported_modes") List<String> unsupportedModes
) {
    public ExternalStackProfilePreflightReport {
        failures = failures == null ? List.of() : List.copyOf(failures);
        missingPromotionEvidence = missingPromotionEvidence == null ? List.of() : List.copyOf(missingPromotionEvidence);
        unsupportedModes = unsupportedModes == null ? List.of() : List.copyOf(unsupportedModes);
        passed = failures.isEmpty();
    }
}
