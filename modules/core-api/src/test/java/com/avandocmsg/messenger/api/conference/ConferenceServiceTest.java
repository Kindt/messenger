package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ConferenceRepository;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConferenceServiceTest {

    private final StubConferenceRepository conferenceRepo = new StubConferenceRepository();
    private final StubChatRepository chatRepo = new StubChatRepository();
    private final RecordingChatService chatService = new RecordingChatService(chatRepo);
    private final ConferenceService service = new ConferenceService(
        conferenceRepo, chatRepo, chatService, NatsOutboundPort.noop(), I18nTestFixtures.messagesEn());

    private final UUID userId = UUID.randomUUID();
    private final UUID chatId = UUID.randomUUID();
    private final UUID conferenceId = UUID.randomUUID();

    @Test
    void createStandalone_usesDefaultTitleWhenBlank() {
        chatService.nextGroup = new ChatResponse(chatId.toString(), "Meeting", "group", null, 1, false, false, null, null);
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userId), "owner");
        conferenceRepo.nextInsert = sampleConference();

        var result = service.createStandalone(userId, new CreateConferenceRequest(null, null));

        assertTrue(result.isPresent());
        assertEquals("Meeting", chatService.lastGroupTitle);
    }

    @Test
    void createStandalone_returnsEmptyWhenGroupCreationFails() {
        chatService.nextGroup = null;

        assertTrue(service.createStandalone(userId, new CreateConferenceRequest("Demo", null)).isEmpty());
    }

    @Test
    void getByRoomSlug_deniesNonMember() {
        conferenceRepo.bySlug = sampleConference();

        assertTrue(service.getByRoomSlug(userId, "room-abc").isEmpty());
    }

    @Test
    void getByRoomSlug_returnsConferenceForMember() {
        conferenceRepo.bySlug = sampleConference();
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userId), "member");

        var result = service.getByRoomSlug(userId, "room-abc");
        assertTrue(result.isPresent());
        assertEquals(conferenceId.toString(), result.get().conferenceId());
    }

    @Test
    void getByRoomSlug_blankSlugReturnsEmpty() {
        assertTrue(service.getByRoomSlug(userId, "  ").isEmpty());
    }

    private ConferenceResponse sampleConference() {
        return new ConferenceResponse(
            conferenceId.toString(),
            chatId.toString(),
            "Demo",
            "active",
            "room-abc",
            "https://meet.example/room-abc",
            "jitsi",
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            0);
    }

    static final class StubConferenceRepository extends ConferenceRepository {
        ConferenceResponse nextInsert;
        ConferenceResponse bySlug;

        StubConferenceRepository() {
            super(null, null, UuidGenerator.standard());
        }

        @Override
        public String newRoomSlug() {
            return "room-abc";
        }

        @Override
        public Optional<ConferenceResponse> insert(UUID chatId, UUID creatorId, String title, String roomSlug) {
            return Optional.ofNullable(nextInsert);
        }

        @Override
        public Optional<ConferenceResponse> findActiveByRoomSlug(String roomSlug) {
            return Optional.ofNullable(bySlug);
        }
    }

    static final class StubChatRepository extends ChatRepository {
        final java.util.Map<RoleKey, String> roles = new java.util.HashMap<>();

        record RoleKey(UUID chatId, UUID userId) {}

        StubChatRepository() {
            super(null, Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return roles.get(new RoleKey(chatId, userId));
        }
    }

    static final class RecordingChatService extends ChatService {
        ChatResponse nextGroup;
        String lastGroupTitle;

        RecordingChatService(ChatRepository chatRepository) {
            super(chatRepository, null, null, null, null, NatsOutboundPort.noop(), Clock.systemUTC(),
                UuidGenerator.standard(), NoOpReadCacheAdapter.INSTANCE, new AppConfig());
        }

        @Override
        public ChatResponse createGroup(String title, UUID ownerId, List<String> memberIds) {
            lastGroupTitle = title;
            return nextGroup;
        }
    }
}
