package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RtpPacketTest {

    @Test
    void parsesRtpWithoutDecodingMediaPayload() {
        byte[] wire = {
            (byte) 0x80, (byte) 0xe0,
            0x00, 0x07,
            0x01, 0x02, 0x03, 0x04,
            0x11, 0x22, 0x33, 0x44,
            0x55, 0x66, 0x77
        };

        var packet = RtpPacket.parse(wire);

        assertEquals(96, packet.payloadType());
        assertEquals(7, packet.sequenceNumber());
        assertEquals(0x01020304L, packet.timestamp());
        assertEquals(0x11223344L, packet.ssrc());
        assertEquals(true, packet.marker());
        assertArrayEquals(new byte[] {0x55, 0x66, 0x77}, packet.payload());
        assertArrayEquals(wire, packet.wireBytes());
    }

    @Test
    void rejectsMalformedRtp() {
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.parse(new byte[] {0x00, 0x01}));
    }
}
