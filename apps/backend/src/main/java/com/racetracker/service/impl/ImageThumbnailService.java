package com.racetracker.service.impl;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Generates small JPEG thumbnails for uploaded images using the JDK imaging stack.
 * Non-image or unreadable content yields an empty result so callers keep the original only.
 */
@Component
public class ImageThumbnailService {

    private static final int MAX_DIMENSION = 480;

    public Optional<Thumbnail> generate(byte[] source) {
        if (source == null || source.length == 0) {
            return Optional.empty();
        }
        try (InputStream input = new ByteArrayInputStream(source)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return Optional.empty();
            }
            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            double scale = Math.min(1.0, MAX_DIMENSION / (double) Math.max(originalWidth, originalHeight));
            int width = Math.max(1, (int) Math.round(originalWidth * scale));
            int height = Math.max(1, (int) Math.round(originalHeight * scale));

            BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(thumbnail, "jpg", output)) {
                return Optional.empty();
            }
            return Optional.of(new Thumbnail(output.toByteArray(), originalWidth, originalHeight, width, height));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public record Thumbnail(byte[] bytes, int originalWidth, int originalHeight, int width, int height) {
    }
}
