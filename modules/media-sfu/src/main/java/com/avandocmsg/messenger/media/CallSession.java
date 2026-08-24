package com.avandocmsg.messenger.media;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CallSession(
    UUID sessionId,
    UUID chatId,
    UUID createdBy,
    CallKind kind,
    CallStatus status,
    String nodeId,
    Instant createdAt,
    Instant lastActivityAt,
    Instant endedAt
) {
    public CallSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(chatId, "chatId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
    }

    public CallSession touch(Instant now) {
        return new CallSession(sessionId, chatId, createdBy, kind, status, nodeId, createdAt, now, endedAt);
    }

    public CallSession end(Instant now) {
        return new CallSession(sessionId, chatId, createdBy, kind, CallStatus.ENDED, nodeId, createdAt, now, now);
    }
}
