package com.avandocmsg.messenger.media;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MediaSignal(
    UUID signalId,
    UUID sessionId,
    UUID participantId,
    SignalType type,
    String sdp,
    String candidate,
    String errorCode,
    Instant createdAt
) {
    public MediaSignal {
        Objects.requireNonNull(signalId, "signalId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        if ((type == SignalType.OFFER || type == SignalType.ANSWER) && (sdp == null || sdp.isBlank())) {
            throw new IllegalArgumentException("sdp required");
        }
        if (type == SignalType.ICE && (candidate == null || candidate.isBlank())) {
            throw new IllegalArgumentException("candidate required");
        }
        if (type == SignalType.ERROR && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("errorCode required");
        }
        if (type != SignalType.ERROR && errorCode != null) {
            throw new IllegalArgumentException("errorCode only allowed for error signals");
        }
        if (sdp != null && sdp.length() > 262_144) {
            throw new IllegalArgumentException("sdp too large");
        }
        if (candidate != null && candidate.length() > 16_384) {
            throw new IllegalArgumentException("candidate too large");
        }
    }
}
