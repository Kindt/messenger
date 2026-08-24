package com.avandocmsg.messenger.api.calls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.avandocmsg.messenger.api.calls.dto.CallSignalRequest;
import com.avandocmsg.messenger.common.dto.CallSessionEvent;
import com.avandocmsg.messenger.media.InMemoryMediaRoomService;
import com.avandocmsg.messenger.media.MediaSignalingProcessor;
import com.avandocmsg.messenger.media.SignalType;
import com.avandocmsg.messenger.testsupport.EmptyChatPersistencePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnifiedCallServiceTest {

    private static final UUID CHAT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID USER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID OTHER = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    private StubChatRepository chats;
    private InMemoryMediaRoomService rooms;
    private UnifiedCallService service;

    @BeforeEach
    void setUp() {
        chats = new StubChatRepository();
        rooms = new InMemoryMediaRoomService(
            Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofMinutes(2),
            "embedded-1"
        );
        service = new UnifiedCallService(chats, rooms);
    }

    @Test
    void createsProviderNeutralSessionForChatMember() {
        chats.role = "owner";

        var result = service.create(CHAT, USER, "group");

        assertTrue(result.isPresent());
        assertEquals("group", result.orElseThrow().kind());
        assertEquals("active", result.orElseThrow().status());
        assertEquals(CHAT.toString(), result.orElseThrow().chatId());
        assertEquals("embedded-1", result.orElseThrow().mediaNodeId());
    }

    @Test
    void derivesDirectKindFromP2pChatInsteadOfTrustingClientKind() {
        chats.role = "owner";
        chats.type = "p2p";

        var result = service.create(CHAT, USER, "group");

        assertEquals("direct", result.orElseThrow().kind());
    }

    @Test
    void publishesOneChatScopedInvitationWhenSessionIsFirstCreated() {
        chats.role = "owner";
        var events = new ArrayList<CallSessionEvent>();
        service = new UnifiedCallService(chats, rooms, MediaSignalingProcessor.NOOP, events::add);

        var owner = service.create(CHAT, USER, "group", "video").orElseThrow();
        service.create(CHAT, OTHER, "group", "video").orElseThrow();

        assertEquals(1, events.size());
        var event = events.getFirst();
        assertEquals(CallSessionEvent.INVITED, event.type());
        assertEquals(CHAT.toString(), event.chatId());
        assertEquals(owner.sessionId(), event.sessionId());
        assertEquals(USER.toString(), event.callerUserId());
        assertEquals("video", event.mediaIntent());
        assertEquals("2026-08-24T00:00:00Z", event.createdAt());
    }

    @Test
    void publishesParticipantLocalInvitationDeclineWithoutEndingSession() {
        chats.role = "member";
        var events = new ArrayList<CallSessionEvent>();
        service = new UnifiedCallService(chats, rooms, MediaSignalingProcessor.NOOP, events::add);
        var owner = service.create(CHAT, USER, "group", "audio").orElseThrow();
        events.clear();

        assertTrue(service.decline(CHAT, UUID.fromString(owner.sessionId()), OTHER));

        assertEquals(1, events.size());
        assertEquals(CallSessionEvent.INVITATION_DECLINED, events.getFirst().type());
        assertEquals(OTHER.toString(), events.getFirst().declinedByUserId());
        assertEquals("ACTIVE", rooms.requireSession(UUID.fromString(owner.sessionId())).status().name());
    }

    @Test
    void rejectsNonMember() {
        chats.role = null;

        assertFalse(service.create(CHAT, USER, "direct").isPresent());
    }

    @Test
    void acceptsOnlyOwnedParticipantSignal() {
        chats.role = "owner";
        var joined = service.create(CHAT, USER, "direct").orElseThrow();

        assertTrue(service.submitSignal(
            CHAT,
            UUID.fromString(joined.sessionId()),
            USER,
            UUID.fromString(joined.participantId()),
            new CallSignalRequest("offer", "v=0\r\n", null)
        ));
        assertEquals(
            SignalType.OFFER,
            rooms.drainNodeSignals(UUID.fromString(joined.sessionId()), 10).getFirst().type()
        );
        assertFalse(service.submitSignal(
            CHAT,
            UUID.fromString(joined.sessionId()),
            USER,
            UUID.randomUUID(),
            new CallSignalRequest("offer", "v=0\r\n", null)
        ));
    }

    @Test
    void joinsExistingSessionAndOnlyOwnerCanEndIt() {
        chats.role = "member";
        var created = service.create(CHAT, USER, "meeting").orElseThrow();
        var sessionId = UUID.fromString(created.sessionId());

        var joined = service.join(CHAT, sessionId, OTHER);

        assertTrue(joined.isPresent());
        assertEquals("member", joined.orElseThrow().role());
        assertFalse(service.end(CHAT, sessionId, OTHER));
        assertTrue(service.end(CHAT, sessionId, USER));
        assertEquals("ENDED", rooms.requireSession(sessionId).status().name());
    }

    @Test
    void createJoinsTheExistingChatCallAndParticipantCanLeave() {
        chats.role = "member";
        var owner = service.create(CHAT, USER, "group").orElseThrow();

        var member = service.create(CHAT, OTHER, "direct").orElseThrow();

        assertEquals(owner.sessionId(), member.sessionId());
        assertEquals("member", member.role());
        assertTrue(service.leave(
            CHAT,
            UUID.fromString(member.sessionId()),
            OTHER,
            UUID.fromString(member.participantId())
        ));
        assertEquals(
            "LEFT",
            rooms.requireParticipant(
                UUID.fromString(member.sessionId()),
                UUID.fromString(member.participantId())
            ).state().name()
        );
    }

    @Test
    void leavingParticipantIsPublishedToRemainingParticipants() {
        chats.role = "member";
        var owner = service.create(CHAT, USER, "group").orElseThrow();
        var member = service.create(CHAT, OTHER, "group").orElseThrow();
        var sessionId = UUID.fromString(owner.sessionId());

        assertTrue(service.leave(
            CHAT,
            sessionId,
            OTHER,
            UUID.fromString(member.participantId())
        ));

        var signals = service.pollSignals(
            CHAT,
            sessionId,
            USER,
            UUID.fromString(owner.participantId()),
            10
        ).orElseThrow();
        assertEquals(1, signals.size());
        assertEquals("participant_left", signals.getFirst().type());
        assertEquals(member.participantId(), signals.getFirst().participantId());
    }

    @Test
    void endingSessionIsPublishedBeforeParticipantsAreDisconnected() {
        chats.role = "member";
        var owner = service.create(CHAT, USER, "group").orElseThrow();
        var member = service.create(CHAT, OTHER, "group").orElseThrow();
        var sessionId = UUID.fromString(owner.sessionId());

        assertTrue(service.end(CHAT, sessionId, USER));

        var signals = service.pollSignals(
            CHAT,
            sessionId,
            OTHER,
            UUID.fromString(member.participantId()),
            10
        ).orElseThrow();
        assertEquals(1, signals.size());
        assertEquals("session_ended", signals.getFirst().type());
    }

    private static final class StubChatRepository extends EmptyChatPersistencePort {
        private String role;
        private String type;

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return role;
        }

        @Override
        public java.util.Optional<String> getChatType(UUID chatId) {
            return java.util.Optional.ofNullable(type);
        }
    }
}
