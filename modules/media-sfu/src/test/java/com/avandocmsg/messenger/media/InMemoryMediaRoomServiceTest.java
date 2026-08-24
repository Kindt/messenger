package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryMediaRoomServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final UUID CHAT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void createsJoinsAndEndsProviderNeutralRoom() {
        var rooms = new InMemoryMediaRoomService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(2),
            "embedded-1"
        );

        var created = rooms.create(CHAT_ID, OWNER_ID, CallKind.GROUP);
        assertEquals(CallStatus.ACTIVE, created.status());
        assertEquals("embedded-1", created.nodeId());

        var participant = rooms.join(created.sessionId(), OWNER_ID, ParticipantRole.HOST);
        assertEquals(created.sessionId(), participant.sessionId());
        assertEquals(ParticipantState.CONNECTED, participant.state());

        rooms.end(created.sessionId(), OWNER_ID);
        assertEquals(CallStatus.ENDED, rooms.requireSession(created.sessionId()).status());
        assertThrows(
            IllegalStateException.class,
            () -> rooms.join(created.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER)
        );
    }

    @Test
    void reusesTheSingleActiveRoomForAChat() {
        var rooms = new InMemoryMediaRoomService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(2),
            "embedded-1"
        );

        var first = rooms.createOrGet(CHAT_ID, OWNER_ID, CallKind.GROUP);
        var second = rooms.createOrGet(CHAT_ID, UUID.randomUUID(), CallKind.DIRECT);

        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(CallKind.GROUP, second.kind());
    }

    @Test
    void letsAConnectedParticipantLeaveWithoutEndingTheRoom() {
        var rooms = new InMemoryMediaRoomService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(2),
            "embedded-1"
        );
        var session = rooms.create(CHAT_ID, OWNER_ID, CallKind.GROUP);
        var participant = rooms.join(session.sessionId(), OWNER_ID, ParticipantRole.HOST);

        rooms.leave(session.sessionId(), participant.participantId(), OWNER_ID);

        assertEquals(
            ParticipantState.LEFT,
            rooms.requireParticipant(session.sessionId(), participant.participantId()).state()
        );
        assertEquals(CallStatus.ACTIVE, rooms.requireSession(session.sessionId()).status());
    }

    @Test
    void routesSignalsBetweenParticipantAndMediaNode() {
        var rooms = new InMemoryMediaRoomService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(2),
            "embedded-1"
        );
        var session = rooms.create(CHAT_ID, OWNER_ID, CallKind.DIRECT);
        var participant = rooms.join(session.sessionId(), OWNER_ID, ParticipantRole.HOST);

        rooms.acceptParticipantSignal(
            session.sessionId(),
            participant.participantId(),
            SignalType.OFFER,
            "v=0\r\n",
            null
        );
        var inbound = rooms.drainNodeSignals(session.sessionId(), 10);
        assertEquals(1, inbound.size());
        assertEquals(SignalType.OFFER, inbound.getFirst().type());

        rooms.publishNodeSignal(
            session.sessionId(),
            participant.participantId(),
            SignalType.ANSWER,
            "v=0\r\nanswer",
            null
        );
        var outbound = rooms.drainParticipantSignals(session.sessionId(), participant.participantId(), 10);
        assertEquals(1, outbound.size());
        assertEquals(SignalType.ANSWER, outbound.getFirst().type());
    }

    @Test
    void appliesLastNToConnectedPublishers() {
        var rooms = new InMemoryMediaRoomService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(2),
            "embedded-1"
        );
        var session = rooms.create(CHAT_ID, OWNER_ID, CallKind.GROUP);
        var viewer = rooms.join(session.sessionId(), OWNER_ID, ParticipantRole.HOST);
        var first = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        var second = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        var third = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);

        var selected = rooms.selectSubscriptions(
            session.sessionId(),
            viewer.participantId(),
            List.of(third.participantId(), second.participantId(), first.participantId(), viewer.participantId()),
            2
        );

        assertEquals(List.of(third.participantId(), second.participantId()), selected);
    }

    @Test
    void removesIdleRoomsAndTheirState() {
        var clock = new MutableClock(NOW);
        var rooms = new InMemoryMediaRoomService(clock, Duration.ofMinutes(2), "embedded-1");
        var session = rooms.create(CHAT_ID, OWNER_ID, CallKind.MEETING);

        clock.advance(Duration.ofMinutes(3));

        assertEquals(1, rooms.removeIdleRooms());
        assertThrows(IllegalArgumentException.class, () -> rooms.requireSession(session.sessionId()));
    }

    @Test
    void mediaActivityKeepsAnActiveRoomAlive() {
        var clock = new MutableClock(NOW);
        var rooms = new InMemoryMediaRoomService(clock, Duration.ofMinutes(2), "embedded-1");
        var session = rooms.create(CHAT_ID, OWNER_ID, CallKind.DIRECT);

        clock.advance(Duration.ofSeconds(90));
        rooms.touch(session.sessionId());
        clock.advance(Duration.ofSeconds(90));

        assertEquals(0, rooms.removeIdleRooms());
        assertEquals(CallStatus.ACTIVE, rooms.requireSession(session.sessionId()).status());

        clock.advance(Duration.ofSeconds(31));
        assertEquals(1, rooms.removeIdleRooms());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
