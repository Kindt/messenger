package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.port.UiBrandingPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a PWA web manifest JSON object from merged UI branding (spec 027).
 */
public final class BrandingWebManifestBuilder {
    private static final String DEFAULT_NAME = "Korus Messenger";
    private static final String DEFAULT_SHORT = "Korus";
    private static final String DEFAULT_THEME = "#7949f4";
    private static final String DEFAULT_BG = "#0c0b10";

    private static final Map<String, String> PALETTE_THEME = Map.of(
        "korus", "#7949f4",
        "vtb", "#0a2896",
        "alfa", "#ef3124",
        "rzd", "#e4002b",
        "sfr", "#005bbb",
        "sberbank", "#21a038"
    );

    private BrandingWebManifestBuilder() {
    }

    public static Map<String, Object> build(UiBrandingPort.MergedBranding branding) {
        var name = resolveName(branding.brandTitle());
        var shortName = resolveShortName(name);
        var themeColor = resolveThemeColor(branding.palette(), branding.tokenOverrides());
        var backgroundColor = resolveBackground(branding.tokenOverrides());

        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("name", name);
        manifest.put("short_name", shortName);
        manifest.put("description", name);
        manifest.put("start_url", "/");
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("orientation", "any");
        manifest.put("lang", "ru");
        manifest.put("background_color", backgroundColor);
        manifest.put("theme_color", themeColor);
        manifest.put("icons", List.of(Map.of(
            "src", "/icon.svg",
            "sizes", "any",
            "type", "image/svg+xml",
            "purpose", "any maskable"
        )));
        return manifest;
    }

    static String resolveName(String brandTitle) {
        if (brandTitle != null && !brandTitle.isBlank()) {
            return brandTitle.trim();
        }
        return DEFAULT_NAME;
    }

    static String resolveShortName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_SHORT;
        }
        var trimmed = name.trim();
        if (trimmed.length() <= 12) {
            return trimmed;
        }
        return trimmed.substring(0, 12).trim();
    }

    static String resolveThemeColor(String palette, Map<String, String> tokenOverrides) {
        var fromToken = tokenValue(tokenOverrides, "--theme-color-meta");
        if (fromToken != null) {
            return fromToken;
        }
        fromToken = tokenValue(tokenOverrides, "--accent");
        if (fromToken != null) {
            return fromToken;
        }
        if (palette != null && !palette.isBlank()) {
            var mapped = PALETTE_THEME.get(palette.trim().toLowerCase(Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
        }
        return DEFAULT_THEME;
    }

    static String resolveBackground(Map<String, String> tokenOverrides) {
        var fromToken = tokenValue(tokenOverrides, "--bg");
        return fromToken != null ? fromToken : DEFAULT_BG;
    }

    private static String tokenValue(Map<String, String> tokenOverrides, String key) {
        if (tokenOverrides == null || tokenOverrides.isEmpty()) {
            return null;
        }
        var value = tokenOverrides.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
