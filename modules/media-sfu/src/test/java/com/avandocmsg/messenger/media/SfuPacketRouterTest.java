package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SfuPacketRouterTest {

    @Test
    void forwardsOnlyToSubscribersWithoutDecodingPayload() {
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var session = rooms.create(UUID.randomUUID(), UUID.randomUUID(), CallKind.GROUP);
        var publisher = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.HOST);
        var subscribedViewer = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        var otherViewer = rooms.join(session.sessionId(), UUID.randomUUID(), ParticipantRole.MEMBER);
        var router = new SfuPacketRouter(rooms, 2);
        router.updateSubscriptions(
            session.sessionId(),
            subscribedViewer.participantId(),
            List.of(publisher.participantId())
        );
        router.updateSubscriptions(
            session.sessionId(),
            otherViewer.participantId(),
            List.of(subscribedViewer.participantId())
        );
        var packet = RtpPacket.parse(new byte[] {
            (byte) 0x80, 96, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 9, 8, 7
        });

        var forwarded = router.route(session.sessionId(), publisher.participantId(), packet);

        assertEquals(1, forwarded.size());
        assertEquals(subscribedViewer.participantId(), forwarded.getFirst().participantId());
        assertEquals(List.of(9, 8, 7), bytes(forwarded.getFirst().packet().payload()));
    }

    private static List<Integer> bytes(byte[] data) {
        var result = new java.util.ArrayList<Integer>(data.length);
        for (byte value : data) {
            result.add(Byte.toUnsignedInt(value));
        }
        return result;
    }
}
