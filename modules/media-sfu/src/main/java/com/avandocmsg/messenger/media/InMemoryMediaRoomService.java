package com.avandocmsg.messenger.media;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMediaRoomService {

    private final Clock clock;
    private final Duration idleTimeout;
    private final String nodeId;
    private final Map<UUID, RoomState> rooms = new ConcurrentHashMap<>();

    public InMemoryMediaRoomService(Clock clock, Duration idleTimeout, String nodeId) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId required");
        }
        this.nodeId = nodeId;
    }

    public CallSession create(UUID chatId, UUID ownerId, CallKind kind) {
        var now = clock.instant();
        var session = new CallSession(
            UUID.randomUUID(),
            Objects.requireNonNull(chatId, "chatId"),
            Objects.requireNonNull(ownerId, "ownerId"),
            Objects.requireNonNull(kind, "kind"),
            CallStatus.ACTIVE,
            nodeId,
            now,
            now,
            null
        );
        rooms.put(session.sessionId(), new RoomState(session));
        return session;
    }

    public synchronized CallSession createOrGet(UUID chatId, UUID ownerId, CallKind kind) {
        return resolveOrCreate(chatId, ownerId, kind).session();
    }

    public synchronized CallSessionResolution resolveOrCreate(UUID chatId, UUID ownerId, CallKind kind) {
        var active = findActiveSession(chatId);
        if (active.isPresent()) {
            return new CallSessionResolution(active.orElseThrow(), false);
        }
        return new CallSessionResolution(create(chatId, ownerId, kind), true);
    }

    public Optional<CallSession> findActiveSession(UUID chatId) {
        Objects.requireNonNull(chatId, "chatId");
        return rooms.values().stream()
            .map(InMemoryMediaRoomService::sessionSnapshot)
            .filter(session -> session.chatId().equals(chatId))
            .filter(session -> session.status() == CallStatus.ACTIVE)
            .findFirst();
    }

    public CallParticipant join(UUID sessionId, UUID userId, ParticipantRole role) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            var existing = room.participants.values().stream()
                .filter(participant -> participant.userId().equals(userId))
                .filter(participant -> participant.state() != ParticipantState.LEFT)
                .findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            var now = clock.instant();
            var participant = new CallParticipant(
                UUID.randomUUID(),
                sessionId,
                Objects.requireNonNull(userId, "userId"),
                Objects.requireNonNull(role, "role"),
                ParticipantState.CONNECTED,
                now,
                now
            );
            room.participants.put(participant.participantId(), participant);
            room.session = room.session.touch(now);
            return participant;
        }
    }

    public CallSession end(UUID sessionId, UUID requestedBy) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            if (!room.session.createdBy().equals(requestedBy)) {
                throw new SecurityException("only call owner can end session");
            }
            if (room.session.status() == CallStatus.ENDED) {
                return room.session;
            }
            var now = clock.instant();
            room.session = room.session.end(now);
            room.participants.replaceAll((ignored, participant) -> participant.leave(now));
            return room.session;
        }
    }

    public CallParticipant leave(UUID sessionId, UUID participantId, UUID userId) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            var participant = room.participants.get(Objects.requireNonNull(participantId, "participantId"));
            if (participant == null || !participant.userId().equals(userId)) {
                throw new SecurityException("participant does not belong to user");
            }
            if (participant.state() == ParticipantState.LEFT) {
                return participant;
            }
            var now = clock.instant();
            var left = participant.leave(now);
            room.participants.put(participantId, left);
            room.session = room.session.touch(now);
            return left;
        }
    }

    public CallSession requireSession(UUID sessionId) {
        return sessionSnapshot(requireRoom(sessionId));
    }

    public CallParticipant requireParticipant(UUID sessionId, UUID participantId) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            var participant = room.participants.get(Objects.requireNonNull(participantId, "participantId"));
            if (participant == null) {
                throw new IllegalArgumentException("call participant not found");
            }
            return participant;
        }
    }

    public List<CallParticipant> listConnectedParticipants(UUID sessionId) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            return room.participants.values().stream()
                .filter(participant -> participant.state() == ParticipantState.CONNECTED)
                .toList();
        }
    }

    public MediaNegotiationResult selectDirectAudioCodec(
        UUID sessionId,
        UUID participantId,
        List<RtpCodecDescriptor> offeredCodecs
    ) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            if (room.session.kind() != CallKind.DIRECT) {
                throw new IllegalStateException("direct audio selection requires a direct session");
            }
            requireConnectedParticipant(room, participantId);
            var offeredPcmu = Objects.requireNonNull(offeredCodecs, "offeredCodecs").stream()
                .filter(RtpCodecDescriptor::isPcmuPtZero)
                .findFirst();
            if (offeredPcmu.isEmpty()) {
                return MediaNegotiationResult.rejected(MediaErrorCode.NO_COMMON_AUDIO_CODEC);
            }
            if (room.selectedAudioCodec == null) {
                room.selectedAudioCodec = RtpCodecDescriptor.pcmu();
            }
            if (!room.selectedAudioCodec.isPcmuPtZero() || !offeredPcmu.orElseThrow().isPcmuPtZero()) {
                return MediaNegotiationResult.rejected(MediaErrorCode.NO_COMMON_AUDIO_CODEC);
            }
            room.session = room.session.touch(clock.instant());
            return MediaNegotiationResult.accepted(room.selectedAudioCodec);
        }
    }

    public Optional<RtpCodecDescriptor> selectedAudioCodec(UUID sessionId) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            return Optional.ofNullable(room.selectedAudioCodec);
        }
    }

    public void touch(UUID sessionId) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            room.session = room.session.touch(clock.instant());
        }
    }

    public void acceptParticipantSignal(
        UUID sessionId,
        UUID participantId,
        SignalType type,
        String sdp,
        String candidate
    ) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            requireConnectedParticipant(room, participantId);
            room.nodeSignals.addLast(newSignal(sessionId, participantId, type, sdp, candidate));
            room.session = room.session.touch(clock.instant());
        }
    }

    public List<MediaSignal> drainNodeSignals(UUID sessionId, int limit) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            return drain(room.nodeSignals, limit);
        }
    }

    public void publishNodeSignal(
        UUID sessionId,
        UUID participantId,
        SignalType type,
        String sdp,
        String candidate
    ) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            requireConnectedParticipant(room, participantId);
            room.participantSignals
                .computeIfAbsent(participantId, ignored -> new ArrayDeque<>())
                .addLast(newSignal(sessionId, participantId, type, sdp, candidate));
            room.session = room.session.touch(clock.instant());
        }
    }

    public void publishNodeError(
        UUID sessionId,
        UUID participantId,
        MediaErrorCode errorCode
    ) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            requireConnectedParticipant(room, participantId);
            room.participantSignals
                .computeIfAbsent(participantId, ignored -> new ArrayDeque<>())
                .addLast(new MediaSignal(
                    UUID.randomUUID(),
                    sessionId,
                    participantId,
                    SignalType.ERROR,
                    null,
                    null,
                    Objects.requireNonNull(errorCode, "errorCode").name(),
                    clock.instant()
                ));
            room.session = room.session.touch(clock.instant());
        }
    }

    public List<MediaSignal> drainParticipantSignals(UUID sessionId, UUID participantId, int limit) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            requireParticipant(room, participantId);
            var signals = room.participantSignals.computeIfAbsent(participantId, ignored -> new ArrayDeque<>());
            return drain(signals, limit);
        }
    }

    public void publishParticipantSignalToOthers(
        UUID sessionId,
        UUID sourceParticipantId,
        SignalType type
    ) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            requireParticipant(room, sourceParticipantId);
            for (var participant : room.participants.values()) {
                if (
                    participant.state() == ParticipantState.CONNECTED
                    && !participant.participantId().equals(sourceParticipantId)
                ) {
                    room.participantSignals
                        .computeIfAbsent(participant.participantId(), ignored -> new ArrayDeque<>())
                        .addLast(newSignal(sessionId, sourceParticipantId, type, null, null));
                }
            }
            room.session = room.session.touch(clock.instant());
        }
    }

    public void publishSessionSignal(UUID sessionId, SignalType type) {
        var room = requireRoom(sessionId);
        synchronized (room) {
            ensureActive(room.session);
            for (var participant : room.participants.values()) {
                if (participant.state() == ParticipantState.CONNECTED) {
                    room.participantSignals
                        .computeIfAbsent(participant.participantId(), ignored -> new ArrayDeque<>())
                        .addLast(newSignal(sessionId, participant.participantId(), type, null, null));
                }
            }
            room.session = room.session.touch(clock.instant());
        }
    }

    public List<UUID> selectSubscriptions(
        UUID sessionId,
        UUID viewerParticipantId,
        List<UUID> orderedPublisherIds,
        int lastN
    ) {
        if (lastN < 0 || lastN > 64) {
            throw new IllegalArgumentException("lastN must be between 0 and 64");
        }
        var room = requireRoom(sessionId);
        synchronized (room) {
            requireConnectedParticipant(room, viewerParticipantId);
            var selected = new ArrayList<UUID>(Math.min(lastN, orderedPublisherIds.size()));
            for (var publisherId : orderedPublisherIds) {
                if (selected.size() >= lastN) {
                    break;
                }
                if (publisherId.equals(viewerParticipantId)) {
                    continue;
                }
                var publisher = room.participants.get(publisherId);
                if (publisher != null && publisher.state() == ParticipantState.CONNECTED) {
                    selected.add(publisherId);
                }
            }
            return List.copyOf(selected);
        }
    }

    public int removeIdleRooms() {
        var cutoff = clock.instant().minus(idleTimeout);
        var removed = 0;
        for (var entry : rooms.entrySet()) {
            var room = entry.getValue();
            synchronized (room) {
                var session = room.session;
                if (session.status() == CallStatus.ENDED || session.lastActivityAt().isBefore(cutoff)) {
                    if (rooms.remove(entry.getKey(), room)) {
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    public int activeRoomCount() {
        return (int) rooms.values().stream()
            .map(InMemoryMediaRoomService::sessionSnapshot)
            .filter(session -> session.status() == CallStatus.ACTIVE)
            .count();
    }

    public boolean containsSession(UUID sessionId) {
        return rooms.containsKey(Objects.requireNonNull(sessionId, "sessionId"));
    }

    private RoomState requireRoom(UUID sessionId) {
        var room = rooms.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (room == null) {
            throw new IllegalArgumentException("call session not found");
        }
        return room;
    }

    private static void ensureActive(CallSession session) {
        if (session.status() != CallStatus.ACTIVE) {
            throw new IllegalStateException("call session is not active");
        }
    }

    private static CallSession sessionSnapshot(RoomState room) {
        synchronized (room) {
            return room.session;
        }
    }

    private CallParticipant requireConnectedParticipant(RoomState room, UUID participantId) {
        var participant = requireParticipant(room, participantId);
        if (participant == null || participant.state() != ParticipantState.CONNECTED) {
            throw new SecurityException("participant is not connected");
        }
        return participant;
    }

    private static CallParticipant requireParticipant(RoomState room, UUID participantId) {
        var participant = room.participants.get(Objects.requireNonNull(participantId, "participantId"));
        if (participant == null) {
            throw new IllegalArgumentException("call participant not found");
        }
        return participant;
    }

    private MediaSignal newSignal(
        UUID sessionId,
        UUID participantId,
        SignalType type,
        String sdp,
        String candidate
    ) {
        return new MediaSignal(
            UUID.randomUUID(),
            sessionId,
            participantId,
            type,
            sdp,
            candidate,
            null,
            clock.instant()
        );
    }

    private static List<MediaSignal> drain(Deque<MediaSignal> source, int limit) {
        if (limit < 1 || limit > 256) {
            throw new IllegalArgumentException("limit must be between 1 and 256");
        }
        var drained = new ArrayList<MediaSignal>(Math.min(limit, source.size()));
        while (drained.size() < limit && !source.isEmpty()) {
            drained.add(source.removeFirst());
        }
        return List.copyOf(drained);
    }

    private static final class RoomState {
        private CallSession session;
        private final Map<UUID, CallParticipant> participants = new LinkedHashMap<>();
        private final Deque<MediaSignal> nodeSignals = new ArrayDeque<>();
        private final Map<UUID, Deque<MediaSignal>> participantSignals = new LinkedHashMap<>();
        private RtpCodecDescriptor selectedAudioCodec;

        private RoomState(CallSession session) {
            this.session = session;
        }
    }
}
