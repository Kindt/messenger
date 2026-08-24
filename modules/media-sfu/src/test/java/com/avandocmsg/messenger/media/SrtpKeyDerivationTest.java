package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SrtpKeyDerivationTest {

    @Test
    void matchesRfc3711AppendixB3() {
        var keys = SrtpSessionKeys.derive(
            hex("E1F97A0D3E018BE0D64FA32C06DE4139"),
            hex("0EC675AD498AFEEBB6960B3AABE6")
        );

        assertArrayEquals(hex("C61E7A93744F39EE10734AFE3FF7A087"), keys.encryptionKey());
        assertArrayEquals(hex("30CBBC08863D8C85D49DB34A9AE1"), keys.salt());
        assertArrayEquals(hex("CEBE321F6FF7716B6FD4AB49AF256A156D38BAA4"), keys.authenticationKey());
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
