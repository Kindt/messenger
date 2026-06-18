package com.avandocmsg.messenger.api.live.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** LiveKit SFU token for group voice/video call in chat (spec 019 US5). */
public record JoinLiveKitCallResponse(
    @JsonProperty("room_name") String roomName,
    @JsonProperty("livekit_url") String livekitUrl,
    @JsonProperty("access_token") String accessToken
) {}
