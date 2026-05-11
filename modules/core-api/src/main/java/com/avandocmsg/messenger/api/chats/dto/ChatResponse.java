package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatResponse(
    String id,
    String title,
    String type,
    @JsonProperty("owner_id") String ownerId,
    @JsonProperty("member_count") int memberCount,
    boolean muted,
    @JsonProperty("ttl_seconds") Integer ttlSeconds,
    @JsonProperty("created_at") Instant createdAt
) {}
