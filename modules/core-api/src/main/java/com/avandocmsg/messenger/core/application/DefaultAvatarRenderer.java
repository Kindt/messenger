package com.avandocmsg.messenger.core.application;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Server-rendered default avatar PNG (initials). */
public final class DefaultAvatarRenderer {

    private static final int SIZE = 128;
    private static final Color BG = new Color(121, 73, 244);
    private static final Color FG = Color.WHITE;

    private DefaultAvatarRenderer() {
    }

    public static byte[] pngBytes(String displayName, String username) {
        var initials = initialsFor(displayName, username);
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillOval(0, 0, SIZE, SIZE);
            g.setColor(FG);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, initials.length() > 1 ? 48 : 56));
            var metrics = g.getFontMetrics();
            var x = (SIZE - metrics.stringWidth(initials)) / 2;
            var y = (SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(initials, x, y);
        } finally {
            g.dispose();
        }
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("default avatar render failed", e);
        }
    }

    static String initialsFor(String displayName, String username) {
        var source = displayName != null && !displayName.isBlank() ? displayName.trim() : username;
        if (source == null || source.isBlank()) {
            return "?";
        }
        var parts = source.split("\\s+");
        if (parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        var one = parts[0];
        if (one.length() >= 2) {
            return one.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return one.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
