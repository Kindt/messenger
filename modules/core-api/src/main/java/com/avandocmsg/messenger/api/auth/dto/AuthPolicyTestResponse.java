package com.avandocmsg.messenger.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthPolicyTestResponse(
    @JsonProperty("ok") boolean ok,
    @JsonProperty("message") String message
) {}
