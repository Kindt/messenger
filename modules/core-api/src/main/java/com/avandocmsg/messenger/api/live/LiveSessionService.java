package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.live.dto.CreateLiveSessionRequest;
import com.avandocmsg.messenger.api.live.dto.JoinLiveSessionResponse;
import com.avandocmsg.messenger.api.live.dto.LiveIngressResponse;
import com.avandocmsg.messenger.api.live.dto.LiveSessionResponse;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.LiveSessionRepository;
import com.avandocmsg.messenger.common.dto.LiveSessionChangeEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LiveSessionService {
    private static final Logger log = LoggerFactory.getLogger(LiveSessionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOKEN_TTL_SEC = 3600;

    private final LiveSessionRepository liveSessionRepository;
    private final ChatRepository chatRepository;
    private final LiveKitTokenService liveKitTokenService;
    private final NatsOutboundPort natsOutbound;
    private final UserMessageSource messages;

    public LiveSessionService(LiveSessionRepository liveSessionRepository,
                              ChatRepository chatRepository,
                              LiveKitTokenService liveKitTokenService,
                              NatsOutboundPort natsOutbound,
                              UserMessageSource messages) {
        this.liveSessionRepository = liveSessionRepository;
        this.chatRepository = chatRepository;
        this.liveKitTokenService = liveKitTokenService;
        this.natsOutbound = natsOutbound;
        this.messages = messages;
    }

    public boolean liveStreamingConfigured() {
        return liveKitTokenService.enabled();
    }

    public Optional<LiveSessionResponse> create(UUID chatId, UUID userId, CreateLiveSessionRequest request) {
        if (!liveKitTokenService.enabled()) {
            log.warn("Live streaming not configured (LiveKit env missing)");
            return Optional.empty();
        }
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        var title = request != null && request.title() != null ? request.title().trim() : defaultTitle();
        var room = liveSessionRepository.newRoomName();
        var created = liveSessionRepository.insert(chatId, userId, title, room);
        created.ifPresent(session -> {
            liveSessionRepository.join(UUID.fromString(session.liveSessionId()), userId, "host");
            publishChange("created", session, userId);
            liveSessionRepository.findById(UUID.fromString(session.liveSessionId()))
                .ifPresent(s -> publishChange("updated", s, userId));
        });
        return created.flatMap(s -> liveSessionRepository.findById(UUID.fromString(s.liveSessionId())));
    }

    public List<LiveSessionResponse> listForChat(UUID chatId, UUID userId, boolean activeOnly) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return List.of();
        }
        return liveSessionRepository.listForChat(chatId, activeOnly);
    }

    public Optional<LiveSessionResponse> get(UUID sessionId, UUID userId) {
        var session = liveSessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        var chatId = UUID.fromString(session.get().chatId());
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        return session;
    }

    public Optional<JoinLiveSessionResponse> join(UUID sessionId, UUID userId) {
        var sessionOpt = liveSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || !"active".equals(sessionOpt.get().status())) {
            return Optional.empty();
        }
        var session = sessionOpt.get();
        var chatId = UUID.fromString(session.chatId());
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        if (!liveKitTokenService.enabled()) {
            return Optional.empty();
        }
        var existingRole = liveSessionRepository.viewerRole(sessionId, userId);
        if (existingRole.isEmpty()) {
            var active = liveSessionRepository.countActiveViewers(sessionId);
            if (active >= session.maxViewers()) {
                log.warn("Live session {} at viewer cap {}", sessionId, session.maxViewers());
                return Optional.empty();
            }
        }
        var role = existingRole.orElse("viewer");
        var creator = liveSessionRepository.findCreatorId(sessionId);
        if (creator.map(userId::equals).orElse(false)) {
            role = "host";
        }
        if (!liveSessionRepository.join(sessionId, userId, role)) {
            return Optional.empty();
        }
        var updated = liveSessionRepository.findById(sessionId).orElse(session);
        var canPublish = "host".equals(role) || "cohost".equals(role);
        var token = liveKitTokenService.createAccessToken(updated.roomName(), userId.toString(), canPublish, TOKEN_TTL_SEC);
        publishChange("updated", updated, userId);
        return Optional.of(new JoinLiveSessionResponse(
            updated.liveSessionId(),
            updated.roomName(),
            liveKitTokenService.livekitUrl(),
            token,
            role,
            updated.viewerCount(),
            updated.maxViewers()
        ));
    }

    public boolean leave(UUID sessionId, UUID userId) {
        if (!liveSessionRepository.leave(sessionId, userId)) {
            return false;
        }
        liveSessionRepository.findById(sessionId).ifPresent(s -> publishChange("updated", s, userId));
        return true;
    }

    public boolean end(UUID sessionId, UUID userId) {
        var session = liveSessionRepository.findById(sessionId);
        if (session.isEmpty() || !"active".equals(session.get().status())) {
            return false;
        }
        var chatId = UUID.fromString(session.get().chatId());
        var role = chatRepository.getMemberRole(chatId, userId);
        if (role == null) {
            return false;
        }
        var creator = liveSessionRepository.findCreatorId(sessionId);
        boolean creatorOk = creator.map(userId::equals).orElse(false);
        boolean moderator = "owner".equals(role) || "admin".equals(role);
        if (!creatorOk && !moderator) {
            return false;
        }
        if (!liveSessionRepository.endSession(sessionId)) {
            return false;
        }
        liveSessionRepository.findById(sessionId).ifPresent(s -> publishChange("ended", s, userId));
        return true;
    }

    public Optional<LiveSessionResponse> updateDvrPlaylist(UUID chatId, UUID sessionId, UUID userId, String playlistUrl) {
        var sessionOpt = requireActiveSessionInChat(chatId, sessionId, userId);
        if (sessionOpt.isEmpty() || !isHost(sessionId, userId)) {
            return Optional.empty();
        }
        if (playlistUrl == null || playlistUrl.isBlank()) {
            return Optional.empty();
        }
        if (!liveSessionRepository.updateDvrPlaylist(sessionId, playlistUrl.trim())) {
            return Optional.empty();
        }
        return liveSessionRepository.findById(sessionId).map(s -> {
            publishChange("updated", s, userId);
            return s;
        });
    }

    public Optional<LiveSessionResponse> recordModeration(UUID chatId, UUID sessionId, UUID userId,
                                                          String action, String reason) {
        if (action == null || action.isBlank()) {
            return Optional.empty();
        }
        var sessionOpt = requireActiveSessionInChat(chatId, sessionId, userId);
        if (sessionOpt.isEmpty() || !canModerate(chatId, sessionId, userId)) {
            return Optional.empty();
        }
        var normalized = action.trim().toLowerCase();
        var trimmedReason = reason != null ? reason.trim() : null;
        if (!liveSessionRepository.recordModerationEvent(sessionId, userId, normalized, trimmedReason)) {
            return Optional.empty();
        }
        resolveModerationState(normalized).ifPresent(state -> liveSessionRepository.setModerationState(sessionId, state));
        if ("stop".equals(normalized) || "stopped".equals(normalized)) {
            liveSessionRepository.endSession(sessionId);
        }
        return liveSessionRepository.findById(sessionId).map(s -> {
            publishChange("stop".equals(normalized) || "stopped".equals(normalized) ? "ended" : "updated", s, userId);
            return s;
        });
    }

    public Optional<LiveIngressResponse> ingressCredentials(UUID chatId, UUID sessionId, UUID userId) {
        if (!liveKitTokenService.enabled()) {
            return Optional.empty();
        }
        var sessionOpt = requireActiveSessionInChat(chatId, sessionId, userId);
        if (sessionOpt.isEmpty() || !isHost(sessionId, userId)) {
            return Optional.empty();
        }
        var rtmpUrl = liveKitTokenService.livekitIngressUrl();
        if (rtmpUrl.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LiveIngressResponse(rtmpUrl, sessionOpt.get().roomName()));
    }

    private Optional<LiveSessionResponse> requireActiveSessionInChat(UUID chatId, UUID sessionId, UUID userId) {
        var session = liveSessionRepository.findById(sessionId);
        if (session.isEmpty() || !chatId.toString().equals(session.get().chatId())) {
            return Optional.empty();
        }
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        if (!"active".equals(session.get().status())) {
            return Optional.empty();
        }
        return session;
    }

    private boolean isHost(UUID sessionId, UUID userId) {
        var creator = liveSessionRepository.findCreatorId(sessionId);
        if (creator.map(userId::equals).orElse(false)) {
            return true;
        }
        return liveSessionRepository.viewerRole(sessionId, userId)
            .map(role -> "host".equals(role) || "cohost".equals(role))
            .orElse(false);
    }

    private boolean canModerate(UUID chatId, UUID sessionId, UUID userId) {
        if (isHost(sessionId, userId)) {
            return true;
        }
        var role = chatRepository.getMemberRole(chatId, userId);
        return "owner".equals(role) || "admin".equals(role);
    }

    private Optional<String> resolveModerationState(String action) {
        return switch (action) {
            case "stop", "stopped" -> Optional.of("stopped");
            case "slow_mode" -> Optional.of("slow_mode");
            case "open", "reopen" -> Optional.of("open");
            case "ban" -> Optional.of("restricted");
            default -> Optional.empty();
        };
    }

    private String defaultTitle() {
        return messages.get("live.default_title");
    }

    private void publishChange(String change, LiveSessionResponse session, UUID actorId) {
        try {
            var event = new LiveSessionChangeEvent(
                change,
                session.liveSessionId(),
                session.chatId(),
                actorId.toString(),
                session.title(),
                session.status(),
                session.mode(),
                session.roomName(),
                session.provider(),
                session.viewerCount(),
                session.maxViewers()
            );
            natsOutbound.publish(NatsSubjects.LIVE_SESSION, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish live.session {}", session.liveSessionId(), e);
        }
    }
}
