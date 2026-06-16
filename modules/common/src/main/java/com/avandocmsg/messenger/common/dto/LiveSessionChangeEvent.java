package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Live session lifecycle (NATS {@code live.session} → pipeline → {@code msg.deliver.*}). */
public record LiveSessionChangeEvent(
    String change,
    @JsonProperty("live_session_id") String liveSessionId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("actor_id") String actorId,
    String title,
    String status,
    String mode,
    @JsonProperty("room_name") String roomName,
    String provider,
    @JsonProperty("viewer_count") Integer viewerCount,
    @JsonProperty("max_viewers") Integer maxViewers
) {}
