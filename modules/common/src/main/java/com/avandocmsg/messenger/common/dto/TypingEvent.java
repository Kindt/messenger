package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Публикуется в NATS {@code msg.typing} для realtime (ТЗ п. 19). */
public record TypingEvent(
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("user_id") String userId,
    long ts
) {}
