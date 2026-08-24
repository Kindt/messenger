package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectAudioBindingTest {

    @Test
    void rejectsThirdParticipantAndBindsOneRemote() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var ownerId = UUID.randomUUID();
        var session = rooms.create(UUID.randomUUID(), ownerId, CallKind.DIRECT);
        var first = rooms.join(session.sessionId(), ownerId, ParticipantRole.HOST);
        var second = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        var third = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        submitOffer(rooms, session.sessionId(), first.participantId(), "first");
        submitOffer(rooms, session.sessionId(), second.participantId(), "second");
        submitOffer(rooms, session.sessionId(), third.participantId(), "third");

        var identity = DtlsIdentity.generate(Clock.systemUTC(), new SecureRandom());
        var node = new EmbeddedWebRtcMediaNode(
            rooms,
            InetAddress.getLoopbackAddress(),
            InetAddress.getLoopbackAddress(),
            0,
            0,
            identity
        );
        try (node) {
            node.processPending(session.sessionId());

            assertEquals(SignalType.ANSWER, drainOne(rooms, session.sessionId(), first.participantId()).type());
            assertEquals(SignalType.ANSWER, drainOne(rooms, session.sessionId(), second.participantId()).type());
            var rejection = drainOne(rooms, session.sessionId(), third.participantId());
            assertEquals(SignalType.ERROR, rejection.type());
            assertEquals("DIRECT_CALL_FULL", rejection.errorCode());
            assertEquals(2, node.activeTransportCount());
            assertEquals(
                Optional.of(second.participantId()),
                node.directAudioRemoteParticipant(session.sessionId(), first.participantId())
            );
            assertEquals(
                Optional.of(first.participantId()),
                node.directAudioRemoteParticipant(session.sessionId(), second.participantId())
            );
            assertEquals(
                Optional.empty(),
                node.directAudioRemoteParticipant(session.sessionId(), third.participantId())
            );
            assertEquals(2, node.directAudioBindingCount(session.sessionId()));
            assertEquals(
                ParticipantState.LEFT,
                rooms.requireParticipant(session.sessionId(), third.participantId()).state()
            );
        }

        assertEquals(0, node.activeTransportCount());
        assertEquals(0, node.activeDirectAudioSessionCount());
        rooms.end(session.sessionId(), ownerId);
        assertEquals(1, rooms.removeIdleRooms());
        assertFalse(rooms.containsSession(session.sessionId()));
    }

    @Test
    void forwardsPcmuPtZeroBidirectionallyWithoutRewrite() {
        var bindings = new DirectAudioBindings();
        var sessionId = UUID.randomUUID();
        var firstId = UUID.randomUUID();
        var secondId = UUID.randomUUID();
        assertEquals(Optional.empty(), bindings.tryBind(sessionId, firstId));
        assertEquals(Optional.empty(), bindings.tryBind(sessionId, secondId));
        var firstPacket = pcmuPacket(7, 0x11, 0x22, 0x33);
        var secondPacket = pcmuPacket(8, 0x44, 0x55, 0x66);

        var firstToSecond = bindings.route(sessionId, firstId, firstPacket);
        var secondToFirst = bindings.route(sessionId, secondId, secondPacket);

        assertEquals(1, firstToSecond.size());
        assertEquals(secondId, firstToSecond.getFirst().participantId());
        assertArrayEquals(firstPacket.wireBytes(), firstToSecond.getFirst().packet().wireBytes());
        assertEquals(1, secondToFirst.size());
        assertEquals(firstId, secondToFirst.getFirst().participantId());
        assertArrayEquals(secondPacket.wireBytes(), secondToFirst.getFirst().packet().wireBytes());
        assertEquals(0, bindings.route(sessionId, firstId, payloadTypeEightPacket()).size());

        bindings.removeSession(sessionId);
        assertEquals(0, bindings.bindingCount(sessionId));
        assertEquals(0, bindings.activeSessionCount());
    }

    private static MediaSignal drainOne(
        InMemoryMediaRoomService rooms,
        UUID sessionId,
        UUID participantId
    ) {
        var signals = rooms.drainParticipantSignals(sessionId, participantId, 10);
        assertEquals(1, signals.size());
        return signals.getFirst();
    }

    private static void submitOffer(
        InMemoryMediaRoomService rooms,
        UUID sessionId,
        UUID participantId,
        String suffix
    ) {
        rooms.acceptParticipantSignal(
            sessionId,
            participantId,
            SignalType.OFFER,
            """
                v=0\r
                o=- 42 2 IN IP4 127.0.0.1\r
                s=-\r
                t=0 0\r
                a=group:BUNDLE 0\r
                a=ice-ufrag:%sUfrag\r
                a=ice-pwd:%sPassword1234567890\r
                a=fingerprint:sha-256 AA:BB:CC\r
                m=audio 9 UDP/TLS/RTP/SAVPF 111 0\r
                c=IN IP4 0.0.0.0\r
                a=mid:0\r
                a=sendrecv\r
                a=rtcp-mux\r
                a=rtpmap:111 opus/48000/2\r
                a=rtpmap:0 PCMU/8000\r
                """.formatted(suffix, suffix),
            null
        );
    }

    private static RtpPacket pcmuPacket(int sequenceNumber, int... payload) {
        var wire = new byte[12 + payload.length];
        wire[0] = (byte) 0x80;
        wire[1] = 0;
        wire[2] = (byte) (sequenceNumber >>> 8);
        wire[3] = (byte) sequenceNumber;
        wire[7] = (byte) sequenceNumber;
        wire[11] = 0x42;
        for (var index = 0; index < payload.length; index++) {
            wire[12 + index] = (byte) payload[index];
        }
        return RtpPacket.parse(wire);
    }

    private static RtpPacket payloadTypeEightPacket() {
        var wire = pcmuPacket(9, 0x77).wireBytes();
        wire[1] = 8;
        return RtpPacket.parse(wire);
    }
}
