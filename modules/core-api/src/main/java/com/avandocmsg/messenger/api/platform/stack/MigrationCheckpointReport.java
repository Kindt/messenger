package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MigrationCheckpointReport(
    @JsonProperty("component") String component,
    @JsonProperty("passed") boolean passed,
    @JsonProperty("severity") String severity,
    @JsonProperty("missing_markers") List<String> missingMarkers,
    @JsonProperty("failures") List<String> failures,
    @JsonProperty("rollback_ready") boolean rollbackReady,
    @JsonProperty("no_silent_fallback") boolean noSilentFallback
) {
    public MigrationCheckpointReport {
        missingMarkers = missingMarkers == null ? List.of() : List.copyOf(missingMarkers);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
