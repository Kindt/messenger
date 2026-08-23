package com.avandocmsg.messenger.desktop.ui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Native OS toast via AWT system tray (Windows Action Center / Linux notify). */
public final class DesktopOsNotifications {

    private static TrayIcon tray;
    private static Runnable onTrayClick = () -> {};

    private DesktopOsNotifications() {}

    public static boolean isSupported() {
        return !isDisabled() && SystemTray.isSupported();
    }

    public static synchronized void init(Runnable onClick) {
        if (!isSupported() || tray != null) {
            return;
        }
        onTrayClick = onClick == null ? () -> {} : onClick;
        SwingUtilities.invokeLater(() -> {
            try {
                if (!SystemTray.isSupported()) {
                    return;
                }
                var image = createIcon();
                var icon = new TrayIcon(image, "Korus Messenger");
                icon.setImageAutoSize(true);
                icon.addActionListener(e -> onTrayClick.run());
                SystemTray.getSystemTray().add(icon);
                tray = icon;
            } catch (AWTException ignored) {
                tray = null;
            }
        });
    }

    public static void show(String title, String body) {
        if (!isSupported() || tray == null) {
            return;
        }
        var safeTitle = title == null || title.isBlank() ? "Korus Messenger" : trim(title, 64);
        var safeBody = body == null || body.isBlank() ? "Новое сообщение" : trim(body, 256);
        var icon = tray;
        SwingUtilities.invokeLater(() -> icon.displayMessage(safeTitle, safeBody, TrayIcon.MessageType.INFO));
    }

    public static synchronized void dispose() {
        if (tray == null) {
            return;
        }
        var icon = tray;
        tray = null;
        SwingUtilities.invokeLater(() -> {
            try {
                SystemTray.getSystemTray().remove(icon);
            } catch (Exception ignored) {
                // tray already removed
            }
        });
    }

    private static boolean isDisabled() {
        return "false".equalsIgnoreCase(System.getProperty("korus.desktop.os.notifications"));
    }

    private static String trim(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private static Image createIcon() {
        int size = 16;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x1a, 0x6b, 0xff));
        g.fillOval(1, 1, size - 2, size - 2);
        g.setColor(Color.WHITE);
        g.fillOval(5, 5, 6, 6);
        g.dispose();
        return image;
    }
}
