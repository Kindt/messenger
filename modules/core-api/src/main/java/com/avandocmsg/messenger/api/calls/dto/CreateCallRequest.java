package com.avandocmsg.messenger.api.calls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCallRequest(
    String kind,
    @JsonProperty("media_intent") String mediaIntent
) {
    public CreateCallRequest(String kind) {
        this(kind, null);
    }
}
