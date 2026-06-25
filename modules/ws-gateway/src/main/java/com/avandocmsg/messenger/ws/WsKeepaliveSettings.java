package com.avandocmsg.messenger.ws;

/** WS protocol ping/pong keepalive (spec 025 FR-091 / FR-092). */
public record WsKeepaliveSettings(boolean enabled, long pingIntervalMs, long pongTimeoutMs) {

    /** Default: ping every 30s, evict after 90s without pong/activity. */
    public static WsKeepaliveSettings fromEnv() {
        return new WsKeepaliveSettings(
            parseEnabled(System.getenv("WS_KEEPALIVE_ENABLED"), true),
            secondsToMs(System.getenv("WS_PING_INTERVAL_SECONDS"), 30),
            secondsToMs(System.getenv("WS_PONG_TIMEOUT_SECONDS"), 90));
    }

    static boolean parseEnabled(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static long secondsToMs(String raw, int defaultSeconds) {
        if (raw == null || raw.isBlank()) {
            return Math.max(1L, defaultSeconds) * 1_000L;
        }
        try {
            return Math.max(1L, Long.parseLong(raw.trim())) * 1_000L;
        } catch (NumberFormatException e) {
            return Math.max(1L, defaultSeconds) * 1_000L;
        }
    }
}
