package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record BotUpdateEvent(
    @JsonProperty("update_id") long updateId,
    @JsonProperty("event_type") String eventType,
    JsonNode payload
) {}
