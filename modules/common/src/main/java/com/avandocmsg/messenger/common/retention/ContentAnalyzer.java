package com.avandocmsg.messenger.common.retention;

import java.util.Optional;
import java.util.UUID;

public class ContentAnalyzer {

    private static final String FILE_REF_PREFIX = "file://";

    private ContentAnalyzer() {
    }

    /**
     * Returns true if the content is a file reference (starts with {@code file://}).
     */
    public static boolean isFileReference(String content) {
        return content != null && content.startsWith(FILE_REF_PREFIX);
    }

    /**
     * Extracts the file UUID from a file reference string.
     *
     * @return Optional containing the file UUID, or empty if not a valid file reference
     */
    public static Optional<UUID> extractFileId(String content) {
        if (content == null || !content.startsWith(FILE_REF_PREFIX)) {
            return Optional.empty();
        }
        var idPart = content.substring(FILE_REF_PREFIX.length()).trim();
        if (idPart.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(idPart));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
