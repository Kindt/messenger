package com.avandocmsg.messenger.api.chats.bans.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatBanResponse(
    String id,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("banned_by") String bannedBy,
    String reason,
    @JsonProperty("created_at") Instant createdAt
) {}
