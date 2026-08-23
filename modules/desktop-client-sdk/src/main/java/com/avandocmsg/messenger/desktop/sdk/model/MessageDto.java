package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageDto(
    String id,
    String content,
    @JsonProperty("thread_id") String threadId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("sender_id") String senderId
) {
    public MessageDto(String id, String content, String threadId, String chatId) {
        this(id, content, threadId, chatId, null);
    }
}
