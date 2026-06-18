package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "User-visible SmartApps / L0 integrations launcher")
public record MeIntegrationsResponse(
    @JsonProperty("items") List<IntegrationItem> items
) {
    public record IntegrationItem(
        String id,
        String label,
        @JsonProperty("bot_name") String botName,
        @JsonProperty("icon_url") String iconUrl,
        @JsonProperty("launch_url") String launchUrl,
        @JsonProperty("open_mode") String openMode
    ) {}
}
