package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.conference.dto.ConferenceParticipantResponse;
import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Video conference rooms. */
public interface ConferencePort {
    String newRoomSlug();

    Optional<ConferenceResponse> insert(UUID chatId, UUID createdBy, String title, String roomSlug);

    List<ConferenceParticipantResponse> listActiveParticipants(UUID conferenceId);

    int countActiveParticipants(UUID conferenceId);

    Optional<ConferenceResponse> findActiveByRoomSlug(String roomSlug);

    Optional<ConferenceResponse> findById(UUID conferenceId);

    List<ConferenceResponse> listActiveForUser(UUID userId);

    List<ConferenceResponse> listForChat(UUID chatId, boolean activeOnly);

    boolean join(UUID conferenceId, UUID userId);

    boolean leave(UUID conferenceId, UUID userId);

    Optional<UUID> findCreatorId(UUID conferenceId);

    boolean endConference(UUID conferenceId);
}
