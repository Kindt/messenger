package com.avandocmsg.messenger.api.blocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record BlockedUserResponse(
    @JsonProperty("user_id") String userId,
    String username,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("blocked_at") Instant blockedAt
) {}
