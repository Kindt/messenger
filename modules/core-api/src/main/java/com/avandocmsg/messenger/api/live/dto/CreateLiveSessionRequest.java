package com.avandocmsg.messenger.api.live.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateLiveSessionRequest(
    String title,
    @JsonProperty("as_host") Boolean asHost
) {}
