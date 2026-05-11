package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateProfileRequest(
    @JsonProperty("display_name") String displayName,
    String phone
) {}
