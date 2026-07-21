package com.avandocmsg.messenger.common.export;

import java.util.Optional;

/**
 * {@code output_path} convention for export jobs: {@code minio:exports/{jobId}.export.json}.
 */
public final class ExportOutputRef {

    public static final String MINIO_PREFIX = "minio:";
    public static final String EXPORT_ZIP_SUFFIX = ".export.zip";
    public static final String EXPORT_JSON_SUFFIX = ".export.json";

    /** Entry inside {@code .export.zip} bundles. */
    public static final String ZIP_JSON_ENTRY = "export.json";

    /** Attachment index inside zip bundles ({@code EXPORT_REPLAY_INCLUDE_FILE_BODIES}). */
    public static final String ZIP_ATTACHMENTS_MANIFEST = "attachments/manifest.json";

    /** Root-level export completeness summary inside {@code .export.zip}. */
    public static final String ZIP_PACKAGE_MANIFEST = "package-manifest.json";

    private ExportOutputRef() {
    }

    public static String safeJobIdForFilename(String jobId) {
        return jobId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static String objectKey(String jobId) {
        return "exports/" + safeJobIdForFilename(jobId) + EXPORT_JSON_SUFFIX;
    }

    public static String zipObjectKey(String jobId) {
        return "exports/" + safeJobIdForFilename(jobId) + EXPORT_ZIP_SUFFIX;
    }

    public static boolean isZipBundlePath(String outputPath) {
        return outputPath != null && outputPath.endsWith(EXPORT_ZIP_SUFFIX);
    }

    public static String downloadFileName(String jobId, String outputPath) {
        if (isZipBundlePath(outputPath)) {
            return safeJobIdForFilename(jobId) + EXPORT_ZIP_SUFFIX;
        }
        return safeJobIdForFilename(jobId) + EXPORT_JSON_SUFFIX;
    }

    public static String minioStoredPath(String objectKey) {
        return MINIO_PREFIX + objectKey;
    }

    public static Optional<String> parseMinioObjectKey(String outputPath) {
        if (outputPath == null || outputPath.isBlank() || !outputPath.startsWith(MINIO_PREFIX)) {
            return Optional.empty();
        }
        var key = outputPath.substring(MINIO_PREFIX.length()).trim();
        if (key.isEmpty() || key.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    public static String outputStorage(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return null;
        }
        return outputPath.startsWith(MINIO_PREFIX) ? "minio" : "filesystem";
    }

    /** {@code zip} when path ends with {@code .export.zip}, else {@code json}. */
    public static String outputFormat(String outputPath) {
        return isZipBundlePath(outputPath) ? "zip" : "json";
    }
}
