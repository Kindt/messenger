package com.avandocmsg.messenger.common.plugin.integration;

import java.util.Locale;

public final class IntegrationEnv {
    private IntegrationEnv() {}

    public static IntegrationBackendMode backendMode() {
        return IntegrationBackendMode.fromEnv(System.getenv("INTEGRATIONS_BACKEND_MODE"));
    }

    public static String mockApiBase() {
        var base = System.getenv("MOCK_API_BASE");
        if (base == null || base.isBlank()) {
            return "http://mock-apis:8080";
        }
        return trimSlash(base);
    }

    public static boolean useMock(boolean liveConfigured) {
        return switch (backendMode()) {
            case MOCK -> true;
            case LIVE -> false;
            case AUTO -> !liveConfigured;
        };
    }

    public static String getenv(String key) {
        var v = System.getenv(key);
        return v != null ? v.trim() : "";
    }

    public static boolean isSet(String key) {
        return !getenv(key).isBlank();
    }

    public static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String llmModeFromSnapshot(java.util.Map<String, Object> snapshot) {
        if (snapshot == null) {
            return "on_prem_only";
        }
        Object mode = snapshot.get("org_llm_mode");
        if (mode == null) {
            return "on_prem_only";
        }
        return mode.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean cloudLlmAllowed(java.util.Map<String, Object> snapshot) {
        var mode = llmModeFromSnapshot(snapshot);
        return "cloud_allowed".equals(mode) || "hybrid".equals(mode);
    }

    public static boolean ocrOnPremOnly(java.util.Map<String, Object> snapshot) {
        if (snapshot == null) {
            return true;
        }
        Object v = snapshot.get("ocr_on_prem_only");
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString());
        }
        return true;
    }
}
