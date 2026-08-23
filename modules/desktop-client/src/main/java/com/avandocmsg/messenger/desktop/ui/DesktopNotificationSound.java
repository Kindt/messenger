package com.avandocmsg.messenger.desktop.ui;

import javafx.scene.media.AudioClip;

/** ICQ/QIP-style incoming message sound. */
public final class DesktopNotificationSound {

    private static final String CHIME =
        "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAESsAACJWAAACABAAZGF0YQAAAAA="
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static AudioClip clip;

    private DesktopNotificationSound() {}

    public static void playIncoming() {
        try {
            if (clip == null) {
                clip = new AudioClip(CHIME);
            }
            clip.play();
        } catch (Exception ignored) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }
}
