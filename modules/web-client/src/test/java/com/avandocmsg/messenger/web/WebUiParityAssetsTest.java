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
            "ui-i18n.js",
            "ui-shell-utils.js",
            "ui-transport-utils.js",
            "ui-ws-client.js",
            "ui-ws-events.js",
            "ui-ws-handler.js",
            "ui-format-utils.js",
            "ui-messages-utils.js",
            "ui-deep-link-utils.js",
            "ui-clipboard-utils.js",
            "ui-markdown-utils.js",
            "ui-file-attach.js",
            "ui-message-content.js",
            "ui-message-reply.js",
            "ui-message-article.js",
            "ui-message-list.js",
            "ui-composer.js",
            "ui-rtc-utils.js",
            "ui-live-session.js",
            "ui-pwa-settings-utils.js",
            "korus-mls-wasm.js",
            "e2ee/openmls/korus-openmls-dev.js",
            "ui-export-utils.js",
            "ui-e2ee-mls.js",
            "ui-e2ee-utils.js",
            "ui-offline-cache.js",
            "app.js"
        );
        var last = -1;
        for (var script : scripts) {
            var idx = html.indexOf(script);
            assertTrue(idx >= 0, "index.html must reference " + script);
            assertTrue(idx > last, script + " must appear after prior scripts");
            last = idx;
        }
        assertTrue(!html.contains("locales/ru.js"), "legacy locale .js scripts must not be in index.html");
    }

    @Test
    void appJs_wiresMessagingFileExportAndReconnectParityPaths() throws Exception {
        var app = readResource("webui/app.js");
        var exportUtils = readResource("webui/ui-export-utils.js");
        assertTrue(app.contains("/messages/") && app.contains("/pin"), "message pin path");
        assertTrue(app.contains("/reactions"), "message reactions path");
        assertTrue(app.contains("/forward"), "message forward path");
        assertTrue(app.contains("/versions"), "message versions path");
        var e2eeUtils = readResource("webui/ui-e2ee-utils.js");
        assertTrue(e2eeUtils.contains("/plaintext-preview"), "plaintext-preview path in ui-e2ee-utils");
        assertTrue(app.contains("/public-links"), "file public-link paths");
        assertTrue(app.contains("/files/") && app.contains("fetchFileMetadata"), "file metadata path");
        assertTrue(app.contains("auth-link"), "auth-link path");
        assertTrue(exportUtils.contains("/export"), "chat export paths in ui-export-utils");
        assertTrue(app.contains("KorusUiExportUtils"), "export utils delegation");
        assertTrue(app.contains("KorusUiE2eeMls"), "e2ee mls utils delegation");
        assertTrue(app.contains("KorusUiE2eeUtils"), "e2ee utils delegation");
        var messageArticle = readResource("webui/ui-message-article.js");
        assertTrue(messageArticle.contains("message-reply-button"), "reply button testid");
        assertTrue(messageArticle.contains("message-forward-button"), "forward button testid");
        assertTrue(messageArticle.contains("message-delete-button"), "delete button testid");
        assertTrue(messageArticle.contains("message-edit-button"), "edit button testid");
        assertTrue(messageArticle.contains("message-link-button"), "link button testid");
        assertTrue(app.contains("integration-panel"), "integration iframe panel testid");
        assertTrue(app.contains("sidebar-filter-bar"), "sidebar filter bar");
        assertTrue(app.contains("sidebar-filter-"), "sidebar filter testid prefix");
        assertTrue(app.contains("sidebarChatFilter"), "sidebar chat filter state");
        assertTrue(app.contains("openIntegration"), "integration launcher");
        assertTrue(app.contains("getMessageReplyCtx"), "reply ctx builder in app");
        assertTrue(app.contains("reply_preview"), "reply_preview from API");
        assertTrue(app.contains("mesh-webrtc-button"), "mesh webrtc testid");
        assertTrue(app.contains("KorusMlsWasmFactory"), "mls wasm factory");
        assertTrue(app.contains("e2ee_openmls_dev"), "openmls dev flag");
        assertTrue(app.contains("KorusOpenMlsDevFactory"), "openmls dev factory selection");
        assertTrue(app.contains("chat-export-button"), "export button testid");
        var mlsWasm = readResource("webui/korus-mls-wasm.js");
        assertTrue(mlsWasm.contains("/e2ee/mls/session/"), "mls session API for client encrypt");
        assertTrue(exportUtils.contains("/attachments"), "export attachments path");
        assertTrue(app.contains("/read-receipts"), "read receipts REST path");
        assertTrue(app.contains("createConferenceInChat"), "in-chat conference path");
        assertTrue(app.contains("KorusUiWsClient"), "ws client module delegation");
        assertTrue(app.contains("KorusUiWsHandler"), "ws handler module delegation");
        assertTrue(app.contains("getWsHandlerCtx"), "ws handler ctx builder");
        assertTrue(app.contains("KorusUiFileAttach"), "file attach module delegation");
        assertTrue(app.contains("getFileAttachCtx"), "file attach ctx builder");
        assertTrue(app.contains("SIDEBAR_FOLDER_ICONS"), "sidebar folder icon chips");
        assertTrue(app.contains("sidebar-folder-icon"), "sidebar folder icon chip class");
        assertTrue(app.contains("wsClient"), "ws client instance");
        assertTrue(app.contains("KorusUiRtcUtils"), "rtc utils delegation");
        assertTrue(app.contains("KorusUiTransportUtils"), "transport utils delegation");
        assertTrue(app.contains("mountMessageList"), "virtual message list mount");
        assertTrue(app.contains("KorusUiMessageArticle"), "message article module delegation");
        assertTrue(app.contains("buildMessageArticle"), "message article builder");
        assertTrue(app.contains("KorusUiDeepLinkUtils"), "deep link utils delegation");
        assertTrue(app.contains("KorusUiClipboardUtils"), "clipboard utils delegation");
        assertTrue(app.contains("KorusUiMarkdownUtils"), "markdown utils delegation");
        assertTrue(app.contains("KorusUiMessageContent"), "message content module delegation");
        assertTrue(app.contains("KorusUiMessageReply"), "message reply module delegation");
        assertTrue(app.contains("KorusUiComposer"), "composer module delegation");
        assertTrue(app.contains("mountComposer"), "composer mount");
        var messageReply = readResource("webui/ui-message-reply.js");
        assertTrue(messageReply.contains("message-reply-quote"), "reply quote testid in module");
        var composerJs = readResource("webui/ui-composer.js");
        assertTrue(composerJs.contains("message-composer"), "composer testid in module");
        assertTrue(composerJs.contains("composer-reply-bar"), "composer reply bar in module");
        var messageContent = readResource("webui/ui-message-content.js");
        assertTrue(messageContent.contains("message-voice-player"), "voice player testid");
        assertTrue(app.contains("KorusI18n") || app.contains("localErr"), "i18n error localization");
        assertTrue(app.contains("/conferences") && app.contains("createConference"), "standalone conference flow");
    }

    @Test
    void localeBundles_shareSameKeyPaths() throws Exception {
        var en = readResource("webui/locales/en.json");
        var ru = readResource("webui/locales/ru.json");
        var manifest = readResource("webui/locales/manifest.json");
        for (var token : List.of(
            "startInChat",
            "readReceipts",
            "deleteMessage",
            "defaultMeetingTitle"
        )) {
            assertTrue(en.contains(token), "en.json missing " + token);
            assertTrue(ru.contains(token), "ru.json missing " + token);
        }
        assertTrue(en.contains("\"locale\": \"Language\""), "en.json settings.locale");
        assertTrue(ru.contains("\"locale\": \"Язык\""), "ru.json settings.locale");
        assertTrue(manifest.contains("\"default\": \"ru\""), "manifest default locale");
        assertTrue(manifest.contains("\"codes\""), "manifest locale codes");
        assertTrue(manifest.contains("keyCount"), "manifest locale keyCount");
        for (var token : List.of(
            "folder",
            "integrations",
            "folderWork"
        )) {
            assertTrue(en.contains(token), "en.json sidebar missing " + token);
            assertTrue(ru.contains(token), "ru.json sidebar missing " + token);
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
    void liveSessionModule_wiredInIndexAndApp() throws Exception {
        var html = readResource("webui/index.html");
        var live = readResource("webui/ui-live-session.js");
        var app = readResource("webui/app.js");
        assertTrue(html.contains("ui-live-session.js"), "index loads live module");
        assertTrue(live.contains("KorusUiLiveSession"), "live module export");
        assertTrue(live.contains("live_session_id"), "live session event field");
        assertTrue(app.contains("KorusUiLiveSession"), "app uses live module");
        assertTrue(app.contains("renderLiveSection"), "call panel live section");
    }

    @Test
    void transportUtils_exposesReconnectBackoff() throws Exception {
        var transport = readResource("webui/ui-transport-utils.js");
        assertTrue(transport.contains("nextWsReconnectDelay"), "reconnect backoff helper");
        assertTrue(transport.contains("translateError") || transport.contains("KorusI18n"), "i18n in transport");
    }
}
