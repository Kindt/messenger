package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PluginAdminDtos {

    private PluginAdminDtos() {}

    public record PresetJson(
        String id,
        String pluginClass,
        String runtimeKind,
        int configSchemaVersion,
        JsonNode capabilities
    ) {}

    public record ConfigureOutboundRequest(
        @JsonProperty("target_chat_id") UUID targetChatId,
        @JsonProperty("actor_user_id") UUID actorUserId,
        @JsonProperty("outbound_token") String outboundToken
    ) {}

    public record InstanceJson(
        UUID id,
        UUID orgId,
        String presetId,
        String botName,
        String displayName,
        boolean enabled,
        String pluginClass,
        String runtimeEndpoint,
        JsonNode configJson,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record CreateL0InstanceRequest(
        @JsonProperty("org_id") UUID orgId,
        @JsonProperty("bot_name") String botName,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("config_json") JsonNode configJson
    ) {}

    public record InvokePluginRequest(
        String type,
        String text,
        JsonNode payload
    ) {}

    public record UpdateInstanceRequest(
        @JsonProperty("enabled") Boolean enabled
    ) {}

    public record PresetListResponse(List<PresetJson> presets) {}

    public record InstanceListResponse(
        List<InstanceJson> instances,
        @JsonProperty("total_count") int totalCount,
        int limit,
        int offset
    ) {
        public InstanceListResponse(List<InstanceJson> instances) {
            this(instances, instances.size(), instances.size(), 0);
        }
    }

    public record IntegrationsComposeRequest(
        String action,
        List<String> services
    ) {}

    public record IntegrationsComposeResponse(
        String status,
        @JsonProperty("recommended_command") String recommendedCommand,
        String note
    ) {}
}
