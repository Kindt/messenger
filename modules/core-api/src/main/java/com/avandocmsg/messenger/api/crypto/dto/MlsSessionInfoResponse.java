package com.avandocmsg.messenger.api.crypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlsSessionInfoResponse(
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("epoch") long epoch
) {}
