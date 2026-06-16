package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.live.dto.CreateLiveSessionRequest;
import com.avandocmsg.messenger.api.live.dto.JoinLiveSessionResponse;
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
