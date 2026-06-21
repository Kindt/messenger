package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ValidationResult(
    @JsonProperty("passed") boolean passed,
    @JsonProperty("failures") List<String> failures,
    @JsonProperty("warnings") List<String> warnings,
    @JsonProperty("redacted") boolean redacted,
    @JsonProperty("metadata") Map<String, String> metadata
) {
    public ValidationResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        passed = failures.isEmpty();
    }
}
