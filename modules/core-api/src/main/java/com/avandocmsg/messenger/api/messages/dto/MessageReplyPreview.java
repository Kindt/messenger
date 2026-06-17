package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Preview of the message being replied to")
public record MessageReplyPreview(
    @Schema(description = "Parent message ID") @JsonProperty("message_id") String messageId,
    @Schema(description = "Parent sender user ID") @JsonProperty("sender_id") String senderId,
    @Schema(description = "Short text preview; null if deleted or E2EE") String snippet,
    @Schema(description = "Whether the parent message is deleted") boolean deleted
) {}
