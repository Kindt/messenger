package com.avandocmsg.messenger.media;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CallParticipant(
    UUID participantId,
    UUID sessionId,
    UUID userId,
    ParticipantRole role,
    ParticipantState state,
    Instant joinedAt,
    Instant lastSeenAt
) {
    public CallParticipant {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    }

    public CallParticipant leave(Instant now) {
        return new CallParticipant(participantId, sessionId, userId, role, ParticipantState.LEFT, joinedAt, now);
    }
}
