package com.avandocmsg.messenger.desktop.sdk.mls;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlsSessionInfo(
    @JsonProperty("session_id") String sessionId,
    long epoch
) {}
