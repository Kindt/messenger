package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bot profile (no access token)")
public record BotResponse(
    @JsonProperty("bot_id") String botId,
    @JsonProperty("bot_name") String botName,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("listen_mode") String listenMode,
    @JsonProperty("default_webhook_url") String defaultWebhookUrl,
    @JsonProperty("created_at_epoch_ms") Long createdAtEpochMs,
    @JsonProperty("avatar_url") String avatarUrl
) {}
