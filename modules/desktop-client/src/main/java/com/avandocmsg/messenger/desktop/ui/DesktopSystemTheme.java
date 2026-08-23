package com.avandocmsg.messenger.desktop.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Best-effort OS dark-mode probe for profile theme=system. */
final class DesktopSystemTheme {

    private DesktopSystemTheme() {}

    static boolean isDarkPreferred() {
        if ("1".equals(System.getenv("KORUS_DESKTOP_DARK"))
            || "true".equalsIgnoreCase(System.getProperty("korus.desktop.theme.dark"))) {
            return true;
        }
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return windowsAppsUseDarkTheme();
        }
        if (os.contains("mac")) {
            return macPrefersDark();
        }
        return false;
    }

    private static boolean windowsAppsUseDarkTheme() {
        try {
            var proc = startSanitized(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme"
            );
            if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return false;
            }
            try (var reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains("AppsUseLightTheme")) {
                        continue;
                    }
                    var parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        return "0x0".equalsIgnoreCase(parts[parts.length - 1]) || "0".equals(parts[parts.length - 1]);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static boolean macPrefersDark() {
        try {
            var proc = startSanitized("defaults", "read", "-g", "AppleInterfaceStyle");
            if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return false;
            }
            try (var reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                var value = reader.readLine();
                return value != null && value.trim().equalsIgnoreCase("Dark");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static Process startSanitized(String... command) throws java.io.IOException {
        var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().put("PATH", fixedPathForOs());
        return pb.start();
    }

    /** Fixed PATH for subprocess probes (Sonar java:S4036). */
    private static String fixedPathForOs() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "C:\\Windows\\System32;C:\\Windows";
        }
        if (os.contains("mac")) {
            return "/usr/bin:/bin:/usr/sbin:/sbin";
        }
        return "/usr/local/bin:/usr/bin:/bin";
    }
}
