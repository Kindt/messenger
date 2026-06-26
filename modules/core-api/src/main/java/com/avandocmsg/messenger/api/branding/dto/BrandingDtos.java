package com.avandocmsg.messenger.api.branding.dto;

import com.avandocmsg.messenger.core.port.UiBrandingPort;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public final class BrandingDtos {
    private BrandingDtos() {
    }

    public static BrandingResponse fromMerged(UiBrandingPort.MergedBranding row) {
        return new BrandingResponse(
            row.orgId(),
            row.palette(),
            row.tokenOverrides(),
            row.customCss(),
            row.brandTitle(),
            row.demoSkinsEnabled(),
            row.revision(),
            row.logoUrl()
        );
    }

    public static BrandingResponse fromPlatform(UiBrandingPort.PlatformBranding row) {
        return new BrandingResponse(
            null,
            row.palette(),
            row.tokenOverrides(),
            row.customCss(),
            row.brandTitle(),
            row.demoSkinsEnabled(),
            row.revision(),
            null
        );
    }

    public static BrandingResponse fromOrg(UiBrandingPort.OrgBranding row) {
        return new BrandingResponse(
            row.orgId(),
            row.palette(),
            row.tokenOverrides(),
            row.customCss(),
            row.brandTitle(),
            null,
            row.revision(),
            null
        );
    }

    public record BrandingResponse(
        @JsonProperty("org_id") UUID orgId,
        @JsonProperty("palette") String palette,
        @JsonProperty("token_overrides") Map<String, String> tokenOverrides,
        @JsonProperty("custom_css") String customCss,
        @JsonProperty("brand_title") String brandTitle,
        @JsonProperty("demo_skins_enabled") Boolean demoSkinsEnabled,
        @JsonProperty("revision") long revision,
        @JsonProperty("logo_url") String logoUrl
    ) {
    }

    public record PlatformBrandingUpsertRequest(
        @JsonProperty("palette") String palette,
        @JsonProperty("token_overrides") Map<String, String> tokenOverrides,
        @JsonProperty("custom_css") String customCss,
        @JsonProperty("brand_title") String brandTitle,
        @JsonProperty("demo_skins_enabled") Boolean demoSkinsEnabled
    ) {
    }

    public record OrgBrandingUpsertRequest(
        @JsonProperty("palette") String palette,
        @JsonProperty("token_overrides") Map<String, String> tokenOverrides,
        @JsonProperty("custom_css") String customCss,
        @JsonProperty("brand_title") String brandTitle
    ) {
    }
}
