package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Статусы: {@code online}, {@code away}, {@code dnd}, {@code offline}. */
public record UpdatePresenceRequest(
    @JsonProperty("presence_status") String presenceStatus,
    @JsonProperty("custom_status_text") String customStatusText,
    @JsonProperty("dnd_until") java.time.Instant dndUntil
) {}
