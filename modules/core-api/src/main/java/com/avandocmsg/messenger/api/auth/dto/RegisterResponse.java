package com.avandocmsg.messenger.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterResponse(
    @JsonProperty("user_id") String userId,
    String username,
    @JsonProperty("display_name") String displayName
) {}
