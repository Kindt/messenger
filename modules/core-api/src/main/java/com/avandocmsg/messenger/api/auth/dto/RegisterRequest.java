package com.avandocmsg.messenger.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterRequest(
    String username,
    String password,
    @JsonProperty("display_name") String displayName
) {}
