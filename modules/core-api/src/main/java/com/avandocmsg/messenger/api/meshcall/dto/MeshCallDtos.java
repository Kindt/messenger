package com.avandocmsg.messenger.api.meshcall.dto;

import com.avandocmsg.messenger.api.meshcall.MeshCallRecordingRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public final class MeshCallDtos {

    private MeshCallDtos() {}

    public record MeshCallSessionResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("audit_recording_id") String auditRecordingId,
        @JsonProperty("media_mode") String mediaMode,
        String status,
        @JsonProperty("recording_mode") String recordingMode,
        @JsonProperty("livekit_room") String livekitRoom,
        @JsonProperty("livekit_url") String livekitUrl,
        @JsonProperty("livekit_token") String livekitToken
    ) {
        public static MeshCallSessionResponse started(
            String sessionId,
            String auditRecordingId,
            String mediaMode,
            String recordingMode,
            String livekitRoom,
            String livekitUrl,
            String livekitToken
        ) {
            return new MeshCallSessionResponse(
                sessionId,
                auditRecordingId,
                mediaMode,
                "active",
                recordingMode,
                livekitRoom,
                livekitUrl,
                livekitToken
            );
        }
    }

    public record MeshCallRecordingResponse(
        @JsonProperty("recording_id") String recordingId,
        String kind,
        String status,
        @JsonProperty("file_id") String fileId,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("created_at") Instant createdAt
    ) {
        public static MeshCallRecordingResponse started(String id, String kind) {
            return new MeshCallRecordingResponse(id, kind, "recording", null, null, Instant.now());
        }

        public static MeshCallRecordingResponse from(MeshCallRecordingRepository.RecordingRow row) {
            return new MeshCallRecordingResponse(
                row.id().toString(),
                row.kind(),
                row.status(),
                row.fileId() != null ? row.fileId().toString() : null,
                row.durationMs(),
                row.startedAt()
            );
        }
    }

    public record StartMeshCallSessionRequest(
        @JsonProperty("media_mode") String mediaMode
    ) {}

    public record StartMeshCallRecordingRequest(
        String kind
    ) {}

    public record CompleteMeshCallRecordingRequest(
        @JsonProperty("file_id") String fileId,
        @JsonProperty("duration_ms") Long durationMs
    ) {}
}
