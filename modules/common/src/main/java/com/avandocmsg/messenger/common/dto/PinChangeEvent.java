package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Pin/unpin fan-out (NATS {@code msg.pin} → pipeline → {@code msg.deliver.*}). */
public record PinChangeEvent(
    String change,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("message_id") String messageId,
    @JsonProperty("pinned_by") String pinnedBy,
    @JsonProperty("created_at") Long createdAt
) {}
