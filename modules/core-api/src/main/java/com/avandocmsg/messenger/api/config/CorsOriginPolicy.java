package com.avandocmsg.messenger.api.config;

import java.util.Arrays;
import java.util.List;

/** Parses {@code cors.allowed.origins} and resolves {@code Access-Control-Allow-Origin} for a request. */
public final class CorsOriginPolicy {

    private CorsOriginPolicy() {
    }

    public static List<String> parseOriginsList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("*");
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /**
     * @return {@code "*"} if wildcard is allowed; echoed origin if it matches the allow-list;
     *         {@code null} if the list is strict and the browser origin is missing or not allowed (no CORS headers).
     */
    public static String resolveAllowOrigin(List<String> allowed, String requestOriginHeader) {
        if (allowed.isEmpty() || allowed.stream().anyMatch("*"::equals)) {
            return "*";
        }
        if (requestOriginHeader == null || requestOriginHeader.isBlank()) {
            return null;
        }
        var o = requestOriginHeader.trim();
        return allowed.stream().anyMatch(a -> a.equalsIgnoreCase(o)) ? o : null;
    }
}
