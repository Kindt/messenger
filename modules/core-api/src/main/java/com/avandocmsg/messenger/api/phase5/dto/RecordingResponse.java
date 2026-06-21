package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record RecordingResponse(
    @JsonProperty("recording_id") String recordingId,
    String status,
    @JsonProperty("conference_id") String conferenceId,
    @JsonProperty("created_at") Instant createdAt
) {
    public static RecordingResponse started(String id) {
        return new RecordingResponse(id, "recording", null, Instant.now());
    }

    public static RecordingResponse from(Phase5AdrRepository.RecordingRow row) {
        return new RecordingResponse(
            row.id().toString(),
            row.status(),
            row.conferenceId().toString(),
            row.createdAt());
    }
}
