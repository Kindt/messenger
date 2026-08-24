package com.avandocmsg.messenger.media;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DirectAudioBindings {

    private static final int DIRECT_PARTICIPANT_LIMIT = 2;
    private final Map<UUID, SessionBindings> sessions = new ConcurrentHashMap<>();

    public Optional<MediaErrorCode> tryBind(UUID sessionId, UUID participantId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(participantId, "participantId");
        var bindings = sessions.computeIfAbsent(sessionId, ignored -> new SessionBindings());
        synchronized (bindings) {
            if (bindings.participantIds.contains(participantId)) {
                return Optional.empty();
            }
            if (bindings.participantIds.size() >= DIRECT_PARTICIPANT_LIMIT) {
                return Optional.of(MediaErrorCode.DIRECT_CALL_FULL);
            }
            bindings.participantIds.add(participantId);
            return Optional.empty();
        }
    }

    public Optional<UUID> remoteParticipant(UUID sessionId, UUID participantId) {
        var bindings = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (bindings == null) {
            return Optional.empty();
        }
        synchronized (bindings) {
            if (
                bindings.participantIds.size() != DIRECT_PARTICIPANT_LIMIT
                || !bindings.participantIds.contains(participantId)
            ) {
                return Optional.empty();
            }
            return bindings.participantIds.stream()
                .filter(candidate -> !candidate.equals(participantId))
                .findFirst();
        }
    }

    public List<ForwardedPacket> route(UUID sessionId, UUID publisherId, RtpPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet.payloadType() != 0) {
            return List.of();
        }
        return remoteParticipant(sessionId, publisherId)
            .map(participantId -> List.of(new ForwardedPacket(participantId, packet)))
            .orElseGet(List::of);
    }

    public boolean containsParticipant(UUID sessionId, UUID participantId) {
        var bindings = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (bindings == null) {
            return false;
        }
        synchronized (bindings) {
            return bindings.participantIds.contains(participantId);
        }
    }

    public int bindingCount(UUID sessionId) {
        var bindings = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (bindings == null) {
            return 0;
        }
        synchronized (bindings) {
            return bindings.participantIds.size() == DIRECT_PARTICIPANT_LIMIT
                ? DIRECT_PARTICIPANT_LIMIT
                : 0;
        }
    }

    public void removeParticipant(UUID sessionId, UUID participantId) {
        var bindings = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (bindings == null) {
            return;
        }
        synchronized (bindings) {
            bindings.participantIds.remove(Objects.requireNonNull(participantId, "participantId"));
            if (bindings.participantIds.isEmpty()) {
                sessions.remove(sessionId, bindings);
            }
        }
    }

    public void removeSession(UUID sessionId) {
        sessions.remove(Objects.requireNonNull(sessionId, "sessionId"));
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }

    public record ForwardedPacket(UUID participantId, RtpPacket packet) {
        public ForwardedPacket {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(packet, "packet");
        }
    }

    private static final class SessionBindings {
        private final Set<UUID> participantIds = new LinkedHashSet<>();
    }
}
