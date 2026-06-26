package com.avandocmsg.messenger.api.branding.dto;

import com.avandocmsg.messenger.core.application.ShellLayout;
import com.avandocmsg.messenger.core.port.UiBrandingPort;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public final class BrandingDtos {
    private BrandingDtos() {
    }

    public static BrandingResponse fromMerged(UiBrandingPort.MergedBranding row) {
        return toResponse(
            row.orgId(),
            row.palette(),
            row.tokenOverrides(),
            row.customCss(),
            row.brandTitle(),
            row.demoSkinsEnabled(),
            row.shellLayout(),
            row.revision(),
            row.logoUrl()
        );
    }

    public static BrandingResponse fromPlatform(UiBrandingPort.PlatformBranding row) {
        return toResponse(
            null,
            row.palette(),
            row.tokenOverrides(),
            row.customCss(),
            row.brandTitle(),
            row.demoSkinsEnabled(),
            row.shellLayout(),
            row.revision(),
            null
        );
    }

    public static BrandingResponse fromOrg(UiBrandingPort.OrgBranding row) {
        return toResponse(
            row.orgId(),
            row.palette(),
            row.tokenOverrides(),
            row.customCss(),
            row.brandTitle(),
            null,
            row.shellLayout(),
            row.revision(),
            null
        );
    }

    private static BrandingResponse toResponse(
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        Boolean demoSkinsEnabled,
        String shellLayout,
        long revision,
        String logoUrl
    ) {
        var resolved = ShellLayout.normalize(shellLayout);
        return new BrandingResponse(
            orgId,
            palette,
            tokenOverrides,
            customCss,
            brandTitle,
            demoSkinsEnabled,
            resolved,
            ShellLayout.authLayout(resolved),
            ShellLayout.postLoginLayout(resolved),
            revision,
            logoUrl
        );
    }

    public record BrandingResponse(
        @JsonProperty("org_id") UUID orgId,
        @JsonProperty("palette") String palette,
        @JsonProperty("token_overrides") Map<String, String> tokenOverrides,
        @JsonProperty("custom_css") String customCss,
        @JsonProperty("brand_title") String brandTitle,
        @JsonProperty("demo_skins_enabled") Boolean demoSkinsEnabled,
        @JsonProperty("shell_layout") String shellLayout,
        @JsonProperty("auth_layout") String authLayout,
        @JsonProperty("post_login_layout") String postLoginLayout,
        @JsonProperty("revision") long revision,
        @JsonProperty("logo_url") String logoUrl
    ) {
    }

    public record PlatformBrandingUpsertRequest(
        @JsonProperty("palette") String palette,
        @JsonProperty("token_overrides") Map<String, String> tokenOverrides,
        @JsonProperty("custom_css") String customCss,
        @JsonProperty("brand_title") String brandTitle,
        @JsonProperty("demo_skins_enabled") Boolean demoSkinsEnabled,
        @JsonProperty("shell_layout") String shellLayout
    ) {
    }

    public record OrgBrandingUpsertRequest(
        @JsonProperty("palette") String palette,
        @JsonProperty("token_overrides") Map<String, String> tokenOverrides,
        @JsonProperty("custom_css") String customCss,
        @JsonProperty("brand_title") String brandTitle,
        @JsonProperty("shell_layout") String shellLayout
    ) {
    }
}
