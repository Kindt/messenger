package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
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
    public String resolvedSessionId() {
        return sessionId;
    }
}
