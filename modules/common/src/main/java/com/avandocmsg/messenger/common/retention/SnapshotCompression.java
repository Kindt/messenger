package com.avandocmsg.messenger.common.retention;

/**
 * Deep-archive / retention snapshot part compression (spec 006 stage 6).
 */
public enum SnapshotCompression {
    NONE,
    GZIP,
    ZSTD;

    public static SnapshotCompression fromEnv() {
        var raw = System.getenv("DEEP_ARCHIVE_COMPRESSION");
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "gzip" -> GZIP;
            case "zstd" -> ZSTD;
            default -> NONE;
        };
    }

    public static int zstdLevelFromEnv() {
        var raw = System.getenv("DEEP_ARCHIVE_ZSTD_LEVEL");
        if (raw == null || raw.isBlank()) {
            return 3;
        }
        try {
            return Math.clamp(Integer.parseInt(raw.trim()), 1, 22);
        } catch (NumberFormatException e) {
            return 3;
        }
    }
}
