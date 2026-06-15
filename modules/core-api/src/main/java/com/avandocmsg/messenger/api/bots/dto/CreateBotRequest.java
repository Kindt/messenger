package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Create bot request")
public record CreateBotRequest(
    @Schema(description = "Globally unique @mention name (3-32 chars, alphanumeric/underscore)", example = "helpdesk_bot")
    @JsonProperty("bot_name") String botName,
    @Schema(description = "Display name", example = "Help Desk Bot")
    @JsonProperty("display_name") String displayName,
    @Schema(description = "MENTIONS_ONLY (default) or READ_ALL")
    @JsonProperty("listen_mode") String listenMode,
    @Schema(description = "Default webhook URL (HTTPS)")
    @JsonProperty("default_webhook_url") String defaultWebhookUrl
) {}
