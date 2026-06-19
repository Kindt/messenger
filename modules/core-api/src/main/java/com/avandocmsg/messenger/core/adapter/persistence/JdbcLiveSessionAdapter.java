package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.LiveSessionPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcLiveSessionAdapter implements LiveSessionPort {
    private final JdbcLiveSessionJdbcRepository jdbc;

    public JdbcLiveSessionAdapter(JdbcLiveSessionJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcLiveSessionAdapter(DataSource dataSource, AppConfig appConfig,
                                  com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcLiveSessionJdbcRepository(dataSource, appConfig, uuidGenerator);
    }

    @Override
    public String newRoomName() {
        return jdbc.newRoomName();
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.live.dto.LiveSessionResponse> insert(
        UUID chatId, UUID createdBy, String title, String roomName) {
        return jdbc.insert(chatId, createdBy, title, roomName);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.live.dto.LiveSessionResponse> findById(UUID sessionId) {
        return jdbc.findById(sessionId);
    }

    @Override
    public List<com.avandocmsg.messenger.api.live.dto.LiveSessionResponse> listForChat(UUID chatId, boolean activeOnly) {
        return jdbc.listForChat(chatId, activeOnly);
    }

    @Override
    public int countActiveViewers(UUID sessionId) {
        return jdbc.countActiveViewers(sessionId);
    }

    @Override
    public Optional<String> viewerRole(UUID sessionId, UUID userId) {
        return jdbc.viewerRole(sessionId, userId);
    }

    @Override
    public boolean join(UUID sessionId, UUID userId, String role) {
        return jdbc.join(sessionId, userId, role);
    }

    @Override
    public boolean leave(UUID sessionId, UUID userId) {
        return jdbc.leave(sessionId, userId);
    }

    @Override
    public Optional<UUID> findCreatorId(UUID sessionId) {
        return jdbc.findCreatorId(sessionId);
    }

    @Override
    public boolean endSession(UUID sessionId) {
        return jdbc.endSession(sessionId);
    }

    @Override
    public boolean updateDvrPlaylist(UUID sessionId, String url) {
        return jdbc.updateDvrPlaylist(sessionId, url);
    }

    @Override
    public boolean recordModerationEvent(UUID sessionId, UUID actorUserId, String action, String reason) {
        return jdbc.recordModerationEvent(sessionId, actorUserId, action, reason);
    }

    @Override
    public boolean setModerationState(UUID sessionId, String state) {
        return jdbc.setModerationState(sessionId, state);
    }
}
