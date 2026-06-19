package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.live.dto.LiveSessionResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcLiveSessionJdbcRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for live session JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcLiveSessionJdbcRepository}.
 */
public class LiveSessionRepository {
    private final JdbcLiveSessionJdbcRepository jdbc;

    public LiveSessionRepository(DataSource dataSource, AppConfig appConfig, UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcLiveSessionJdbcRepository(dataSource, appConfig, uuidGenerator);
    }

    public JdbcLiveSessionJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public String newRoomName() {
        return jdbc.newRoomName();
    }

    public Optional<LiveSessionResponse> insert(UUID chatId, UUID createdBy, String title, String roomName) {
        return jdbc.insert(chatId, createdBy, title, roomName);
    }

    public Optional<LiveSessionResponse> findById(UUID sessionId) {
        return jdbc.findById(sessionId);
    }

    public List<LiveSessionResponse> listForChat(UUID chatId, boolean activeOnly) {
        return jdbc.listForChat(chatId, activeOnly);
    }

    public int countActiveViewers(UUID sessionId) {
        return jdbc.countActiveViewers(sessionId);
    }

    public Optional<String> viewerRole(UUID sessionId, UUID userId) {
        return jdbc.viewerRole(sessionId, userId);
    }

    public boolean join(UUID sessionId, UUID userId, String role) {
        return jdbc.join(sessionId, userId, role);
    }

    public boolean leave(UUID sessionId, UUID userId) {
        return jdbc.leave(sessionId, userId);
    }

    public Optional<UUID> findCreatorId(UUID sessionId) {
        return jdbc.findCreatorId(sessionId);
    }

    public boolean endSession(UUID sessionId) {
        return jdbc.endSession(sessionId);
    }

    public boolean updateDvrPlaylist(UUID sessionId, String url) {
        return jdbc.updateDvrPlaylist(sessionId, url);
    }

    public boolean recordModerationEvent(UUID sessionId, UUID actorUserId, String action, String reason) {
        return jdbc.recordModerationEvent(sessionId, actorUserId, action, reason);
    }

    public boolean setModerationState(UUID sessionId, String state) {
        return jdbc.setModerationState(sessionId, state);
    }
}
