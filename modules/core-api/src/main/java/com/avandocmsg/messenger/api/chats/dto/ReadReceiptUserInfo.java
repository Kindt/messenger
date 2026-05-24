package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "User who read a message")
public record ReadReceiptUserInfo(
    @JsonProperty("user_id") String userId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("read_at") Instant readAt
) {}
