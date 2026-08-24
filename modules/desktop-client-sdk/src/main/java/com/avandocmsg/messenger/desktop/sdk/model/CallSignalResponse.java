package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CallSignalResponse(
    String id,
    String type,
    String sdp,
    String candidate,
    @JsonProperty("error_code") String errorCode,
    @JsonProperty("created_at") Instant createdAt
) {}
