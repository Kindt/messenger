package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record PublicLinkCreatedResponse(
    @JsonProperty("link_id") String linkId,
    /** Raw token (shown once); use in {@code /pub/{token}} or {@code /auth-link/{token}} paths. */
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("link_kind") String linkKind,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("public_url_hint") String publicUrlHint
) {}
