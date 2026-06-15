package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bot created (access_token shown once)")
public record CreateBotResponse(
    @JsonProperty("bot_id") String botId,
    @JsonProperty("bot_name") String botName,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("listen_mode") String listenMode,
    @JsonProperty("default_webhook_url") String defaultWebhookUrl,
    @JsonProperty("access_token") String accessToken
) {}
