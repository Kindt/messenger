package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to pin/unpin a message")
public record PinRequest(
    @Schema(description = "Message ID to pin") @JsonProperty("message_id") String messageId
) {}
