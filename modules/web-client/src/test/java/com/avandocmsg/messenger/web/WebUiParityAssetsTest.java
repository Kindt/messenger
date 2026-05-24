package com.avandocmsg.messenger.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static parity checks for webui assets (spec 002 T010/T016/T022 wiring without live stack).
 */
class WebUiParityAssetsTest {

    private static String readResource(String name) throws Exception {
        try (InputStream in = WebUiParityAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "missing classpath resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void indexHtml_loadsUtilityModulesBeforeAppJs() throws Exception {
        var html = readResource("webui/index.html");
        var scripts = List.of(
            "ui-shell-utils.js",
            "ui-transport-utils.js",
            "ui-format-utils.js",
            "ui-messages-utils.js",
            "ui-rtc-utils.js",
            "ui-pwa-settings-utils.js",
            "app.js"
        );
        var last = -1;
        for (var script : scripts) {
            var idx = html.indexOf(script);
            assertTrue(idx >= 0, "index.html must reference " + script);
            assertTrue(idx > last, script + " must appear after prior scripts");
            last = idx;
        }
    }

    @Test
    void appJs_wiresMessagingFileExportAndReconnectParityPaths() throws Exception {
        var app = readResource("webui/app.js");
        assertTrue(app.contains("/messages/") && app.contains("/pin"), "message pin path");
        assertTrue(app.contains("/reactions"), "message reactions path");
        assertTrue(app.contains("/forward"), "message forward path");
        assertTrue(app.contains("/public-links"), "file public-link paths");
        assertTrue(app.contains("/export"), "chat export paths");
        assertTrue(app.contains("scheduleWsReconnect"), "ws reconnect scheduler");
        assertTrue(app.contains("KorusUiRtcUtils"), "rtc utils delegation");
        assertTrue(app.contains("KorusUiTransportUtils"), "transport utils delegation");
    }

    @Test
    void rtcUtils_preservesContractEnvelope() throws Exception {
        var rtc = readResource("webui/ui-rtc-utils.js");
        assertTrue(rtc.contains("\"rtc_signal\""), "rtc_signal type");
        assertTrue(rtc.contains("chatId"), "chatId field");
        assertTrue(rtc.contains("payload"), "payload field");
        assertTrue(rtc.contains("\"hangup\""), "hangup kind");
    }

    @Test
    void transportUtils_exposesReconnectBackoff() throws Exception {
        var transport = readResource("webui/ui-transport-utils.js");
        assertTrue(transport.contains("nextWsReconnectDelay"), "reconnect backoff helper");
        assertTrue(transport.contains("buildWsUrl"), "ws url builder");
    }
}
