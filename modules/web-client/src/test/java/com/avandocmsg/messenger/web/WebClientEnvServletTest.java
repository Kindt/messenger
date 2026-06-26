package com.avandocmsg.messenger.web;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientEnvServletTest {

    @Test
    void defaultWs_andNullIce_whenUnset() {
        String body = WebClientEnvServlet.buildEnvScriptBody(k -> null);
        assertEquals(
            "window.__WEB_CLIENT__ = { wsUrl: \"ws://127.0.0.1:8081/ws\", iceServersJson: null, vapidPublicKey: null, watermarkText: null, disableServiceWorker: false, demoSkinsEnabled: true };\n",
            body);
    }

    @Test
    void trimsTrailingSlash_onWsUrl() {
        Map<String, String> m = new HashMap<>();
        m.put("WEB_CLIENT_WS_PUBLIC_URL", "ws://lb.example/ws/");
        String body = WebClientEnvServlet.buildEnvScriptBody(m::get);
        assertTrue(body.contains("wsUrl: \"ws://lb.example/ws\""));
        assertTrue(body.contains("iceServersJson: null"));
    }

    @Test
    void iceServersJson_quotedJson_forClientParse() {
        Map<String, String> m = new HashMap<>();
        m.put("WEB_CLIENT_WS_PUBLIC_URL", "ws://x/ws");
        m.put("WEB_CLIENT_RTC_ICE_SERVERS", "[{\"urls\":\"stun:custom:19302\"}]");
        String body = WebClientEnvServlet.buildEnvScriptBody(m::get);
        assertTrue(body.contains("wsUrl: \"ws://x/ws\""));
        assertTrue(body.contains("iceServersJson: \"[{\\\"urls\\\":\\\"stun:custom:19302\\\"}]\""));
    }

    @Test
    void vapidPublicKey_quotedWhenSet() {
        Map<String, String> m = new HashMap<>();
        m.put("WEB_CLIENT_WS_PUBLIC_URL", "ws://h/ws");
        m.put("WEB_CLIENT_VAPID_PUBLIC_KEY", "BKx-example");
        String body = WebClientEnvServlet.buildEnvScriptBody(m::get);
        assertTrue(body.contains("vapidPublicKey: \"BKx-example\""));
    }

    @Test
    void demoSkinsDisabled_whenEnvFalse() {
        Map<String, String> m = new HashMap<>();
        m.put("WEB_CLIENT_DEMO_SKINS", "false");
        String body = WebClientEnvServlet.buildEnvScriptBody(m::get);
        assertTrue(body.contains("demoSkinsEnabled: false"));
    }
}
