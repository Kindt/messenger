package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatMemberResponse(
    @JsonProperty("user_id") String userId,
    String username,
    @JsonProperty("display_name") String displayName,
    String role,
    boolean muted,
    boolean banned,
    @JsonProperty("joined_at") Instant joinedAt
) {}
