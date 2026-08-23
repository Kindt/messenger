package com.avandocmsg.messenger.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DesktopThemeApplierTest {

    @Test
    void resolveDarkAndLight() {
        assertEquals("dark", DesktopThemeApplier.resolve("dark"));
        assertEquals("light", DesktopThemeApplier.resolve("light"));
    }

    @Test
    void resolveSystemUsesOverrideProperty() {
        var key = "korus.desktop.theme.dark";
        var prev = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            assertEquals("dark", DesktopThemeApplier.resolve("system"));
            System.setProperty(key, "false");
            assertEquals("light", DesktopThemeApplier.resolve("system"));
        } finally {
            if (prev == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, prev);
            }
        }
    }
}
