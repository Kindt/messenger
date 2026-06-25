package com.avandocmsg.messenger.ws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsKeepaliveSettingsTest {

    @Test
    void defaultEnabledWhenUnset() {
        assertTrue(WsKeepaliveSettings.parseEnabled(null, true));
        assertTrue(WsKeepaliveSettings.parseEnabled("  ", true));
    }

    @Test
    void parsesExplicitBoolean() {
        assertTrue(WsKeepaliveSettings.parseEnabled("true", false));
        assertFalse(WsKeepaliveSettings.parseEnabled("false", true));
    }

    @Test
    void defaultIntervals() {
        var settings = new WsKeepaliveSettings(true, 30_000, 90_000);
        assertTrue(settings.enabled());
        assertEquals(30_000L, settings.pingIntervalMs());
        assertEquals(90_000L, settings.pongTimeoutMs());
    }
}
