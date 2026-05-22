package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Активная публичная ссылка владельца с привязкой к файлу. */
public record OwnerPublicLinkSummary(
    String id,
    @JsonProperty("file_id") String fileId,
    @JsonProperty("link_kind") String linkKind,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("created_at") Instant createdAt,
    String filename
) {}
