package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Send message as bot (Bot token auth)")
public record BotSendMessageRequest(
    @Schema(description = "Target chat UUID")
    @JsonProperty("chat_id") String chatId,
    @Schema(description = "Message type", example = "text") String type,
    @Schema(description = "Message content") String content,
    @JsonProperty("client_msg_id") String clientMsgId
) {}
