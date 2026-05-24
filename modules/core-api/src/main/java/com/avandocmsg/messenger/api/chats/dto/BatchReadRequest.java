package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Batch mark messages as read")
public record BatchReadRequest(
    @JsonProperty("message_ids")
    @Schema(description = "Message UUIDs in this chat", example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
    List<String> messageIds
) {}
