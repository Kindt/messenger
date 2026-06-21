package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExternalStackComponentProfileSummary(
    @JsonProperty("component") String component,
    @JsonProperty("profile_count") int profileCount,
    @JsonProperty("supported_count") int supportedCount,
    @JsonProperty("candidate_count") int candidateCount,
    @JsonProperty("rejected_count") int rejectedCount,
    @JsonProperty("readiness_warning") String readinessWarning
) {}
