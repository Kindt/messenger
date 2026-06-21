package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ExternalStackManifestPreflightReport(
    @JsonProperty("passed") boolean passed,
    @JsonProperty("severity") String severity,
    @JsonProperty("failure_count") int failureCount,
    @JsonProperty("warning_count") int warningCount,
    @JsonProperty("missing_required_check_count") int missingRequiredCheckCount,
    @JsonProperty("remediation_actions") List<String> remediationActions,
    @JsonProperty("validation") ValidationResult validation,
    @JsonProperty("components") Map<String, ComponentSummary> components
) {
    public record ComponentSummary(
        @JsonProperty("manifest_count") int manifestCount,
        @JsonProperty("active_count") int activeCount,
        @JsonProperty("failures") List<String> failures,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("missing_required_checks") List<String> missingRequiredChecks,
        @JsonProperty("remediation_actions") List<String> remediationActions,
        @JsonProperty("redacted_endpoint") String redactedEndpoint
    ) {
        public ComponentSummary {
            failures = failures == null ? List.of() : List.copyOf(failures);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            missingRequiredChecks = missingRequiredChecks == null ? List.of() : List.copyOf(missingRequiredChecks);
            remediationActions = remediationActions == null ? List.of() : List.copyOf(remediationActions);
        }
    }

    public ExternalStackManifestPreflightReport {
        remediationActions = remediationActions == null ? List.of() : List.copyOf(remediationActions);
    }
}
