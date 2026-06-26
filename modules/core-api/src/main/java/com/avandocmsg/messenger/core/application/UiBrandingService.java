package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.port.UiBrandingPort;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UiBrandingService {
    private static final Set<String> ALLOWED_PALETTES = Set.of("korus", "vtb", "alfa", "rzd", "sfr", "sberbank");

    private final UiBrandingPort brandingPort;
    private final CustomCssSanitizer customCssSanitizer;

    public UiBrandingService(UiBrandingPort brandingPort, CustomCssSanitizer customCssSanitizer) {
        this.brandingPort = brandingPort;
        this.customCssSanitizer = customCssSanitizer;
    }

    public UiBrandingPort.MergedBranding getPublicBranding() {
        return brandingPort.mergeForOrg(null, null);
    }

    public UiBrandingPort.MergedBranding getForOrg(UUID orgId, String logoUrl) {
        return brandingPort.mergeForOrg(orgId, logoUrl);
    }

    public UiBrandingPort.PlatformBranding getPlatform() {
        return brandingPort.getPlatform().orElseGet(() ->
            brandingPort.upsertPlatform("korus", Map.of(), null, null, false));
    }

    public UiBrandingPort.PlatformBranding upsertPlatform(
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled
    ) {
        return brandingPort.upsertPlatform(
            validatePalette(palette),
            normalizeMap(tokenOverrides),
            customCssSanitizer.sanitize(customCss),
            normalizeTitle(brandTitle),
            demoSkinsEnabled
        );
    }

    public UiBrandingPort.OrgBranding getOrg(UUID orgId) {
        return brandingPort.getOrg(orgId).orElseGet(() ->
            new UiBrandingPort.OrgBranding(orgId, null, Map.of(), null, null, 0, null, null));
    }

    public UiBrandingPort.OrgBranding upsertOrg(
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle
    ) {
        return brandingPort.upsertOrg(
            orgId,
            palette != null ? validatePalette(palette) : null,
            normalizeMap(tokenOverrides),
            customCssSanitizer.sanitize(customCss),
            normalizeTitle(brandTitle)
        );
    }

    public boolean deleteOrg(UUID orgId) {
        return brandingPort.deleteOrg(orgId);
    }

    private static String validatePalette(String palette) {
        if (palette == null || palette.isBlank()) {
            throw new IllegalArgumentException("palette is required");
        }
        var normalized = palette.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PALETTES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported palette");
        }
        return normalized;
    }

    private static Map<String, String> normalizeMap(Map<String, String> tokenOverrides) {
        if (tokenOverrides == null || tokenOverrides.isEmpty()) {
            return Map.of();
        }
        var out = new java.util.LinkedHashMap<String, String>();
        for (var entry : tokenOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            out.put(entry.getKey().trim(), entry.getValue() != null ? entry.getValue().trim() : "");
        }
        return out;
    }

    private static String normalizeTitle(String brandTitle) {
        if (brandTitle == null) {
            return null;
        }
        var trimmed = brandTitle.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
