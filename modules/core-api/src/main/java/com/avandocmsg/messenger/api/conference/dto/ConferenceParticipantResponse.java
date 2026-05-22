package com.avandocmsg.messenger.api.conference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Active conference participant")
public record ConferenceParticipantResponse(
    @JsonProperty("user_id") String userId,
    @Schema(description = "Login") String username,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("joined_at") Instant joinedAt
) {}
