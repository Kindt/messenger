package com.avandocmsg.messenger.api.calls;

import com.avandocmsg.messenger.api.calls.dto.CallJoinResponse;
import com.avandocmsg.messenger.api.calls.dto.CallSignalRequest;
import com.avandocmsg.messenger.api.calls.dto.CallSignalResponse;
import com.avandocmsg.messenger.common.dto.CallSessionEvent;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.media.CallKind;
import com.avandocmsg.messenger.media.CallParticipant;
import com.avandocmsg.messenger.media.CallSession;
import com.avandocmsg.messenger.media.CallStatus;
import com.avandocmsg.messenger.media.InMemoryMediaRoomService;
import com.avandocmsg.messenger.media.MediaSignalingProcessor;
import com.avandocmsg.messenger.media.ParticipantRole;
import com.avandocmsg.messenger.media.SignalType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class UnifiedCallService {

    private final ChatPersistencePort chats;
    private final InMemoryMediaRoomService rooms;
    private final MediaSignalingProcessor signalingProcessor;
    private final CallSessionEventPublisher eventPublisher;

    public UnifiedCallService(ChatPersistencePort chats, InMemoryMediaRoomService rooms) {
        this(chats, rooms, MediaSignalingProcessor.NOOP, CallSessionEventPublisher.NOOP);
    }

    public UnifiedCallService(
        ChatPersistencePort chats,
        InMemoryMediaRoomService rooms,
        MediaSignalingProcessor signalingProcessor
    ) {
        this(chats, rooms, signalingProcessor, CallSessionEventPublisher.NOOP);
    }

    public UnifiedCallService(
        ChatPersistencePort chats,
        InMemoryMediaRoomService rooms,
        MediaSignalingProcessor signalingProcessor,
        CallSessionEventPublisher eventPublisher
    ) {
        this.chats = chats;
        this.rooms = rooms;
        this.signalingProcessor = signalingProcessor;
        this.eventPublisher = eventPublisher;
    }

    public Optional<CallJoinResponse> create(UUID chatId, UUID userId, String kind) {
        return create(chatId, userId, kind, "audio");
    }

    public Optional<CallJoinResponse> create(UUID chatId, UUID userId, String kind, String mediaIntent) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        var resolution = rooms.resolveOrCreate(chatId, userId, resolveKind(chatId, kind));
        var session = resolution.session();
        var role = session.createdBy().equals(userId) ? ParticipantRole.HOST : ParticipantRole.MEMBER;
        var participant = rooms.join(session.sessionId(), userId, role);
        if (resolution.created()) {
            eventPublisher.publish(new CallSessionEvent(
                CallSessionEvent.INVITED,
                session.chatId().toString(),
                session.sessionId().toString(),
                userId.toString(),
                normalizeMediaIntent(mediaIntent),
                session.createdAt().toString()
            ));
        }
        return Optional.of(toJoinResponse(session, participant));
    }

    public Optional<CallJoinResponse> join(UUID chatId, UUID sessionId, UUID userId) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        try {
            var session = rooms.requireSession(sessionId);
            if (!session.chatId().equals(chatId)) {
                return Optional.empty();
            }
            var participant = rooms.join(sessionId, userId, ParticipantRole.MEMBER);
            return Optional.of(toJoinResponse(rooms.requireSession(sessionId), participant));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    public boolean decline(UUID chatId, UUID sessionId, UUID userId) {
        if (!isChatMember(chatId, userId)) {
            return false;
        }
        try {
            var session = rooms.requireSession(sessionId);
            if (
                !session.chatId().equals(chatId)
                || session.createdBy().equals(userId)
                || session.status() != CallStatus.ACTIVE
            ) {
                return false;
            }
            eventPublisher.publish(new CallSessionEvent(
                CallSessionEvent.INVITATION_DECLINED,
                session.chatId().toString(),
                session.sessionId().toString(),
                session.createdBy().toString(),
                null,
                session.createdAt().toString(),
                userId.toString()
            ));
            return true;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return false;
        }
    }

    public boolean end(UUID chatId, UUID sessionId, UUID userId) {
        if (!isChatMember(chatId, userId)) {
            return false;
        }
        try {
            var session = rooms.requireSession(sessionId);
            if (!session.chatId().equals(chatId)) {
                return false;
            }
            rooms.publishSessionSignal(sessionId, SignalType.SESSION_ENDED);
            rooms.end(sessionId, userId);
            signalingProcessor.endSession(sessionId);
            return true;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return false;
        }
    }

    public boolean leave(
        UUID chatId,
        UUID sessionId,
        UUID userId,
        UUID participantId
    ) {
        if (!isChatMember(chatId, userId)) {
            return false;
        }
        try {
            var session = rooms.requireSession(sessionId);
            var participant = rooms.requireParticipant(sessionId, participantId);
            if (!session.chatId().equals(chatId) || !participant.userId().equals(userId)) {
                return false;
            }
            rooms.acceptParticipantSignal(
                sessionId,
                participantId,
                SignalType.HANGUP,
                null,
                null
            );
            signalingProcessor.processPending(sessionId);
            rooms.leave(sessionId, participantId, userId);
            rooms.publishParticipantSignalToOthers(
                sessionId,
                participantId,
                SignalType.PARTICIPANT_LEFT
            );
            return true;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return false;
        }
    }

    public boolean submitSignal(
        UUID chatId,
        UUID sessionId,
        UUID userId,
        UUID participantId,
        CallSignalRequest request
    ) {
        if (!isChatMember(chatId, userId) || request == null) {
            return false;
        }
        try {
            var session = rooms.requireSession(sessionId);
            var participant = rooms.requireParticipant(sessionId, participantId);
            if (!session.chatId().equals(chatId) || !participant.userId().equals(userId)) {
                return false;
            }
            rooms.acceptParticipantSignal(
                sessionId,
                participantId,
                parseSignalType(request.type()),
                request.sdp(),
                request.candidate()
            );
            signalingProcessor.processPending(sessionId);
            return true;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return false;
        }
    }

    public Optional<List<CallSignalResponse>> pollSignals(
        UUID chatId,
        UUID sessionId,
        UUID userId,
        UUID participantId,
        int limit
    ) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        try {
            var session = rooms.requireSession(sessionId);
            var participant = rooms.requireParticipant(sessionId, participantId);
            if (!session.chatId().equals(chatId) || !participant.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(
                rooms.drainParticipantSignals(sessionId, participantId, limit).stream()
                    .map(CallSignalResponse::from)
                    .toList()
            );
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private boolean isChatMember(UUID chatId, UUID userId) {
        return chats.getMemberRole(chatId, userId) != null;
    }

    private static CallKind parseKind(String value) {
        var normalized = value == null || value.isBlank() ? "group" : value.trim();
        try {
            return CallKind.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("unsupported call kind", error);
        }
    }

    private CallKind resolveKind(UUID chatId, String requestedKind) {
        var chatType = chats.getChatType(chatId).orElse("");
        if ("p2p".equalsIgnoreCase(chatType) || "direct".equalsIgnoreCase(chatType)) {
            return CallKind.DIRECT;
        }
        return parseKind(requestedKind);
    }

    private static SignalType parseSignalType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("signal type required");
        }
        try {
            return SignalType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("unsupported signal type", error);
        }
    }

    private static String normalizeMediaIntent(String value) {
        return "video".equalsIgnoreCase(value) ? "video" : "audio";
    }

    private static CallJoinResponse toJoinResponse(CallSession session, CallParticipant participant) {
        var signalingPath = "/api/v1/chats/" + session.chatId()
            + "/calls/" + session.sessionId() + "/signals";
        return new CallJoinResponse(
            session.sessionId().toString(),
            participant.participantId().toString(),
            session.chatId().toString(),
            session.kind().name().toLowerCase(Locale.ROOT),
            participant.role().name().toLowerCase(Locale.ROOT),
            session.status().name().toLowerCase(Locale.ROOT),
            session.nodeId(),
            signalingPath,
            List.of()
        );
    }
}
