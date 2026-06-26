package com.avandocmsg.messenger.core.application;

import java.util.Locale;
import java.util.Set;

/** Shell layout enum helpers (spec 028). */
public final class ShellLayout {
    public static final String DEFAULT = "default";
    public static final String COMPACT = "compact";
    public static final String AUTH_SPLIT = "auth-split";

    private static final Set<String> ALLOWED = Set.of(DEFAULT, COMPACT, AUTH_SPLIT);

    private ShellLayout() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static String validateRequired(String value) {
        var normalized = normalize(value);
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("unsupported shell_layout");
        }
        return normalized;
    }

    public static String validateOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateRequired(value);
    }

    public static String authLayout(String shellLayout) {
        return AUTH_SPLIT.equals(normalize(shellLayout)) ? AUTH_SPLIT : DEFAULT;
    }

    public static String postLoginLayout(String shellLayout) {
        var normalized = normalize(shellLayout);
        if (AUTH_SPLIT.equals(normalized)) {
            return DEFAULT;
        }
        if (COMPACT.equals(normalized)) {
            return COMPACT;
        }
        return DEFAULT;
    }
}
