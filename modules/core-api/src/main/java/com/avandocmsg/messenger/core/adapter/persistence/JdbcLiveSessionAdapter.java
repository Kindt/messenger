package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.LiveSessionRepository;
import com.avandocmsg.messenger.core.port.LiveSessionPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcLiveSessionAdapter implements LiveSessionPort {
    private final LiveSessionRepository delegate;

    public JdbcLiveSessionAdapter(LiveSessionRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcLiveSessionAdapter(DataSource dataSource, AppConfig appConfig,
                                  com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.delegate = new LiveSessionRepository(dataSource, appConfig, uuidGenerator);
    }

    @Override
    public String newRoomName() {
        return delegate.newRoomName();
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.live.dto.LiveSessionResponse> insert(
        UUID chatId, UUID createdBy, String title, String roomName) {
        return delegate.insert(chatId, createdBy, title, roomName);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.live.dto.LiveSessionResponse> findById(UUID sessionId) {
        return delegate.findById(sessionId);
    }

    @Override
    public List<com.avandocmsg.messenger.api.live.dto.LiveSessionResponse> listForChat(UUID chatId, boolean activeOnly) {
        return delegate.listForChat(chatId, activeOnly);
    }

    @Override
    public int countActiveViewers(UUID sessionId) {
        return delegate.countActiveViewers(sessionId);
    }

    @Override
    public Optional<String> viewerRole(UUID sessionId, UUID userId) {
        return delegate.viewerRole(sessionId, userId);
    }

    @Override
    public boolean join(UUID sessionId, UUID userId, String role) {
        return delegate.join(sessionId, userId, role);
    }

    @Override
    public boolean leave(UUID sessionId, UUID userId) {
        return delegate.leave(sessionId, userId);
    }

    @Override
    public Optional<UUID> findCreatorId(UUID sessionId) {
        return delegate.findCreatorId(sessionId);
    }

    @Override
    public boolean endSession(UUID sessionId) {
        return delegate.endSession(sessionId);
    }

    @Override
    public boolean updateDvrPlaylist(UUID sessionId, String url) {
        return delegate.updateDvrPlaylist(sessionId, url);
    }

    @Override
    public boolean recordModerationEvent(UUID sessionId, UUID actorUserId, String action, String reason) {
        return delegate.recordModerationEvent(sessionId, actorUserId, action, reason);
    }

    @Override
    public boolean setModerationState(UUID sessionId, String state) {
        return delegate.setModerationState(sessionId, state);
    }
}
