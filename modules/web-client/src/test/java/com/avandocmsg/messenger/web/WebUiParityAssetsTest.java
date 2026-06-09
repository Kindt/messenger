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
            "locales/ru.js",
            "locales/en.js",
            "ui-i18n.js",
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
        assertTrue(app.contains("/versions"), "message versions path");
        assertTrue(app.contains("/plaintext-preview"), "plaintext-preview path");
        assertTrue(app.contains("/public-links"), "file public-link paths");
        assertTrue(app.contains("/files/") && app.contains("fetchFileMetadata"), "file metadata path");
        assertTrue(app.contains("auth-link"), "auth-link path");
        assertTrue(app.contains("/export"), "chat export paths");
        assertTrue(app.contains("/attachments"), "export attachments path");
        assertTrue(app.contains("/read-receipts"), "read receipts REST path");
        assertTrue(app.contains("createConferenceInChat"), "in-chat conference path");
        assertTrue(app.contains("scheduleWsReconnect"), "ws reconnect scheduler");
        assertTrue(app.contains("KorusUiRtcUtils"), "rtc utils delegation");
        assertTrue(app.contains("KorusUiTransportUtils"), "transport utils delegation");
        assertTrue(app.contains("KorusI18n") || app.contains("localErr"), "i18n error localization");
        assertTrue(app.contains("/conferences") && app.contains("createConference"), "standalone conference flow");
    }

    @Test
    void localeBundles_shareSameKeyPaths() throws Exception {
        var en = readResource("webui/locales/en.js");
        var ru = readResource("webui/locales/ru.js");
        for (var token : List.of(
            "startInChat",
            "readReceipts",
            "common:",
            "deleteMessage",
            "defaultMeetingTitle"
        )) {
            assertTrue(en.contains(token), "en.js missing " + token);
            assertTrue(ru.contains(token), "ru.js missing " + token);
        }
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
        assertTrue(transport.contains("translateError") || transport.contains("KorusI18n"), "i18n in transport");
    }
}
