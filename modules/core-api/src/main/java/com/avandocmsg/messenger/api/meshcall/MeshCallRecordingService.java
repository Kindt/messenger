package com.avandocmsg.messenger.api.meshcall;

import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.MeshCallRecordingResponse;
import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.MeshCallSessionResponse;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MeshCallRecordingService {

    private static final String KIND_AUDIT = "audit";
    private static final String KIND_USER = "user";

    private final MeshCallRecordingRepository repository;
    private final ChatPersistencePort chatPersistencePort;
    private final AuditPort auditPort;

    public MeshCallRecordingService(
        MeshCallRecordingRepository repository,
        ChatPersistencePort chatPersistencePort,
        AuditPort auditPort
    ) {
        this.repository = repository;
        this.chatPersistencePort = chatPersistencePort;
        this.auditPort = auditPort;
    }

    public Optional<MeshCallSessionResponse> startSession(UUID chatId, UUID userId, String mediaMode) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        var mode = normalizeMediaMode(mediaMode);
        var sessionId = repository.createSession(chatId, userId, mode);
        var auditId = repository.createRecording(sessionId, chatId, userId, KIND_AUDIT);
        auditPort.record(userId, "mesh_call.session.started", "mesh_call_session", sessionId.toString(),
            "{\"chat_id\":\"" + chatId + "\",\"media_mode\":\"" + mode + "\"}");
        auditPort.record(userId, "mesh_call.audit_recording.started", "mesh_call_recording", auditId.toString(),
            "{\"session_id\":\"" + sessionId + "\",\"kind\":\"audit\"}");
        return Optional.of(MeshCallSessionResponse.started(
            sessionId.toString(),
            auditId.toString(),
            mode
        ));
    }

    public Optional<Boolean> endSession(UUID chatId, UUID userId, UUID sessionId) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return Optional.empty();
        }
        var session = repository.findSession(sessionId, chatId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        repository.endSession(sessionId, chatId);
        auditPort.record(userId, "mesh_call.session.ended", "mesh_call_session", sessionId.toString(), null);
        return Optional.of(Boolean.TRUE);
    }

    public Optional<MeshCallSessionResponse> joinSession(UUID chatId, UUID userId, UUID sessionId) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return Optional.empty();
        }
        var session = repository.findSession(sessionId, chatId);
        if (session.isEmpty() || !"active".equals(session.get().status())) {
            return Optional.empty();
        }
        var auditId = repository.createRecording(sessionId, chatId, userId, KIND_AUDIT);
        auditPort.record(userId, "mesh_call.session.joined", "mesh_call_session", sessionId.toString(), null);
        auditPort.record(userId, "mesh_call.audit_recording.started", "mesh_call_recording", auditId.toString(),
            "{\"session_id\":\"" + sessionId + "\",\"kind\":\"audit\"}");
        return Optional.of(new MeshCallSessionResponse(
            sessionId.toString(),
            auditId.toString(),
            session.get().mediaMode(),
            session.get().status()
        ));
    }

    public Optional<MeshCallRecordingResponse> startUserRecording(
        UUID chatId,
        UUID userId,
        UUID sessionId
    ) {
        return startRecording(chatId, userId, sessionId, KIND_USER);
    }

    public Optional<MeshCallRecordingResponse> startRecording(
        UUID chatId,
        UUID userId,
        UUID sessionId,
        String kind
    ) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return Optional.empty();
        }
        if (!KIND_USER.equals(kind)) {
            return Optional.empty();
        }
        var session = repository.findSession(sessionId, chatId);
        if (session.isEmpty() || !"active".equals(session.get().status())) {
            return Optional.empty();
        }
        var recId = repository.createRecording(sessionId, chatId, userId, KIND_USER);
        auditPort.record(userId, "mesh_call.user_recording.started", "mesh_call_recording", recId.toString(),
            "{\"session_id\":\"" + sessionId + "\"}");
        return Optional.of(MeshCallRecordingResponse.started(recId.toString(), KIND_USER));
    }

    public Optional<Boolean> completeRecording(
        UUID chatId,
        UUID userId,
        UUID sessionId,
        UUID recordingId,
        UUID fileId,
        long durationMs
    ) {
        if (!isChatMember(chatId, userId) || sessionId == null || recordingId == null || fileId == null) {
            return Optional.empty();
        }
        var rec = repository.findRecording(recordingId, sessionId, chatId);
        if (rec.isEmpty()) {
            return Optional.empty();
        }
        var row = rec.get();
        if (KIND_USER.equals(row.kind()) && !userId.equals(row.recordedBy())) {
            return Optional.empty();
        }
        if (KIND_AUDIT.equals(row.kind()) && !userId.equals(row.recordedBy())) {
            return Optional.empty();
        }
        if (!repository.completeRecording(recordingId, sessionId, chatId, fileId, durationMs)) {
            return Optional.empty();
        }
        var event = KIND_AUDIT.equals(row.kind())
            ? "mesh_call.audit_recording.completed"
            : "mesh_call.user_recording.completed";
        auditPort.record(userId, event, "mesh_call_recording", recordingId.toString(),
            "{\"file_id\":\"" + fileId + "\",\"duration_ms\":" + durationMs + "}");
        return Optional.of(Boolean.TRUE);
    }

    public List<MeshCallRecordingResponse> listRecordings(
        UUID chatId,
        UUID userId,
        UUID sessionId
    ) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return List.of();
        }
        var includeAudit = isChatAdmin(chatId, userId);
        return repository.listRecordings(sessionId, chatId, userId, includeAudit).stream()
            .map(MeshCallRecordingResponse::from)
            .toList();
    }

    private boolean isChatMember(UUID chatId, UUID userId) {
        return chatPersistencePort.getMemberRole(chatId, userId) != null;
    }

    private boolean isChatAdmin(UUID chatId, UUID userId) {
        var role = chatPersistencePort.getMemberRole(chatId, userId);
        return "owner".equals(role) || "admin".equals(role);
    }

    private static String normalizeMediaMode(String mediaMode) {
        if ("video".equalsIgnoreCase(mediaMode)) {
            return "video";
        }
        return "audio";
    }
}
