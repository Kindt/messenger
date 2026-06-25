package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Consolidated downstream routing envelope (spec 025 FR-012 Phase 1 dual-publish).
 * Legacy {@code msg.event.index|push|bot} payloads remain during migration.
 */
public record MessageDownstreamEvent(
    @JsonProperty("route") List<String> route,
    @JsonProperty("message_id") String messageId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("payload") MessageWorkerEvent payload
) {
}
