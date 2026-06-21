package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackComponentProfileSummary(
    @JsonProperty("component") String component,
    @JsonProperty("profile_count") int profileCount,
    @JsonProperty("supported_count") int supportedCount,
    @JsonProperty("candidate_count") int candidateCount,
    @JsonProperty("rejected_count") int rejectedCount,
    @JsonProperty("readiness_warning") String readinessWarning,
    @JsonProperty("readiness_severity") String readinessSeverity,
    @JsonProperty("remediation_actions") List<String> remediationActions
) {
    public ExternalStackComponentProfileSummary {
        remediationActions = remediationActions == null ? List.of() : List.copyOf(remediationActions);
    }
}
