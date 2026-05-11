package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePublicLinkRequest(
    /** {@code A}, {@code B}, or {@code C} (ТЗ п. 15). */
    @JsonProperty("link_kind") String linkKind,
    @JsonProperty("password") String password,
    @JsonProperty("ttl_seconds") Long ttlSeconds
) {}
