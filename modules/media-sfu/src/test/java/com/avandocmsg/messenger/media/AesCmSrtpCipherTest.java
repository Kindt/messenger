package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AesCmSrtpCipherTest {

    @Test
    void matchesRfc3711AesCmKeystreamAndRoundTripsPacket() {
        var keys = new SrtpSessionKeys(
            hex("2B7E151628AED2A6ABF7158809CF4F3C"),
            new byte[20],
            hex("F0F1F2F3F4F5F6F7F8F9FAFBFCFD")
        );
        var cipher = new AesCmSrtpCipher(keys);
        var expectedKeystream = hex("""
            E03EAD0935C95E80E166B16DD92B4EB4
            D23513162B02D0F72A43A2FE4A5F97AB
            41E95B3BB0A2E8DD477901E4FCA894C0
            """);
        var plain = new byte[12 + expectedKeystream.length];
        System.arraycopy(hex("800F0000DECAFBAD00000000"), 0, plain, 0, 12);

        var protectedPacket = cipher.protect(plain, 0);

        assertEquals(plain.length + 10, protectedPacket.length);
        assertArrayEquals(expectedKeystream, Arrays.copyOfRange(protectedPacket, 12, plain.length));
        assertArrayEquals(plain, cipher.unprotect(protectedPacket, 0));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value.replaceAll("\\s+", ""));
    }
}
