package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Conference created/ended fan-out (NATS {@code msg.conference} → pipeline → {@code msg.deliver.*}). */
public record ConferenceChangeEvent(
    String change,
    @JsonProperty("conference_id") String conferenceId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("actor_id") String actorId,
    String title,
    String status,
    @JsonProperty("room_slug") String roomSlug,
    @JsonProperty("join_url") String joinUrl,
    String provider,
    @JsonProperty("participant_count") Integer participantCount
) {}
