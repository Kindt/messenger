package com.avandocmsg.messenger.worker.push;

final class PushPlatformDefaults {

    private PushPlatformDefaults() {
    }

    /**
     * HTTP port for {@code GET /health}. Env {@code PUSH_METRICS_PORT}; default {@code 9191}; {@code 0} disables HTTP.
     */
    static int metricsPort() {
        var raw = System.getenv("PUSH_METRICS_PORT");
        if (raw == null || raw.isBlank()) {
            return 9191;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 9191;
        }
    }

    /** Env: {@code PUSH_DEVICE_QUERY_LIMIT}; default {@code 500}; clamped to {@code 1..5000}. */
    static int deviceQueryLimit() {
        var raw = System.getenv("PUSH_DEVICE_QUERY_LIMIT");
        if (raw == null || raw.isBlank()) {
            return 500;
        }
        try {
            return Math.max(1, Math.min(5000, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return 500;
        }
    }
}
