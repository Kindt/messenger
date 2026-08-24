package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddedWebRtcMediaNodeTest {

    @Test
    void turnsParticipantOfferIntoOwnedMediaNodeAnswer() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var session = rooms.create(UUID.randomUUID(), UUID.randomUUID(), CallKind.DIRECT);
        var participant = rooms.join(session.sessionId(), session.createdBy(), ParticipantRole.HOST);
        rooms.acceptParticipantSignal(
            session.sessionId(),
            participant.participantId(),
            SignalType.OFFER,
            offer(),
            null
        );
        var identity = DtlsIdentity.generate(Clock.systemUTC(), new SecureRandom());
        try (var node = new EmbeddedWebRtcMediaNode(
            rooms,
            InetAddress.getLoopbackAddress(),
            InetAddress.getLoopbackAddress(),
            0,
            0,
            identity
        )) {
            node.processPending(session.sessionId());

            var signals = rooms.drainParticipantSignals(
                session.sessionId(),
                participant.participantId(),
                10
            );
            assertEquals(1, signals.size());
            assertEquals(SignalType.ANSWER, signals.getFirst().type());
            assertTrue(signals.getFirst().sdp().contains("a=ice-lite\r\n"));
            assertTrue(signals.getFirst().sdp().contains(identity.sha256Fingerprint()));
            assertTrue(signals.getFirst().sdp().contains("m=audio 9 UDP/TLS/RTP/SAVPF 0\r\n"));
            assertFalse(signals.getFirst().sdp().contains("a=rtpmap:111 opus/48000/2\r\n"));
            assertEquals(1, node.activeTransportCount());
        }
    }

    @Test
    void keepsOfferedCodecsForGroupSessions() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var session = rooms.create(UUID.randomUUID(), UUID.randomUUID(), CallKind.GROUP);
        var participant = rooms.join(session.sessionId(), session.createdBy(), ParticipantRole.HOST);
        rooms.acceptParticipantSignal(
            session.sessionId(),
            participant.participantId(),
            SignalType.OFFER,
            offer(),
            null
        );
        var identity = DtlsIdentity.generate(Clock.systemUTC(), new SecureRandom());
        try (var node = new EmbeddedWebRtcMediaNode(
            rooms,
            InetAddress.getLoopbackAddress(),
            InetAddress.getLoopbackAddress(),
            0,
            0,
            identity
        )) {
            node.processPending(session.sessionId());

            var answer = rooms.drainParticipantSignals(
                session.sessionId(),
                participant.participantId(),
                10
            ).getFirst().sdp();
            assertTrue(answer.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111 0\r\n"));
            assertTrue(answer.contains("a=rtpmap:111 opus/48000/2\r\n"));
        }
    }

    @Test
    void keepsOfferedMediaForDirectVideoSessions() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var session = rooms.create(UUID.randomUUID(), UUID.randomUUID(), CallKind.DIRECT);
        var participant = rooms.join(session.sessionId(), session.createdBy(), ParticipantRole.HOST);
        rooms.acceptParticipantSignal(
            session.sessionId(),
            participant.participantId(),
            SignalType.OFFER,
            videoOffer(),
            null
        );
        var identity = DtlsIdentity.generate(Clock.systemUTC(), new SecureRandom());
        try (var node = new EmbeddedWebRtcMediaNode(
            rooms,
            InetAddress.getLoopbackAddress(),
            InetAddress.getLoopbackAddress(),
            0,
            0,
            identity
        )) {
            node.processPending(session.sessionId());

            var answer = rooms.drainParticipantSignals(
                session.sessionId(),
                participant.participantId(),
                10
            ).getFirst().sdp();
            assertTrue(answer.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111 0\r\n"));
            assertTrue(answer.contains("a=rtpmap:111 opus/48000/2\r\n"));
            assertTrue(answer.contains("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n"));
            assertTrue(answer.contains("a=rtpmap:96 VP8/90000\r\n"));
        }
    }

    private static String offer() {
        return """
            v=0\r
            o=- 42 2 IN IP4 127.0.0.1\r
            s=-\r
            t=0 0\r
            a=group:BUNDLE 0\r
            a=ice-ufrag:clientUfrag\r
            a=ice-pwd:clientPassword1234567890\r
            a=fingerprint:sha-256 AA:BB:CC\r
            m=audio 9 UDP/TLS/RTP/SAVPF 111 0\r
            c=IN IP4 0.0.0.0\r
            a=mid:0\r
            a=sendrecv\r
            a=rtcp-mux\r
            a=rtpmap:111 opus/48000/2\r
            a=rtpmap:0 PCMU/8000\r
            """;
    }

    private static String videoOffer() {
        return offer() + """
            m=video 9 UDP/TLS/RTP/SAVPF 96\r
            c=IN IP4 0.0.0.0\r
            a=mid:1\r
            a=sendrecv\r
            a=rtcp-mux\r
            a=rtpmap:96 VP8/90000\r
            """;
    }
}
