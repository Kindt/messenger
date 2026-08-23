package com.avandocmsg.messenger.desktop.ui;

import javafx.scene.Scene;

/** Applies profile theme (light / dark / system) to scene root. */
public final class DesktopThemeApplier {

    private DesktopThemeApplier() {}

    public static void apply(Scene scene, String theme) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        var root = scene.getRoot();
        root.getStyleClass().removeIf(c -> c.startsWith("theme-"));
        root.getStyleClass().add("theme-" + resolve(theme));
    }

    static String resolve(String theme) {
        if (theme == null || theme.isBlank() || "system".equalsIgnoreCase(theme)) {
            return DesktopSystemTheme.isDarkPreferred() ? "dark" : "light";
        }
        return "dark".equalsIgnoreCase(theme) ? "dark" : "light";
    }
}
