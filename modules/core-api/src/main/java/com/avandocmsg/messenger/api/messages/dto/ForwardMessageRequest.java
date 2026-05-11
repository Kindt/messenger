package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForwardMessageRequest(
    @JsonProperty("target_chat_id") String targetChatId
) {}
