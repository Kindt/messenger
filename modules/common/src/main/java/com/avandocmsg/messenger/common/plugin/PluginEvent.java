package com.avandocmsg.messenger.common.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PluginEvent(
    String eventId,
    UUID botInstanceId,
    String pluginClass,
    String type,
    UUID userId,
    UUID chatId,
    String text,
    Map<String, Object> payload,
    Map<String, Object> configSnapshot
) {}
