package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NativeWebRtcAudioClientTest {

    @Test
    void twoNativeClientsExchangePcmuThroughTheEmbeddedMediaNode() throws Exception {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var owner = UUID.randomUUID();
        var session = rooms.create(UUID.randomUUID(), owner, CallKind.GROUP);
        var first = rooms.join(session.sessionId(), owner, ParticipantRole.HOST);
        var second = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        var identity = DtlsIdentity.generate(Clock.systemUTC(), new SecureRandom());
        var received = new ArrayBlockingQueue<byte[]>(4);

        try (
            var node = new EmbeddedWebRtcMediaNode(
                rooms,
                InetAddress.getLoopbackAddress(),
                InetAddress.getLoopbackAddress(),
                0,
                0,
                identity
            );
            var caller = NativeWebRtcAudioClient.create();
            var callee = NativeWebRtcAudioClient.create()
        ) {
            connect(rooms, node, session.sessionId(), first.participantId(), caller);
            connect(rooms, node, session.sessionId(), second.participantId(), callee);
            callee.onPcmu(received::add);

            var payload = new byte[160];
            for (var i = 0; i < payload.length; i++) {
                payload[i] = (byte) (i + 7);
            }
            caller.sendPcmu(payload);

            var remote = received.poll(8, TimeUnit.SECONDS);
            assertTrue(caller.mediaReady());
            assertTrue(callee.mediaReady());
            assertArrayEquals(payload, remote);
        }
    }

    @Test
    void buildsPcmuOfferWithIceAndDtlsFingerprint() {
        try (var client = NativeWebRtcAudioClient.create()) {
            var offer = WebRtcSdpOffer.parse(client.createOffer());
            assertEquals("sha-256 " + client.fingerprint(), offer.fingerprint());
            assertEquals(1, offer.media().size());
            assertEquals("audio", offer.media().getFirst().type());
            assertTrue(offer.media().getFirst().codecs().getFirst().isPcmuPtZero());
        }
    }

    private static void connect(
        InMemoryMediaRoomService rooms,
        EmbeddedWebRtcMediaNode node,
        UUID sessionId,
        UUID participantId,
        NativeWebRtcAudioClient client
    ) throws Exception {
        rooms.acceptParticipantSignal(sessionId, participantId, SignalType.OFFER, client.createOffer(), null);
        node.processPending(sessionId);
        var answer = rooms.drainParticipantSignals(sessionId, participantId, 1).getFirst();
        assertEquals(SignalType.ANSWER, answer.type());
        client.connect(answer.sdp());
    }
}
