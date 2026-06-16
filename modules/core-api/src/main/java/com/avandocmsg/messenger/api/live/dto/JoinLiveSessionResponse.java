package com.avandocmsg.messenger.api.live.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinLiveSessionResponse(
    @JsonProperty("live_session_id") String liveSessionId,
    @JsonProperty("room_name") String roomName,
    @JsonProperty("livekit_url") String livekitUrl,
    @JsonProperty("access_token") String accessToken,
    String role,
    @JsonProperty("viewer_count") int viewerCount,
    @JsonProperty("max_viewers") int maxViewers
) {}
