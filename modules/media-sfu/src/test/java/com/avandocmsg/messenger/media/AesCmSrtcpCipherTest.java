package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AesCmSrtcpCipherTest {

    @Test
    void protectsAuthenticatesAndRestoresRtcp() {
        var keys = SrtpSessionKeys.deriveRtcp(
            hex("0d c9 2b 7e 50 a6 68 92 8f 0f 7a 1c 16 f3 f7 44"),
            hex("62 77 8d a3 b7 6a ef e4 91 90 16 3a c2 b8")
        );
        var cipher = new AesCmSrtcpCipher(keys);
        var receiverReport = hex(
            "81 c9 00 07 ca fe ba be 12 34 56 78 00 00 00 00"
                + " 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
        );

        var protectedPacket = cipher.protect(receiverReport, 17);
        var restored = cipher.unprotect(protectedPacket);

        assertEquals(receiverReport.length + 14, protectedPacket.length);
        assertEquals(17, restored.index());
        assertArrayEquals(receiverReport, restored.bytes());

        protectedPacket[12] ^= 1;
        assertThrows(SecurityException.class, () -> cipher.unprotect(protectedPacket));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value.replace(" ", ""));
    }
}
