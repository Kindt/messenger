package com.avandocmsg.messenger.desktop.sdk.update;

/** Desktop client version + platform id for update manifests. */
public final class DesktopVersions {

    public static final String CURRENT = "0.0.1";

    private DesktopVersions() {}

    public static String platformKey() {
        var os = System.getProperty("os.name", "").toLowerCase();
        var arch = System.getProperty("os.arch", "amd64").toLowerCase();
        if (os.contains("win")) {
            return "windows-x64";
        }
        if (os.contains("mac")) {
            return arch.contains("aarch") || arch.contains("arm") ? "macos-aarch64" : "macos-x64";
        }
        return "linux-x64";
    }
}
