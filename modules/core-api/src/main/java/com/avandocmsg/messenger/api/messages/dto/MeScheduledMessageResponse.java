package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Scheduled outbound message for current user")
public record MeScheduledMessageResponse(
    String id,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("message_type") String messageType,
    String content,
    @JsonProperty("scheduled_at") String scheduledAt,
    String status,
    @JsonProperty("created_at") String createdAt
) {}
