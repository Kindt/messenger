package com.avandocmsg.messenger.desktop.sdk.ws;

import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import java.net.URI;

/** Resolves WS base URL from server entry or API host (QEMU :18082). */
public final class WsUrlResolver {

    private WsUrlResolver() {}

    public static String resolve(ServerEntry entry) {
        if (entry.wsPublicUrl() != null && !entry.wsPublicUrl().isBlank()) {
            return normalizeBase(entry.wsPublicUrl());
        }
        return defaultFromApiBase(entry.apiBaseUrl());
    }

    public static String defaultFromApiBase(String apiBaseUrl) {
        var uri = URI.create(apiBaseUrl.trim());
        var host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        var port = uri.getPort() > 0 ? uri.getPort() : 18080;
        var wsPort = port == 18080 ? 18082 : port;
        return "ws://" + host + ":" + wsPort + "/ws";
    }

    public static String withToken(String baseUrl, String token) {
        var sep = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + sep + "token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String normalizeBase(String url) {
        var trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
