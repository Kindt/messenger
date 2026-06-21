package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackProfilePreflightRequest(
    @JsonProperty("profile_id") String profileId,
    @JsonProperty("evidence") List<String> evidence
) {
    public ExternalStackProfilePreflightRequest(String profileId) {
        this(profileId, List.of());
    }

    public ExternalStackProfilePreflightRequest {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
