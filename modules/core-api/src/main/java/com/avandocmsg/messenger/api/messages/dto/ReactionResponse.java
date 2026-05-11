package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A reaction on a message")
public record ReactionResponse(
    @Schema(description = "Message ID") @JsonProperty("message_id") String messageId,
    @Schema(description = "User who reacted") @JsonProperty("user_id") String userId,
    @Schema(description = "Reaction emoji/string") String reaction,
    @Schema(description = "When the reaction was added") @JsonProperty("created_at") Instant createdAt
) {}
