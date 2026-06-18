package com.avandocmsg.messenger.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Public login methods for an organization")
public record LoginOptionsResponse(
    @JsonProperty("org_id") String orgId,
    @JsonProperty("org_slug") String orgSlug,
    @JsonProperty("registration_allowed") boolean registrationAllowed,
    @JsonProperty("methods") List<LoginMethodJson> methods
) {
    public record LoginMethodJson(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("label") String label,
        @JsonProperty("authorization_url") String authorizationUrl
    ) {}
}
