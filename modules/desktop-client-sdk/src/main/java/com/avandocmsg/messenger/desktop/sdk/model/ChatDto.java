package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatDto(String id, @JsonProperty("chat_id") String chatId, String title) {
    public String resolvedId() {
        if (id != null && !id.isBlank()) {
            return id;
        }
        if (chatId != null && !chatId.isBlank()) {
            return chatId;
        }
        throw new IllegalStateException("chat missing id");
    }
}
