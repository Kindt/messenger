package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrandingWebManifestBuilderTest {

    @Test
    void build_usesBrandTitleAndPaletteTheme() {
        var branding = new com.avandocmsg.messenger.core.port.UiBrandingPort.MergedBranding(
            null,
            "vtb",
            Map.of(),
            null,
            "VTB Messenger",
            true,
            2L,
            null
        );
        var manifest = BrandingWebManifestBuilder.build(branding);
        assertEquals("VTB Messenger", manifest.get("name"));
        assertEquals("VTB Messenge", manifest.get("short_name"));
        assertEquals("#0a2896", manifest.get("theme_color"));
    }

    @Test
    void resolveThemeColor_prefersTokenOverrides() {
        var color = BrandingWebManifestBuilder.resolveThemeColor(
            "korus",
            Map.of("--accent", "#112233")
        );
        assertEquals("#112233", color);
    }
}
