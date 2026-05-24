package com.avandocmsg.messenger.ws;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Parses {@code WS_ALLOWED_ORIGINS} (comma-separated, {@code *} = allow all). */
final class WsOriginPolicy {

    private WsOriginPolicy() {
    }

    static List<String> parseAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("*");
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    static boolean isAllowed(String origin, List<String> allowed) {
        if (allowed.contains("*")) {
            return true;
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        var o = origin.trim();
        for (var a : allowed) {
            if (a.equalsIgnoreCase(o)) {
                return true;
            }
        }
        return false;
    }
}
