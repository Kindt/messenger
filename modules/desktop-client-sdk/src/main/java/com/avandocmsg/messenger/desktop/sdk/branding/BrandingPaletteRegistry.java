package com.avandocmsg.messenger.desktop.sdk.branding;

import com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot;
import java.util.Map;

/** JavaFX palette tokens aligned with web themes-palettes.css (spec 027). */
public final class BrandingPaletteRegistry {

    private BrandingPaletteRegistry() {}

    public record PaletteTokens(
        String accent,
        String accentHover,
        String background,
        String panel,
        String text,
        String muted,
        String border
    ) {}

    private static final Map<String, PaletteTokens> DARK = Map.of(
        "korus", new PaletteTokens("#7949f4", "#9568ff", "#0c0b10", "#16151c", "#f4f3f7", "#8e8e8e", "#2e2c38"),
        "vtb", new PaletteTokens("#0066b3", "#1a7fcc", "#0a1628", "#12233d", "#eef4fb", "#8aa4c4", "#2a4060"),
        "alfa", new PaletteTokens("#ef3124", "#ff4d42", "#120808", "#1e1010", "#fff5f4", "#b08888", "#3d2828"),
        "rzd", new PaletteTokens("#e21b24", "#ff3b44", "#140a0a", "#221010", "#fff0f0", "#b09090", "#402828"),
        "sfr", new PaletteTokens("#0072bc", "#1a8ad4", "#081018", "#101c28", "#eef6fc", "#88a8c4", "#283848"),
        "sberbank", new PaletteTokens("#21a038", "#2ec04a", "#081208", "#102018", "#f0faf2", "#88b090", "#284030")
    );

    public static PaletteTokens resolve(BrandingSnapshot branding, boolean dark) {
        var palette = branding == null || branding.palette() == null ? "korus" : branding.palette().toLowerCase();
        var base = DARK.getOrDefault(palette, DARK.get("korus"));
        if (branding == null || branding.tokenOverrides() == null || branding.tokenOverrides().isEmpty()) {
            return base;
        }
        var o = branding.tokenOverrides();
        return new PaletteTokens(
            o.getOrDefault("--accent", base.accent()),
            o.getOrDefault("--accent-hover", base.accentHover()),
            o.getOrDefault("--bg", base.background()),
            o.getOrDefault("--panel", base.panel()),
            o.getOrDefault("--text", base.text()),
            o.getOrDefault("--muted", base.muted()),
            o.getOrDefault("--border", base.border())
        );
    }
}
