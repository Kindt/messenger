package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackCatalogHealthReport(
    @JsonProperty("passed") boolean passed,
    @JsonProperty("component_count") int componentCount,
    @JsonProperty("profile_count") int profileCount,
    @JsonProperty("candidate_profile_count") int candidateProfileCount,
    @JsonProperty("failures") List<String> failures,
    @JsonProperty("warnings") List<String> warnings
) {
    public ExternalStackCatalogHealthReport {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        passed = failures.isEmpty();
    }
}
