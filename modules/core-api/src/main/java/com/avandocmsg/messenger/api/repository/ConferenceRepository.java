package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.conference.dto.ConferenceParticipantResponse;
import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcConferenceAdapter;
import com.avandocmsg.messenger.core.port.ConferencePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for conference JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcConferenceAdapter}.
 */
public class ConferenceRepository {
    private final ConferencePort port;

    public ConferenceRepository(DataSource dataSource, AppConfig appConfig, UuidGenerator uuidGenerator) {
        this.port = new JdbcConferenceAdapter(dataSource, appConfig, uuidGenerator);
    }

    ConferenceRepository(ConferencePort port) {
        this.port = port;
    }

    public String newRoomSlug() {
        return port.newRoomSlug();
    }

    public Optional<ConferenceResponse> insert(UUID chatId, UUID createdBy, String title, String roomSlug) {
        return port.insert(chatId, createdBy, title, roomSlug);
    }

    public List<ConferenceParticipantResponse> listActiveParticipants(UUID conferenceId) {
        return port.listActiveParticipants(conferenceId);
    }

    public int countActiveParticipants(UUID conferenceId) {
        return port.countActiveParticipants(conferenceId);
    }

    public Optional<ConferenceResponse> findActiveByRoomSlug(String roomSlug) {
        return port.findActiveByRoomSlug(roomSlug);
    }

    public Optional<ConferenceResponse> findById(UUID conferenceId) {
        return port.findById(conferenceId);
    }

    public List<ConferenceResponse> listActiveForUser(UUID userId) {
        return port.listActiveForUser(userId);
    }

    public List<ConferenceResponse> listForChat(UUID chatId, boolean activeOnly) {
        return port.listForChat(chatId, activeOnly);
    }

    public boolean join(UUID conferenceId, UUID userId) {
        return port.join(conferenceId, userId);
    }

    public boolean leave(UUID conferenceId, UUID userId) {
        return port.leave(conferenceId, userId);
    }

    public Optional<UUID> findCreatorId(UUID conferenceId) {
        return port.findCreatorId(conferenceId);
    }

    public boolean endConference(UUID conferenceId) {
        return port.endConference(conferenceId);
    }
}
