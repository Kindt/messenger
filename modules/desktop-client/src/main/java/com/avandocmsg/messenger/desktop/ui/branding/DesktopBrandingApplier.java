package com.avandocmsg.messenger.desktop.ui.branding;

import com.avandocmsg.messenger.desktop.sdk.branding.BrandingPaletteRegistry;
import com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot;
import javafx.scene.Scene;

/** Applies spec 027 branding tokens to JavaFX scene (offline-capable). */
public final class DesktopBrandingApplier {

    private DesktopBrandingApplier() {}

    public static void apply(Scene scene, BrandingSnapshot branding, boolean dark) {
        if (scene == null) {
            return;
        }
        var snap = branding == null ? BrandingSnapshot.korusDefault() : branding;
        var tokens = BrandingPaletteRegistry.resolve(snap, dark);
        var root = scene.getRoot();
        if (root == null) {
            return;
        }
        root.setStyle(String.format(
            "-fx-base: %s; -fx-background: %s; -fx-control-inner-background: %s; "
                + "-fx-text-fill: %s; -fx-accent: %s;",
            tokens.panel(),
            tokens.background(),
            tokens.panel(),
            tokens.text(),
            tokens.accent()
        ));
        if (snap.brandTitle() != null && !snap.brandTitle().isBlank()) {
            var window = scene.getWindow();
            if (window instanceof javafx.stage.Stage stage) {
                stage.setTitle(snap.brandTitle() + " — Desktop");
            }
        }
    }
}
