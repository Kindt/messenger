package com.avandocmsg.messenger.api.live.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LiveIngressResponse(
    @JsonProperty("rtmp_url") String rtmpUrl,
    @JsonProperty("stream_key") String streamKey
) {}
