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
}
