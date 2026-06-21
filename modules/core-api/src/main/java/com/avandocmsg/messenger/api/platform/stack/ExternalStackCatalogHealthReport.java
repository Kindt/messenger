package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackCatalogHealthReport(
    @JsonProperty("passed") boolean passed,
    @JsonProperty("component_count") int componentCount,
    @JsonProperty("profile_count") int profileCount,
    @JsonProperty("candidate_profile_count") int candidateProfileCount,
    @JsonProperty("failure_count") int failureCount,
    @JsonProperty("warning_count") int warningCount,
    @JsonProperty("failures") List<String> failures,
    @JsonProperty("warnings") List<String> warnings,
    @JsonProperty("remediation_actions") List<String> remediationActions
) {
    public ExternalStackCatalogHealthReport {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        remediationActions = remediationActions == null ? List.of() : List.copyOf(remediationActions);
        failureCount = failures.size();
        warningCount = warnings.size();
        passed = failures.isEmpty();
    }
}
