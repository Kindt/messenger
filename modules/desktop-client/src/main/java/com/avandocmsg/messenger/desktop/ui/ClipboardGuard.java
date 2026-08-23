package com.avandocmsg.messenger.desktop.ui;

import javafx.animation.PauseTransition;
import javafx.scene.input.Clipboard;
import javafx.util.Duration;

public final class ClipboardGuard {

    private ClipboardGuard() {}

    public static void scheduleClear(boolean enabled, int seconds) {
        if (!enabled || seconds <= 0) {
            return;
        }
        var pause = new PauseTransition(Duration.seconds(seconds));
        pause.setOnFinished(e -> {
            try {
                var clip = Clipboard.getSystemClipboard();
                if (clip != null && clip.getString() != null) {
                    clip.clear();
                }
            } catch (Exception ignored) {
                // best-effort
            }
        });
        pause.play();
    }
}
