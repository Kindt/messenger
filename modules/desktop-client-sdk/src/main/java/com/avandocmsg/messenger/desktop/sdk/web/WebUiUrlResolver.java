package com.avandocmsg.messenger.desktop.sdk.web;

import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Resolves web UI base URL from server entry or API host (QEMU :18080 → :19088). */
public final class WebUiUrlResolver {

    private static final String ENV_WEB_UI_URL = "KORUS_WEB_UI_URL";

    private WebUiUrlResolver() {}

    public static String resolve(ServerEntry entry) {
        var fromEnv = System.getenv(ENV_WEB_UI_URL);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return normalizeBase(fromEnv);
        }
        return defaultFromApiBase(entry.apiBaseUrl());
    }

    public static String defaultFromApiBase(String apiBaseUrl) {
        var raw = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (raw.endsWith("/api")) {
            raw = raw.substring(0, raw.length() - 4);
        }
        var uri = URI.create(raw.isEmpty() ? "http://127.0.0.1:18080" : raw);
        var scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        var host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        var port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
        var webPort = port == 18080 ? 19088 : port;
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        var base = scheme + "://" + host + ":" + webPort + path;
        return normalizeBase(base);
    }

    public static String callJoinUrl(String webBase, String chatId, String sessionId, String mediaMode) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId required");
        }
        var mode = "video".equalsIgnoreCase(mediaMode) ? "video" : "audio";
        var base = normalizeBase(webBase);
        return base
            + "?chat=" + encode(chatId)
            + "&call_session=" + encode(sessionId)
            + "&call_mode=" + mode;
    }

    /** @deprecated Use {@link #callJoinUrl(String, String, String, String)}. */
    @Deprecated(forRemoval = true)
    public static String meshJoinUrl(String webBase, String chatId, String sessionId, String mediaMode) {
        return callJoinUrl(webBase, chatId, sessionId, mediaMode);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeBase(String url) {
        var trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
