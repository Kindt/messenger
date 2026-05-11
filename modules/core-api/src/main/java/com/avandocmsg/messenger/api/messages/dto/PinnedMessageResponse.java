package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A pinned message in a chat")
public record PinnedMessageResponse(
    @Schema(description = "Chat ID") @JsonProperty("chat_id") String chatId,
    @Schema(description = "Message ID") @JsonProperty("message_id") String messageId,
    @Schema(description = "User who pinned the message") @JsonProperty("pinned_by") String pinnedBy,
    @Schema(description = "When the message was pinned") @JsonProperty("created_at") Instant createdAt
) {}
