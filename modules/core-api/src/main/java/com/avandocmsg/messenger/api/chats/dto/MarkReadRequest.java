package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarkReadRequest(
    /** Если не задан — последнее сообщение в чате. */
    @JsonProperty("up_to_message_id") String upToMessageId
) {}
