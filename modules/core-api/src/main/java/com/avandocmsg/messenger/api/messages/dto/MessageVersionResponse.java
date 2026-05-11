package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A previous version of an edited message")
public record MessageVersionResponse(
    @Schema(description = "Version ID") long id,
    @Schema(description = "Message ID") @JsonProperty("message_id") String messageId,
    @Schema(description = "Content at this version") String content,
    @Schema(description = "User who made the edit") @JsonProperty("edited_by") String editedBy,
    @Schema(description = "When this version was created") @JsonProperty("created_at") Instant createdAt
) {}
