package com.avandocmsg.messenger.desktop.sdk.conference;

import java.awt.Desktop;
import java.net.URI;

/** Opens mesh call join URL in the system browser (web UI WebRTC). */
public final class CallLauncher {

    private CallLauncher() {}

    public static void openJoinUrl(String joinUrl) throws Exception {
        if (joinUrl == null || joinUrl.isBlank()) {
            throw new IllegalArgumentException("empty join_url");
        }
        if (!Desktop.isDesktopSupported()) {
            throw new IllegalStateException("Desktop browse not supported: " + joinUrl);
        }
        Desktop.getDesktop().browse(URI.create(joinUrl));
    }
}
