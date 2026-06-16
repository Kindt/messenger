package com.avandocmsg.messenger.worker.botdelivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotWebhookSignerTest {

    @Test
    void signSha256Hex_deterministic() {
        var sig = BotWebhookSigner.signSha256Hex("secret", "{\"a\":1}");
        assertTrue(sig.startsWith("sha256="));
        assertEquals(sig, BotWebhookSigner.signSha256Hex("secret", "{\"a\":1}"));
    }

    @Test
    void signSha256Hex_blankSecret_returnsNull() {
        assertNull(BotWebhookSigner.signSha256Hex("", "{}"));
        assertNull(BotWebhookSigner.signSha256Hex(null, "{}"));
    }
}
