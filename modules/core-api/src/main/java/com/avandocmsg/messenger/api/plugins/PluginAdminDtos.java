package com.avandocmsg.messenger.api.plugins;

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
        UUID targetChatId,
        UUID actorUserId,
        String outboundToken
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
        UUID orgId,
        String botName,
        String displayName,
        JsonNode configJson
    ) {}

    public record InvokePluginRequest(
        String type,
        String text,
        JsonNode payload
    ) {}

    public record PresetListResponse(List<PresetJson> presets) {}

    public record InstanceListResponse(List<InstanceJson> instances) {}
}
