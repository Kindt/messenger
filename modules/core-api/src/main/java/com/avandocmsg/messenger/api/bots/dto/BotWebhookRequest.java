package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Update default webhook URL")
public record BotWebhookRequest(
    @Schema(description = "HTTPS webhook URL")
    @JsonProperty("webhook_url") String webhookUrl
) {}
