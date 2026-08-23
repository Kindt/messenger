package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

/** Mirrors GET /api/v1/branding (spec 027). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrandingSnapshot(
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
    public static BrandingSnapshot korusDefault() {
        return new BrandingSnapshot(
            null, "korus", Map.of(), null, "Korus Messenger", true,
            "default", "centered", "default", 1L, null
        );
    }
}
