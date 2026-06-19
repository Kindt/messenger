package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.ConferenceRepository;
import com.avandocmsg.messenger.core.port.ConferencePort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcConferenceAdapter implements ConferencePort {
    private final ConferenceRepository delegate;

    public JdbcConferenceAdapter(ConferenceRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcConferenceAdapter(DataSource dataSource, AppConfig appConfig,
                                 com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.delegate = new ConferenceRepository(dataSource, appConfig, uuidGenerator);
    }

    @Override
    public String newRoomSlug() {
        return delegate.newRoomSlug();
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.conference.dto.ConferenceResponse> insert(
        UUID chatId, UUID createdBy, String title, String roomSlug) {
        return delegate.insert(chatId, createdBy, title, roomSlug);
    }

    @Override
    public List<com.avandocmsg.messenger.api.conference.dto.ConferenceParticipantResponse> listActiveParticipants(
        UUID conferenceId) {
        return delegate.listActiveParticipants(conferenceId);
    }

    @Override
    public int countActiveParticipants(UUID conferenceId) {
        return delegate.countActiveParticipants(conferenceId);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.conference.dto.ConferenceResponse> findActiveByRoomSlug(
        String roomSlug) {
        return delegate.findActiveByRoomSlug(roomSlug);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.conference.dto.ConferenceResponse> findById(UUID conferenceId) {
        return delegate.findById(conferenceId);
    }

    @Override
    public List<com.avandocmsg.messenger.api.conference.dto.ConferenceResponse> listActiveForUser(UUID userId) {
        return delegate.listActiveForUser(userId);
    }

    @Override
    public List<com.avandocmsg.messenger.api.conference.dto.ConferenceResponse> listForChat(
        UUID chatId, boolean activeOnly) {
        return delegate.listForChat(chatId, activeOnly);
    }

    @Override
    public boolean join(UUID conferenceId, UUID userId) {
        return delegate.join(conferenceId, userId);
    }

    @Override
    public boolean leave(UUID conferenceId, UUID userId) {
        return delegate.leave(conferenceId, userId);
    }

    @Override
    public Optional<UUID> findCreatorId(UUID conferenceId) {
        return delegate.findCreatorId(conferenceId);
    }

    @Override
    public boolean endConference(UUID conferenceId) {
        return delegate.endConference(conferenceId);
    }
}
