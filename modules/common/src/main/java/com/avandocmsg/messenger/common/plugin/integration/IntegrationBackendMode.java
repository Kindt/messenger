package com.avandocmsg.messenger.common.plugin.integration;

/**
 * Spec 014: mock vs live backend resolution for integrations workers.
 */
public enum IntegrationBackendMode {
    MOCK,
    LIVE,
    AUTO;

    public static IntegrationBackendMode fromEnv(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase()) {
            case "mock" -> MOCK;
            case "live" -> LIVE;
            default -> AUTO;
        };
    }
}
