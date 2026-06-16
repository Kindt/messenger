package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageResizeServiceTest {

    @Test
    void resizeToJpeg_scalesDownLargePng() throws Exception {
        var png = pngBytes(128, 96, Color.BLUE);
        try (var in = new ByteArrayInputStream(png)) {
            var resized = ImageResizeService.resizeToJpeg(in, 32, 32, 25_000_000);
            assertTrue(resized.isPresent());
            var img = ImageIO.read(new ByteArrayInputStream(resized.get()));
            assertTrue(img.getWidth() <= 32);
            assertTrue(img.getHeight() <= 32);
        }
    }

    @Test
    void resizeToJpeg_keepsSmallImageWithinBounds() throws Exception {
        var png = pngBytes(16, 16, Color.RED);
        try (var in = new ByteArrayInputStream(png)) {
            var resized = ImageResizeService.resizeToJpeg(in, 64, 64, 25_000_000);
            assertTrue(resized.isPresent());
            var img = ImageIO.read(new ByteArrayInputStream(resized.get()));
            assertEquals(16, img.getWidth());
            assertEquals(16, img.getHeight());
        }
    }

    @Test
    void isResizableMimeType_acceptsCommonImages() {
        assertTrue(ImageResizeService.isResizableMimeType("image/png"));
        assertTrue(ImageResizeService.isResizableMimeType("image/jpeg; charset=binary"));
        assertFalse(ImageResizeService.isResizableMimeType("application/pdf"));
    }

    private static byte[] pngBytes(int w, int h, Color color) throws Exception {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
