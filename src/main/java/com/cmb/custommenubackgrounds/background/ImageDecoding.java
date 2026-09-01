package com.cmb.custommenubackgrounds.background;

import net.minecraft.client.texture.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Decodes PNG/JPG/WebP files into Minecraft's {@link NativeImage}.
 *
 * PNG is handled natively by NativeImage.read (fastest path).
 * JPG and WebP go through javax.imageio: JPG is supported out of the box by
 * the JDK, WebP requires the optional "webp-imageio" library declared in
 * build.gradle (see README - some JDK/OS combinations lack a built-in WebP
 * reader).
 */
final class ImageDecoding {

    private ImageDecoding() {
    }

    static NativeImage decodeAnyFormat(InputStream in, Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        byte[] bytes = in.readAllBytes();

        if (name.endsWith(".png")) {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        }

        // JPG / WebP: decode via ImageIO into an ARGB BufferedImage, then copy
        // pixel-by-pixel into a NativeImage (done once at load time, not per frame).
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
        if (buffered == null) {
            throw new IOException("Unsupported or corrupt image format: " + file.getFileName());
        }
        return bufferedImageToNativeImage(buffered);
    }

    static NativeImage bufferedImageToNativeImage(BufferedImage buffered) {
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = buffered.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                image.setColor(x, y, abgr);
            }
        }
        return image;
    }

    /** Convenience used by the GIF decoder, which already works with BufferedImages. */
    static ByteArrayOutputStream toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out;
    }
}
