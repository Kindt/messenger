package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrowserPcmuAnswerPolicyTest {

    @Test
    void selectsOnlyPcmuFromNormalChromiumOffer() {
        var offer = WebRtcSdpOffer.parse(normalBrowserAudioOffer("policy"));
        var audioCodecs = offer.media().stream()
            .filter(media -> media.type().equals("audio"))
            .flatMap(media -> media.codecs().stream())
            .toList();
        assertTrue(audioCodecs.stream().anyMatch(codec ->
            codec.name().equals("OPUS")
                && codec.clockRate() == 48_000
                && codec.channels() == 2
                && "minptime=10;useinbandfec=1".equals(codec.fmtp())
        ));
        assertTrue(audioCodecs.stream().anyMatch(RtpCodecDescriptor::isPcmuPtZero));

        var answer = new WebRtcSdpAnswerBuilder(
            "serverUfrag",
            "serverPassword1234567890",
            "11:22:33",
            new InetSocketAddress("192.0.2.10", 40_000)
        ).answer(offer, WebRtcSdpAnswerPolicy.DIRECT_PCMU_AUDIO);

        assertTrue(answer.contains("m=audio 9 UDP/TLS/RTP/SAVPF 0\r\n"));
        assertTrue(answer.contains("a=rtpmap:0 PCMU/8000\r\n"));
        assertFalse(answer.contains("a=rtpmap:111 opus/48000/2\r\n"));
        assertTrue(answer.contains("m=video 0 UDP/TLS/RTP/SAVPF 96\r\n"));
    }

    @Test
    void acceptsSecondParticipantWithSelectedPcmu() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var ownerId = UUID.randomUUID();
        var session = rooms.create(UUID.randomUUID(), ownerId, CallKind.DIRECT);
        var first = rooms.join(session.sessionId(), ownerId, ParticipantRole.HOST);
        var second = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        rooms.acceptParticipantSignal(
            session.sessionId(),
            first.participantId(),
            SignalType.OFFER,
            normalBrowserAudioOffer("first"),
            null
        );
        rooms.acceptParticipantSignal(
            session.sessionId(),
            second.participantId(),
            SignalType.OFFER,
            normalBrowserAudioOffer("second"),
            null
        );

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

            var selected = rooms.selectedAudioCodec(session.sessionId()).orElseThrow();
            assertEquals("PCMU", selected.name());
            assertEquals(8_000, selected.clockRate());
            assertEquals(1, selected.channels());
            assertEquals(0, selected.payloadType());
            assertPcmuAnswer(rooms, session.sessionId(), first.participantId());
            assertPcmuAnswer(rooms, session.sessionId(), second.participantId());
            assertEquals(2, node.activeTransportCount());
        }

        assertEquals(0, node.activeTransportCount());
        rooms.end(session.sessionId(), ownerId);
        assertEquals(1, rooms.removeIdleRooms());
        assertFalse(rooms.containsSession(session.sessionId()));
    }

    @Test
    void rejectsParticipantWithoutSelectedPcmu() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var ownerId = UUID.randomUUID();
        var session = rooms.create(UUID.randomUUID(), ownerId, CallKind.DIRECT);
        var first = rooms.join(session.sessionId(), ownerId, ParticipantRole.HOST);
        var rejected = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
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
            rooms.acceptParticipantSignal(
                session.sessionId(),
                first.participantId(),
                SignalType.OFFER,
                normalBrowserAudioOffer("first"),
                null
            );
            node.processPending(session.sessionId());
            assertPcmuAnswer(rooms, session.sessionId(), first.participantId());
            assertEquals(1, node.activeTransportCount());

            rooms.acceptParticipantSignal(
                session.sessionId(),
                rejected.participantId(),
                SignalType.OFFER,
                opusOnlyOffer(),
                null
            );
            node.processPending(session.sessionId());

            var signals = rooms.drainParticipantSignals(
                session.sessionId(),
                rejected.participantId(),
                10
            );
            assertEquals(1, signals.size());
            assertEquals(SignalType.ERROR, signals.getFirst().type());
            assertEquals("NO_COMMON_AUDIO_CODEC", signals.getFirst().errorCode());
            assertEquals(1, node.activeTransportCount());
            assertEquals(
                ParticipantState.CONNECTED,
                rooms.requireParticipant(session.sessionId(), first.participantId()).state()
            );
            assertTrue(rooms.selectedAudioCodec(session.sessionId()).orElseThrow().isPcmuPtZero());
        }

        assertEquals(0, node.activeTransportCount());
        rooms.end(session.sessionId(), ownerId);
        assertEquals(1, rooms.removeIdleRooms());
        assertFalse(rooms.containsSession(session.sessionId()));
    }

    private static void assertPcmuAnswer(
        InMemoryMediaRoomService rooms,
        UUID sessionId,
        UUID participantId
    ) {
        var signals = rooms.drainParticipantSignals(sessionId, participantId, 10);
        assertEquals(1, signals.size());
        assertEquals(SignalType.ANSWER, signals.getFirst().type());
        assertTrue(signals.getFirst().sdp().contains("m=audio 9 UDP/TLS/RTP/SAVPF 0\r\n"));
        assertFalse(signals.getFirst().sdp().contains("a=rtpmap:111 opus/48000/2\r\n"));
    }

    private static String normalBrowserAudioOffer(String suffix) {
        return """
            v=0\r
            o=- 42 2 IN IP4 127.0.0.1\r
            s=-\r
            t=0 0\r
            a=group:BUNDLE 0 1\r
            a=ice-ufrag:%sUfrag\r
            a=ice-pwd:%sPassword1234567890\r
            a=fingerprint:sha-256 AA:BB:CC\r
            m=audio 9 UDP/TLS/RTP/SAVPF 111 0 8 13\r
            c=IN IP4 0.0.0.0\r
            a=mid:0\r
            a=sendrecv\r
            a=rtcp-mux\r
            a=rtpmap:111 opus/48000/2\r
            a=fmtp:111 minptime=10;useinbandfec=1\r
            a=rtpmap:0 PCMU/8000\r
            a=rtpmap:8 PCMA/8000\r
            a=rtpmap:13 CN/8000\r
            m=video 9 UDP/TLS/RTP/SAVPF 96\r
            c=IN IP4 0.0.0.0\r
            a=mid:1\r
            a=recvonly\r
            a=rtcp-mux\r
            a=rtpmap:96 VP8/90000\r
            """.formatted(suffix, suffix);
    }

    private static String opusOnlyOffer() {
        return normalBrowserAudioOffer("rejected").replace(
            "m=audio 9 UDP/TLS/RTP/SAVPF 111 0 8 13",
            "m=audio 9 UDP/TLS/RTP/SAVPF 111"
        );
    }
}
