package com.avandocmsg.messenger.media;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public record MediaSfuConfiguration(
    MediaSfuMode mode,
    String nodeId,
    int port,
    Duration idleTimeout,
    int lastN,
    String bindAddress,
    String publicAddress,
    int mediaPortMin,
    int mediaPortMax
) {
    public static MediaSfuConfiguration from(Map<String, String> environment) {
        var mode = parseMode(environment.getOrDefault("MEDIA_SFU_MODE", "embedded"));
        var nodeId = environment.getOrDefault(
            "MEDIA_SFU_NODE_ID",
            mode == MediaSfuMode.EMBEDDED ? "embedded-1" : "media-1"
        ).trim();
        var port = parseInt(environment, "MEDIA_SFU_PORT", 18090);
        var idleSeconds = parseInt(environment, "MEDIA_SFU_IDLE_SECONDS", 120);
        var lastN = parseInt(environment, "MEDIA_SFU_LAST_N", 4);
        var bindAddress = environment.getOrDefault("MEDIA_SFU_BIND_ADDRESS", "0.0.0.0").trim();
        var publicAddress = environment.getOrDefault("MEDIA_SFU_PUBLIC_ADDRESS", "127.0.0.1").trim();
        var mediaPortMin = parseInt(environment, "MEDIA_SFU_PORT_MIN", 40000);
        var mediaPortMax = parseInt(environment, "MEDIA_SFU_PORT_MAX", 40100);
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("MEDIA_SFU_NODE_ID required");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("MEDIA_SFU_PORT out of range");
        }
        if (idleSeconds < 10 || idleSeconds > 86_400) {
            throw new IllegalArgumentException("MEDIA_SFU_IDLE_SECONDS out of range");
        }
        if (lastN < 1 || lastN > 64) {
            throw new IllegalArgumentException("MEDIA_SFU_LAST_N out of range");
        }
        if (bindAddress.isBlank() || publicAddress.isBlank()) {
            throw new IllegalArgumentException("MEDIA_SFU bind and public addresses required");
        }
        if (mediaPortMin < 1 || mediaPortMax < mediaPortMin || mediaPortMax > 65_535) {
            throw new IllegalArgumentException("MEDIA_SFU media port range invalid");
        }
        return new MediaSfuConfiguration(
            mode,
            nodeId,
            port,
            Duration.ofSeconds(idleSeconds),
            lastN,
            bindAddress,
            publicAddress,
            mediaPortMin,
            mediaPortMax
        );
    }

    public static MediaSfuConfiguration fromEnvironment() {
        return from(System.getenv());
    }

    private static MediaSfuMode parseMode(String value) {
        try {
            return MediaSfuMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("MEDIA_SFU_MODE must be embedded or standalone", error);
        }
    }

    private static int parseInt(Map<String, String> environment, String key, int fallback) {
        var value = environment.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer", error);
        }
    }
}
