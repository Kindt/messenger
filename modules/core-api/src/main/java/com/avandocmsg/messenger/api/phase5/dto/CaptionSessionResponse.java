package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record CaptionSessionResponse(
    @JsonProperty("session_id") String sessionId,
    String status,
    String language,
    @JsonProperty("transcript_json") String transcriptJson,
    @JsonProperty("created_at") Instant createdAt
) {
    public static CaptionSessionResponse from(Phase5AdrRepository.CaptionSessionRow row) {
        return new CaptionSessionResponse(
            row.id().toString(),
            row.status(),
            row.language(),
            row.transcriptJson(),
            row.createdAt());
    }
}
