package com.avandocmsg.messenger.core.application;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** On-the-fly image resize (embedded file-proxy mode; JPEG output). */
public final class ImageResizeService {

    private static final Set<String> RESIZABLE_MIME = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", "image/webp");
    private static final long DEFAULT_MAX_SOURCE_BYTES = 15L * 1024 * 1024;

    private ImageResizeService() {
    }

    public static boolean isResizableMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        var normalized = mimeType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return RESIZABLE_MIME.contains(normalized);
    }

    public static Optional<byte[]> resizeToJpeg(InputStream source, int targetWidth, int targetHeight,
                                                long maxSourcePixels) throws IOException {
        return resizeToJpeg(source, targetWidth, targetHeight, maxSourcePixels, DEFAULT_MAX_SOURCE_BYTES);
    }

    public static Optional<byte[]> resizeToJpeg(InputStream source, int targetWidth, int targetHeight,
                                                long maxSourcePixels, long maxSourceBytes) throws IOException {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return Optional.empty();
        }
        var bytes = readBounded(source, maxSourceBytes);
        if (bytes.length == 0) {
            return Optional.empty();
        }
        try (var in = new ByteArrayInputStream(bytes)) {
            var original = ImageIO.read(in);
            if (original == null) {
                return Optional.empty();
            }
            long pixels = (long) original.getWidth() * original.getHeight();
            if (pixels > maxSourcePixels) {
                return Optional.empty();
            }
            var scaled = scaleToFit(original, targetWidth, targetHeight);
            var rgb = toRgb(scaled);
            var out = new ByteArrayOutputStream(Math.max(4096, bytes.length / 4));
            if (!ImageIO.write(rgb, "jpg", out)) {
                return Optional.empty();
            }
            return Optional.of(out.toByteArray());
        }
    }

    private static BufferedImage scaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= maxWidth && srcH <= maxHeight) {
            return source;
        }
        double scale = Math.min((double) maxWidth / srcW, (double) maxHeight / srcH);
        int dstW = Math.max(1, (int) Math.round(srcW * scale));
        int dstH = Math.max(1, (int) Math.round(srcH * scale));
        var scaled = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source.getScaledInstance(dstW, dstH, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return scaled;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        var rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] readBounded(InputStream source, long maxBytes) throws IOException {
        var buf = new byte[65536];
        var out = new ByteArrayOutputStream(Math.min(4096, (int) Math.min(maxBytes, Integer.MAX_VALUE)));
        long total = 0;
        int n;
        while ((n = source.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                return new byte[0];
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
