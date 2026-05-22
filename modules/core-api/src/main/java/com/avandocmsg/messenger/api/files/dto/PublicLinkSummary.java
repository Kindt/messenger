package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Активная публичная ссылка (без токена). */
public record PublicLinkSummary(
    String id,
    @JsonProperty("link_kind") String linkKind,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("created_at") Instant createdAt
) {}
