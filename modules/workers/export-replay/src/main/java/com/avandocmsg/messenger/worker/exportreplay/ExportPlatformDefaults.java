package com.avandocmsg.messenger.worker.exportreplay;

/** Platform retention defaults (same env keys as core-api {@code AppConfig}). */
record ExportPlatformDefaults(
    Integer hotBodyMaxAgeDays,
    Integer hotMetadataMinAgeDays,
    boolean archiveMetadataEnabled,
    boolean deepArchiveEnabled,
    boolean legalHold
) {
    static ExportPlatformDefaults fromEnv() {
        return new ExportPlatformDefaults(
            parseIntOrNull(System.getenv("RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS")),
            parseIntOrNull(System.getenv("RETENTION_DEFAULT_HOT_METADATA_MIN_AGE_DAYS")),
            parseBool(System.getenv("RETENTION_DEFAULT_ARCHIVE_METADATA_ENABLED"), true),
            parseBool(System.getenv("RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED"), true),
            parseBool(System.getenv("RETENTION_DEFAULT_LEGAL_HOLD"), false)
        );
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean parseBool(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    /** Env {@code EXPORT_REPLAY_METRICS_PORT}: {@code 0} disables HTTP metrics. */
    static int metricsPortFromEnv() {
        var raw = System.getenv("EXPORT_REPLAY_METRICS_PORT");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            var port = Integer.parseInt(raw.trim());
            if (port < 0 || port > 65535) {
                return 0;
            }
            return port;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
