package com.avandocmsg.messenger.api.live.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record LiveSessionResponse(
    @JsonProperty("live_session_id") String liveSessionId,
    @JsonProperty("chat_id") String chatId,
    String title,
    String status,
    String mode,
    @JsonProperty("room_name") String roomName,
    String provider,
    @JsonProperty("livekit_url") String livekitUrl,
    @JsonProperty("viewer_count") int viewerCount,
    @JsonProperty("max_viewers") int maxViewers,
    @JsonProperty("dvr_playlist_url") String dvrPlaylistUrl,
    @JsonProperty("moderation_state") String moderationState,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("ended_at") Instant endedAt
) {}
