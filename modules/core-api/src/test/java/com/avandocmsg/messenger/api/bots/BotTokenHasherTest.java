package com.avandocmsg.messenger.api.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotTokenHasherTest {

    @Test
    void generateToken_startsWithPrefix() {
        var token = BotTokenHasher.generateToken();
        assertTrue(token.startsWith("kbt_"));
        assertTrue(token.length() > 20);
    }

    @Test
    void hashToken_isDeterministic() {
        var token = "kbt_testtoken";
        assertEqualsHex(BotTokenHasher.hashToken(token), BotTokenHasher.hashToken(token));
    }

    @Test
    void hashToken_differsForDifferentTokens() {
        assertNotEquals(BotTokenHasher.hashToken("kbt_a"), BotTokenHasher.hashToken("kbt_b"));
    }

    private static void assertEqualsHex(String a, String b) {
        org.junit.jupiter.api.Assertions.assertEquals(a, b);
    }
}
