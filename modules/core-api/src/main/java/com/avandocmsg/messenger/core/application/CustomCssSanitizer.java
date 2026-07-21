package com.avandocmsg.messenger.core.application;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Removes dangerous CSS constructs and enforces payload size limit. */
public final class CustomCssSanitizer {
    private static final int MAX_BYTES = 32 * 1024;
    /** Possessive quantifier avoids catastrophic backtracking (S8786). */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?is)@import\\s++[^;]++;");
    private static final Pattern JAVASCRIPT_PATTERN = Pattern.compile("(?i)javascript\\s*+:");
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("(?i)expression\\s*+\\(");

    public String sanitize(String css) {
        if (css == null || css.isBlank()) {
            return null;
        }
        var sanitized = IMPORT_PATTERN.matcher(css).replaceAll("");
        sanitized = JAVASCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = EXPRESSION_PATTERN.matcher(sanitized).replaceAll("");
        if (sanitized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("custom_css exceeds 32KB");
        }
        return sanitized.trim();
    }
}
