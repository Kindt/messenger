package com.avandocmsg.messenger.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Ответ login/refresh; имена полей как у Keycloak OAuth (snake_case в JSON). */
public record LoginResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") int expiresIn
) {}
