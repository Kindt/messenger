package com.avandocmsg.messenger.api.live.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PatchLiveSessionDvrRequest(
    @JsonProperty("playlist_url") String playlistUrl
) {}
