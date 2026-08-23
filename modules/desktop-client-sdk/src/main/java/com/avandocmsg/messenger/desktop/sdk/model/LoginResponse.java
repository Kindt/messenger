package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") Integer expiresIn
) {
    public LoginResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("empty access_token");
        }
    }

    public int expiresInOrZero() {
        return expiresIn == null ? 0 : expiresIn;
    }
}
