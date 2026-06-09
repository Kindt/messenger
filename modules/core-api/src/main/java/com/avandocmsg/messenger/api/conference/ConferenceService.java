package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.conference.dto.ConferenceParticipantResponse;
import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.repository.ConferenceRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.common.dto.ConferenceChangeEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ConferenceService {
    private static final Logger log = LoggerFactory.getLogger(ConferenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConferenceRepository conferenceRepository;
    private final ChatRepository chatRepository;
    private final ChatService chatService;
    private final NatsOutboundPort natsOutbound;
    private final UserMessageSource messages;

    public ConferenceService(ConferenceRepository conferenceRepository, ChatRepository chatRepository,
                             ChatService chatService, NatsOutboundPort natsOutbound,
                             UserMessageSource messages) {
        this.conferenceRepository = conferenceRepository;
        this.chatRepository = chatRepository;
        this.chatService = chatService;
        this.natsOutbound = natsOutbound;
        this.messages = messages;
    }

    /** Встреча «как в Телемосте»: группа-контейнер + конференция + join_url для приглашений. */
    public Optional<ConferenceResponse> createStandalone(UUID userId, CreateConferenceRequest request) {
        var title = request != null && request.title() != null && !request.title().isBlank()
            ? request.title().trim()
            : defaultMeetingTitle();
        var chat = chatService.createGroup(title, userId, request != null ? request.memberIds() : null);
        if (chat == null) {
            log.warn("Failed to create meeting group for user {}", userId);
            return Optional.empty();
        }
        return create(UUID.fromString(chat.id()), userId, new CreateConferenceRequest(title, null));
    }

    public Optional<ConferenceResponse> getByRoomSlug(UUID userId, String roomSlug) {
        if (roomSlug == null || roomSlug.isBlank()) {
            return Optional.empty();
        }
        var conf = conferenceRepository.findActiveByRoomSlug(roomSlug.trim());
        if (conf.isEmpty()) {
            return Optional.empty();
        }
        var chatId = UUID.fromString(conf.get().chatId());
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        return conf;
    }

    private String defaultMeetingTitle() {
        return messages.get("conference.default_title");
    }

    public Optional<ConferenceResponse> create(UUID chatId, UUID userId, CreateConferenceRequest request) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            log.warn("User {} not in chat {}", userId, chatId);
            return Optional.empty();
        }
        var title = request != null && request.title() != null ? request.title() : "";
        var slug = conferenceRepository.newRoomSlug();
        var created = conferenceRepository.insert(chatId, userId, title, slug);
        created.ifPresent(conf -> publishConferenceChange("created", conf, userId));
        return created;
    }

    public List<ConferenceResponse> listActiveForUser(UUID userId) {
        return conferenceRepository.listActiveForUser(userId);
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

    public Optional<List<ConferenceParticipantResponse>> listParticipants(UUID conferenceId, UUID userId) {
        if (get(conferenceId, userId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(conferenceRepository.listActiveParticipants(conferenceId));
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
        if (!conferenceRepository.join(conferenceId, userId)) {
            return false;
        }
        conferenceRepository.findById(conferenceId)
            .ifPresent(c -> publishConferenceChange("updated", c, userId));
        return true;
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
        if (!conferenceRepository.endConference(conferenceId)) {
            return false;
        }
        conferenceRepository.findById(conferenceId).ifPresent(ended -> publishConferenceChange("ended", ended, userId));
        return true;
    }

    private void publishConferenceChange(String change, ConferenceResponse conf, UUID actorId) {
        try {
            var event = new ConferenceChangeEvent(
                change,
                conf.conferenceId(),
                conf.chatId(),
                actorId.toString(),
                conf.title(),
                conf.status(),
                conf.roomSlug(),
                conf.joinUrl(),
                conf.provider(),
                conf.participantCount());
            natsOutbound.publish(NatsSubjects.MSG_CONFERENCE, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish {} for conference {}", NatsSubjects.MSG_CONFERENCE, conf.conferenceId(), e);
        }
    }
}
