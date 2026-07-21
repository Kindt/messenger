package com.avandocmsg.messenger.api.meshcall;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.live.LiveKitEgressClient;
import com.avandocmsg.messenger.api.live.LiveKitTokenService;
import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.MeshCallRecordingResponse;
import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.MeshCallSessionResponse;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MeshCallRecordingService {

    private static final String KIND_AUDIT = "audit";
    private static final String KIND_USER = "user";
    private static final String KIND_COMPOSITE = "composite";
    private static final String MODE_MESH = "mesh";
    private static final String MODE_COMPOSITE = "composite";
    private static final String AUDIT_ENTITY_SESSION = "mesh_call_session";
    private static final String AUDIT_ENTITY_RECORDING = "mesh_call_recording";
    private static final String AUDIT_DETAIL_SESSION_ID = "{\"session_id\":\"";

    private final MeshCallRecordingRepository repository;
    private final ChatPersistencePort chatPersistencePort;
    private final AuditPort auditPort;
    private final LiveKitTokenService liveKitTokenService;
    private final LiveKitEgressClient egressClient;
    private final AppConfig appConfig;
    private final FileMetadataPort fileMetadataPort;
    private final UuidGenerator uuidGenerator;

    public MeshCallRecordingService( // NOSONAR java:S107
        MeshCallRecordingRepository repository,
        ChatPersistencePort chatPersistencePort,
        AuditPort auditPort,
        LiveKitTokenService liveKitTokenService,
        LiveKitEgressClient egressClient,
        AppConfig appConfig,
        FileMetadataPort fileMetadataPort,
        UuidGenerator uuidGenerator
    ) {
        this.repository = repository;
        this.chatPersistencePort = chatPersistencePort;
        this.auditPort = auditPort;
        this.liveKitTokenService = liveKitTokenService;
        this.egressClient = egressClient;
        this.appConfig = appConfig;
        this.fileMetadataPort = fileMetadataPort;
        this.uuidGenerator = uuidGenerator;
    }

    public Optional<MeshCallSessionResponse> startSession(UUID chatId, UUID userId, String mediaMode) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        var mode = normalizeMediaMode(mediaMode);
        var sessionId = repository.createSession(chatId, userId, mode);
        auditPort.record(userId, "mesh_call.session.started", AUDIT_ENTITY_SESSION, sessionId.toString(),
            "{\"chat_id\":\"" + chatId + "\",\"media_mode\":\"" + mode + "\"}");

        if (appConfig.compositeCallRecordingEnabled()) {
            var room = "mesh-" + sessionId.toString().replace("-", "");
            var filepath = "mesh-recordings/" + sessionId + "/audit.mp4";
            var egressId = egressClient.startRoomComposite(room, filepath);
            if (egressId.isPresent()) {
                var compositeId = repository.createRecording(sessionId, chatId, userId, KIND_COMPOSITE);
                repository.attachSessionComposite(sessionId, chatId, room, egressId.get(), MODE_COMPOSITE);
                repository.attachRecordingEgress(compositeId, sessionId, chatId, egressId.get(), filepath);
                auditPort.record(userId, "mesh_call.composite_recording.started", AUDIT_ENTITY_RECORDING,
                    compositeId.toString(), AUDIT_DETAIL_SESSION_ID + sessionId + "\",\"egress_id\":\"" + egressId.get() + "\"}");
                return Optional.of(buildSessionResponse(sessionId, compositeId, mode, room, userId));
            }
        }

        var auditId = repository.createRecording(sessionId, chatId, userId, KIND_AUDIT);
        repository.attachSessionComposite(sessionId, chatId, null, null, MODE_MESH);
        auditPort.record(userId, "mesh_call.audit_recording.started", AUDIT_ENTITY_RECORDING, auditId.toString(),
            AUDIT_DETAIL_SESSION_ID + sessionId + "\",\"kind\":\"audit\"}");
        return Optional.of(buildSessionResponse(sessionId, auditId, mode, null, userId));
    }

    public Optional<MeshCallSessionResponse> joinSession(UUID chatId, UUID userId, UUID sessionId) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return Optional.empty();
        }
        var session = repository.findSession(sessionId, chatId);
        if (session.isEmpty() || !"active".equals(session.get().status())) {
            return Optional.empty();
        }
        auditPort.record(userId, "mesh_call.session.joined", AUDIT_ENTITY_SESSION, sessionId.toString(), null);
        if (MODE_COMPOSITE.equals(session.get().recordingMode())) {
            return Optional.of(buildSessionResponse(
                sessionId,
                null,
                session.get().mediaMode(),
                session.get().livekitRoom(),
                userId
            ));
        }
        var auditId = repository.createRecording(sessionId, chatId, userId, KIND_AUDIT);
        auditPort.record(userId, "mesh_call.audit_recording.started", AUDIT_ENTITY_RECORDING, auditId.toString(),
            AUDIT_DETAIL_SESSION_ID + sessionId + "\",\"kind\":\"audit\"}");
        return Optional.of(buildSessionResponse(sessionId, auditId, session.get().mediaMode(), null, userId));
    }

    public Optional<Boolean> endSession(UUID chatId, UUID userId, UUID sessionId) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return Optional.empty();
        }
        var session = repository.findSession(sessionId, chatId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (MODE_COMPOSITE.equals(session.get().recordingMode()) && session.get().egressId() != null) {
            finalizeCompositeEgress(session.get().egressId(), sessionId, chatId, userId, KIND_COMPOSITE);
        }
        repository.endSession(sessionId, chatId);
        auditPort.record(userId, "mesh_call.session.ended", AUDIT_ENTITY_SESSION, sessionId.toString(), null);
        return Optional.of(Boolean.TRUE);
    }

    public Optional<MeshCallRecordingResponse> startUserRecording(UUID chatId, UUID userId, UUID sessionId) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return Optional.empty();
        }
        var session = repository.findSession(sessionId, chatId);
        if (session.isEmpty() || !"active".equals(session.get().status())) {
            return Optional.empty();
        }
        var recId = repository.createRecording(sessionId, chatId, userId, KIND_USER);
        if (MODE_COMPOSITE.equals(session.get().recordingMode()) && session.get().livekitRoom() != null) {
            var filepath = "mesh-recordings/" + sessionId + "/user-" + recId + ".mp4";
            var egressId = egressClient.startRoomComposite(session.get().livekitRoom(), filepath);
            if (egressId.isEmpty()) {
                repository.failRecording(recId, sessionId, chatId);
                return Optional.empty();
            }
            repository.attachRecordingEgress(recId, sessionId, chatId, egressId.get(), filepath);
        }
        auditPort.record(userId, "mesh_call.user_recording.started", AUDIT_ENTITY_RECORDING, recId.toString(),
            AUDIT_DETAIL_SESSION_ID + sessionId + "\"}");
        return Optional.of(MeshCallRecordingResponse.started(recId.toString(), KIND_USER));
    }

    public Optional<Boolean> stopUserRecording(UUID chatId, UUID userId, UUID sessionId, UUID recordingId) {
        if (!isChatMember(chatId, userId) || sessionId == null || recordingId == null) {
            return Optional.empty();
        }
        var rec = repository.findRecording(recordingId, sessionId, chatId);
        if (rec.isEmpty() || !KIND_USER.equals(rec.get().kind()) || !userId.equals(rec.get().recordedBy())) {
            return Optional.empty();
        }
        var row = rec.get();
        if (row.egressId() != null) {
            egressClient.stopEgress(row.egressId());
            finalizeCompositeEgress(row.egressId(), sessionId, chatId, userId, KIND_USER);
            return Optional.of(Boolean.TRUE);
        }
        return Optional.empty();
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
        auditPort.record(userId, "mesh_call.recording.completed", AUDIT_ENTITY_RECORDING, recordingId.toString(),
            "{\"file_id\":\"" + fileId + "\",\"duration_ms\":" + durationMs + "}");
        return Optional.of(Boolean.TRUE);
    }

    public List<MeshCallRecordingResponse> listRecordings(UUID chatId, UUID userId, UUID sessionId) {
        if (!isChatMember(chatId, userId) || sessionId == null) {
            return List.of();
        }
        var includeAudit = isChatAdmin(chatId, userId);
        return repository.listRecordings(sessionId, chatId, userId, includeAudit).stream()
            .map(MeshCallRecordingResponse::from)
            .toList();
    }

    private MeshCallSessionResponse buildSessionResponse(
        UUID sessionId,
        UUID auditRecordingId,
        String mediaMode,
        String livekitRoom,
        UUID userId
    ) {
        String token = null;
        String url = null;
        String recordingMode = livekitRoom != null ? MODE_COMPOSITE : MODE_MESH;
        if (livekitRoom != null && liveKitTokenService.enabled()) {
            url = liveKitTokenService.livekitUrl();
            token = liveKitTokenService.createAccessToken(livekitRoom, userId.toString(), true, 3600);
        }
        return MeshCallSessionResponse.started(
            sessionId.toString(),
            auditRecordingId != null ? auditRecordingId.toString() : null,
            mediaMode,
            recordingMode,
            livekitRoom,
            url,
            token
        );
    }

    private void finalizeCompositeEgress(String egressId, UUID sessionId, UUID chatId, UUID userId, String kind) {
        egressClient.stopEgress(egressId);
        var recOpt = repository.findRecordingByEgress(sessionId, chatId, egressId);
        if (recOpt.isEmpty()) {
            return;
        }
        var rec = recOpt.get();
        var info = egressClient.getEgress(egressId).orElse(null);
        var filepath = info != null && info.filepath() != null && !info.filepath().isBlank()
            ? info.filepath()
            : rec.storageKey();
        if (filepath == null || filepath.isBlank()) {
            return;
        }
        var fileId = registerStorageFile(filepath, userId, kind + "-recording.mp4");
        fileId.ifPresent(id -> repository.completeRecording(rec.id(), sessionId, chatId, id, 0L));
    }

    private Optional<UUID> registerStorageFile(String storageKey, UUID uploadedBy, String filename) {
        var fileId = FileId.of(uuidGenerator.randomUuid());
        var hash = "egress-" + storageKey.hashCode();
        return fileMetadataPort.insertWithStorage(
            fileId,
            filename,
            "video/mp4",
            0L,
            new UserId(uploadedBy),
            hash,
            storageKey
        ).map(stored -> stored.id().value());
    }

    private boolean isChatMember(UUID chatId, UUID userId) {
        return chatPersistencePort.getMemberRole(chatId, userId) != null;
    }

    private boolean isChatAdmin(UUID chatId, UUID userId) {
        var role = chatPersistencePort.getMemberRole(chatId, userId);
        return "owner".equals(role) || "admin".equals(role);
    }

    private static String normalizeMediaMode(String mediaMode) {
        return "video".equalsIgnoreCase(mediaMode) ? "video" : "audio";
    }
}
