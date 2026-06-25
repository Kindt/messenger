package com.avandocmsg.messenger.web;

import java.net.http.HttpClient;
import java.time.Duration;

/** Outbound proxy client tuning (spec 025 FR-032). Env: {@code WEB_CLIENT_UPSTREAM_HTTP2_ENABLED}. */
public record UpstreamProxySettings(boolean http2Enabled) {

    public static UpstreamProxySettings fromEnv() {
        return new UpstreamProxySettings(parseEnabled(System.getenv("WEB_CLIENT_UPSTREAM_HTTP2_ENABLED"), true));
    }

    static boolean parseEnabled(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    public static HttpClient buildClient(UpstreamProxySettings settings, Duration connectTimeout) {
        var builder = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(settings.http2Enabled() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1);
        return builder.build();
    }
}
