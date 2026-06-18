package com.avandocmsg.messenger.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TestAuthPolicyRequest(
    @JsonProperty("provider_id") @JsonAlias("providerId") String providerId
) {}
