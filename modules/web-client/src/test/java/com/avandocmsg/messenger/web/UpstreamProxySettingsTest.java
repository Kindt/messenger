package com.avandocmsg.messenger.web;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamProxySettingsTest {

    @Test
    void defaultHttp2EnabledWhenUnset() {
        assertTrue(UpstreamProxySettings.parseEnabled(null, true));
        assertTrue(UpstreamProxySettings.parseEnabled("  ", true));
    }

    @Test
    void parsesExplicitHttp2Toggle() {
        assertTrue(UpstreamProxySettings.parseEnabled("true", false));
        assertFalse(UpstreamProxySettings.parseEnabled("false", true));
    }

    @Test
    void buildClient_prefersHttp2WhenEnabled() {
        var client = UpstreamProxySettings.buildClient(new UpstreamProxySettings(true), java.time.Duration.ofSeconds(5));
        assertEquals(HttpClient.Version.HTTP_2, client.version());
    }

    @Test
    void buildClient_usesHttp11WhenDisabled() {
        var client = UpstreamProxySettings.buildClient(new UpstreamProxySettings(false), java.time.Duration.ofSeconds(5));
        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
    }
}
