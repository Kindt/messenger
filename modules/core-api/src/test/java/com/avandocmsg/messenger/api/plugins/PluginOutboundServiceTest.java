package com.avandocmsg.messenger.api.plugins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PluginOutboundServiceTest {

    @Test
    void hashTokenIsStable() {
        var a = PluginOutboundService.hashToken("secret-token");
        var b = PluginOutboundService.hashToken("secret-token");
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void hashTokenDiffersForDifferentSecrets() {
        assertNotEquals(
            PluginOutboundService.hashToken("one"),
            PluginOutboundService.hashToken("two")
        );
    }
}
