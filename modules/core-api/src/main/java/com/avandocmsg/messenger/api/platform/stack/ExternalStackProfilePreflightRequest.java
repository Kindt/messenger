package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExternalStackProfilePreflightRequest(
    @JsonProperty("profile_id") String profileId
) {}
