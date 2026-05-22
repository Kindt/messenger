package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FileMessageRefResponse(
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("message_id") String messageId
) {}
