package com.avandocmsg.messenger.desktop.sdk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** OS paths for Korus desktop data (aligned with mobile SDK layout). */
public final class DesktopPaths {

    private DesktopPaths() {}

    public static Path appRoot() {
        var override = System.getProperty("korus.desktop.data.dir");
        if (override == null || override.isBlank()) {
            override = System.getenv("KORUS_DESKTOP_DATA_DIR");
        }
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            var appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, "KorusMessenger");
            }
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "KorusMessenger");
        }
        return Paths.get(System.getProperty("user.home"), ".local", "share", "korus-messenger");
    }

    public static Path downloadsRoot() {
        var home = Paths.get(System.getProperty("user.home"));
        var winDownloads = home.resolve("Downloads");
        if (Files.isDirectory(winDownloads)) {
            return winDownloads;
        }
        return home;
    }
}
