package com.avandocmsg.messenger.desktop.sdk.update;

import java.util.regex.Pattern;

/** Semver-ish compare for desktop update manifests (major.minor.patch). */
public final class VersionComparer {

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");

    private VersionComparer() {}

    public static boolean isNewer(String candidate, String current) {
        var c = parse(candidate);
        var cur = parse(current);
        if (c == null || cur == null) {
            return false;
        }
        if (c[0] != cur[0]) {
            return c[0] > cur[0];
        }
        if (c[1] != cur[1]) {
            return c[1] > cur[1];
        }
        return c[2] > cur[2];
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        var m = VERSION.matcher(version.trim());
        if (!m.find()) {
            return null;
        }
        return new int[] {
            Integer.parseInt(m.group(1)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3))
        };
    }
}
