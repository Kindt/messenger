package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BotUpdatesResponse(
    List<BotUpdateEvent> updates,
    @JsonProperty("next_offset") long nextOffset
) {}
