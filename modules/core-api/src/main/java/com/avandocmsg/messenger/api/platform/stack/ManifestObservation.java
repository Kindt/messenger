package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ManifestObservation(
    @JsonProperty("desired_manifest") ComponentBackendManifest desiredManifest,
    @JsonProperty("observed_manifest") ComponentBackendManifest observedManifest,
    @JsonProperty("health_status") String healthStatus,
    @JsonProperty("degraded_reason") String degradedReason,
    @JsonProperty("validation_result") ValidationResult validationResult
) {}
