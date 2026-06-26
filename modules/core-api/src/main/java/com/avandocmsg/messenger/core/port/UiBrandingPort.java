package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Platform/org branding configuration persistence. */
public interface UiBrandingPort {
    Optional<PlatformBranding> getPlatform();

    PlatformBranding upsertPlatform(
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled,
        String shellLayout
    );

    Optional<OrgBranding> getOrg(UUID orgId);

    OrgBranding upsertOrg(
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        String shellLayout
    );

    boolean deleteOrg(UUID orgId);

    MergedBranding mergeForOrg(UUID orgId, String logoUrl);

    record PlatformBranding(
        long id,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled,
        String shellLayout,
        long revision,
        Instant createdAt,
        Instant updatedAt
    ) {}

    record OrgBranding(
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        String shellLayout,
        long revision,
        Instant createdAt,
        Instant updatedAt
    ) {}

    record MergedBranding(
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled,
        String shellLayout,
        long revision,
        String logoUrl
    ) {}
}
