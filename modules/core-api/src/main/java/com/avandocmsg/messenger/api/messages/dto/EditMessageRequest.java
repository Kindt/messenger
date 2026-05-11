package com.avandocmsg.messenger.api.messages.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to edit a message")
public record EditMessageRequest(
    @Schema(description = "New message content") String content
) {}
