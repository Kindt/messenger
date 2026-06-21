package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackCutoverReadinessReport(
    @JsonProperty("ready") boolean ready,
    @JsonProperty("severity") String severity,
    @JsonProperty("environment") String environment,
    @JsonProperty("smoke_command") String smokeCommand,
    @JsonProperty("blocker_count") int blockerCount,
    @JsonProperty("warning_count") int warningCount,
    @JsonProperty("blockers") List<String> blockers,
    @JsonProperty("warnings") List<String> warnings,
    @JsonProperty("remediation_actions") List<String> remediationActions
) {
    public ExternalStackCutoverReadinessReport {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        remediationActions = remediationActions == null ? List.of() : List.copyOf(remediationActions);
        blockerCount = blockers.size();
        warningCount = warnings.size();
        ready = blockers.isEmpty();
    }
}
