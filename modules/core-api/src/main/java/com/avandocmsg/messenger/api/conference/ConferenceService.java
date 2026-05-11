package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ConferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ConferenceService {
    private static final Logger log = LoggerFactory.getLogger(ConferenceService.class);

    private final ConferenceRepository conferenceRepository;
    private final ChatRepository chatRepository;

    public ConferenceService(ConferenceRepository conferenceRepository, ChatRepository chatRepository) {
        this.conferenceRepository = conferenceRepository;
        this.chatRepository = chatRepository;
    }

    public Optional<ConferenceResponse> create(UUID chatId, UUID userId, CreateConferenceRequest request) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            log.warn("User {} not in chat {}", userId, chatId);
            return Optional.empty();
        }
        var title = request != null && request.title() != null ? request.title() : "";
        var slug = conferenceRepository.newRoomSlug();
        return conferenceRepository.insert(chatId, userId, title, slug);
    }

    public List<ConferenceResponse> listForChat(UUID chatId, UUID userId, boolean activeOnly) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return List.of();
        }
        return conferenceRepository.listForChat(chatId, activeOnly);
    }

    public Optional<ConferenceResponse> get(UUID conferenceId, UUID userId) {
        var conf = conferenceRepository.findById(conferenceId);
        if (conf.isEmpty()) {
            return Optional.empty();
        }
        var chatId = UUID.fromString(conf.get().chatId());
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        return conf;
    }

    public boolean join(UUID conferenceId, UUID userId) {
        var conf = conferenceRepository.findById(conferenceId);
        if (conf.isEmpty() || !"active".equals(conf.get().status())) {
            return false;
        }
        var chatId = UUID.fromString(conf.get().chatId());
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return false;
        }
        return conferenceRepository.join(conferenceId, userId);
    }

    public boolean leave(UUID conferenceId, UUID userId) {
        return conferenceRepository.leave(conferenceId, userId);
    }

    public boolean end(UUID conferenceId, UUID userId) {
        var conf = conferenceRepository.findById(conferenceId);
        if (conf.isEmpty() || !"active".equals(conf.get().status())) {
            return false;
        }
        var chatId = UUID.fromString(conf.get().chatId());
        var role = chatRepository.getMemberRole(chatId, userId);
        if (role == null) {
            return false;
        }
        var creator = conferenceRepository.findCreatorId(conferenceId);
        boolean creatorOk = creator.map(userId::equals).orElse(false);
        boolean moderator = "owner".equals(role) || "admin".equals(role);
        if (!creatorOk && !moderator) {
            log.warn("User {} cannot end conference {}", userId, conferenceId);
            return false;
        }
        return conferenceRepository.endConference(conferenceId);
    }
}
