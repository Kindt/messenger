package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MeIntegrationsMarketplaceResponse(
    @JsonProperty("categories") List<String> categories,
    @JsonProperty("items") List<MarketplaceItem> items
) {
    public record MarketplaceItem(
        @JsonProperty("id") String id,
        @JsonProperty("preset_id") String presetId,
        @JsonProperty("plugin_class") String pluginClass,
        @JsonProperty("label") String label,
        @JsonProperty("description") String description,
        @JsonProperty("category") String category,
        @JsonProperty("bot_name") String botName,
        @JsonProperty("icon_url") String iconUrl,
        @JsonProperty("launch_url") String launchUrl,
        @JsonProperty("open_mode") String openMode,
        @JsonProperty("connected") boolean connected
    ) {}
}
