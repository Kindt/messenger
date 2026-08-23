package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SendMessageRequest(
    String type,
    String content,
    @JsonProperty("reply_to_msg_id") String replyToMsgId,
    @JsonProperty("thread_id") String threadId
) {
    public SendMessageRequest(String content) {
        this("text", content, null, null);
    }
}
