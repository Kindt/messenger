package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchHit(
    String type,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("message_id") String messageId,
    String snippet,
    String title
) {}
