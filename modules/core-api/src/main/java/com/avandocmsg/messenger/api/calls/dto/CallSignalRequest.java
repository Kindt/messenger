package com.avandocmsg.messenger.api.calls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CallSignalRequest(
    String type,
    String sdp,
    @JsonProperty("candidate") String candidate
) {}
