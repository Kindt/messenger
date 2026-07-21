package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackProfilePreflightReport(
    @JsonProperty("passed") boolean passed,
    @JsonProperty("severity") String severity,
    @JsonProperty("profile_id") String profileId,
    @JsonProperty("component") String component,
    @JsonProperty("lifecycle_status") String lifecycleStatus,
    @JsonProperty("missing_promotion_evidence_count") int missingPromotionEvidenceCount,
    @JsonProperty("unsupported_mode_count") int unsupportedModeCount,
    @JsonProperty("failures") List<String> failures,
    @JsonProperty("missing_promotion_evidence") List<String> missingPromotionEvidence,
    @JsonProperty("unsupported_modes") List<String> unsupportedModes,
    @JsonProperty("remediation_actions") List<String> remediationActions
) {
    public ExternalStackProfilePreflightReport {
        failures = failures == null ? List.of() : List.copyOf(failures);
        missingPromotionEvidence = missingPromotionEvidence == null ? List.of() : List.copyOf(missingPromotionEvidence);
        unsupportedModes = unsupportedModes == null ? List.of() : List.copyOf(unsupportedModes);
        remediationActions = remediationActions == null ? List.of() : List.copyOf(remediationActions);
        missingPromotionEvidenceCount = missingPromotionEvidence.size();
        unsupportedModeCount = unsupportedModes.size();
        // Green only when support failures, promotion gaps, and unsupported modes are all clear
        passed = failures.isEmpty()
            && missingPromotionEvidence.isEmpty()
            && unsupportedModes.isEmpty();
    }
}
