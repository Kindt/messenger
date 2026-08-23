package com.avandocmsg.messenger.desktop.ui.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Isolated JVM properties for TestFX UI runs (no pollution of user APPDATA). */
public final class DesktopUiTestSupport {

    private static Path dataDir;

    private DesktopUiTestSupport() {}

    public static synchronized void init() {
        if (dataDir != null) {
            return;
        }
        try {
            dataDir = Files.createTempDirectory("korus-desktop-ui-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        applyBaseProps();
    }

    public static synchronized void resetForTestClass() {
        try {
            if (dataDir != null) {
                java.nio.file.Files.walk(dataDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            java.nio.file.Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
            }
            dataDir = Files.createTempDirectory("korus-desktop-ui-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        applyBaseProps();
    }

    private static void applyBaseProps() {
        System.setProperty("korus.desktop.data.dir", dataDir.toString());
        System.setProperty("korus.desktop.demo", "true");
        System.setProperty("korus.desktop.undecorated", "false");
        System.setProperty("korus.desktop.session.lock", "false");
        System.setProperty("korus.desktop.os.notifications", "false");
    }

    public static void autostartDemoShell() {
        resetForTestClass();
        System.setProperty("korus.desktop.autostart", "demo");
    }

    public static void autostartProfilePicker() {
        resetForTestClass();
        System.clearProperty("korus.desktop.autostart");
    }

    public static Path dataDir() {
        init();
        return dataDir;
    }

    public static void setTestAttachFile(Path file) {
        init();
        System.setProperty("korus.desktop.test.attach.file", file.toString());
    }

    public static void clearTestAttachFile() {
        System.clearProperty("korus.desktop.test.attach.file");
    }
}
