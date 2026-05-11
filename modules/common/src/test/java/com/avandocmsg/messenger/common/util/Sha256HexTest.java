package com.avandocmsg.messenger.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256HexTest {

    @Test
    void knownUtf8JsonVector_lowercaseHex64() {
        byte[] utf8 = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertEquals(
            "015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862",
            Sha256Hex.of(utf8)
        );
    }
}
