package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Subscribe bot to chat webhook delivery")
public record BotSubscribeRequest(
    @Schema(description = "Optional per-chat webhook override; uses bot default when omitted")
    @JsonProperty("webhook_url") String webhookUrl
) {}
