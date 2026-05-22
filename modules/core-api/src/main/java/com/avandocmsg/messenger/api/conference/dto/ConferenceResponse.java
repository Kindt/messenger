package com.avandocmsg.messenger.api.conference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Метаданные конференции; медиа идёт через WebRTC/Jitsi по {@link #joinUrl()}. */
public record ConferenceResponse(
    @JsonProperty("conference_id") String conferenceId,
    @JsonProperty("chat_id") String chatId,
    String title,
    String status,
    @JsonProperty("room_slug") String roomSlug,
    @JsonProperty("join_url") String joinUrl,
    String provider,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("ended_at") Instant endedAt,
    @JsonProperty("participant_count") int participantCount
) {}
