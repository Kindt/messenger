package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConferenceResponse(
    @JsonProperty("conference_id") String conferenceId,
    @JsonProperty("chat_id") String chatId,
    String title,
    String status,
    @JsonProperty("room_slug") String roomSlug,
    @JsonProperty("join_url") String joinUrl,
    String provider,
    @JsonProperty("participant_count") int participantCount
) {}
