package com.cmb.custommenubackgrounds.background;

import com.cmb.custommenubackgrounds.util.TextureUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a single PNG/JPG/WebP image, decoded once and re-scaled every
 * frame purely on the GPU (no re-decoding of the source file happens after
 * construction).
 */
public class StaticImageBackgroundRenderer implements BackgroundRenderer {

    private final TextureUtil texture = new TextureUtil("static_image");
    private final ScaleMode scaleMode;
    private boolean loaded = false;

    public StaticImageBackgroundRenderer(Path file, ScaleMode scaleMode) {
        this.scaleMode = scaleMode;
        try (InputStream in = Files.newInputStream(file)) {
            // NativeImage.read supports PNG directly; JPG/WebP are decoded via
            // javax.imageio (WebP requires the optional webp-imageio reader,
            // see README) and re-encoded into a NativeImage buffer.
            NativeImage image = ImageDecoding.decodeAnyFormat(in, file);
            texture.upload(image);
            loaded = true;
        } catch (IOException e) {
            System.err.println("[CustomMenuBackgrounds] Failed to load static background '" + file + "': " + e.getMessage());
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        // Static image: nothing to animate.
    }

    @Override
    public void render(DrawContext context, int screenWidth, int screenHeight, float brightness, float overlayOpacity) {
        if (!loaded) {
            return;
        }
        ScaleMode.Rect rect = scaleMode.compute(screenWidth, screenHeight, texture.getWidth(), texture.getHeight());
        texture.drawRect(context, rect.x(), rect.y(), rect.width(), rect.height(), brightness);
        if (overlayOpacity > 0f) {
            context.fill(0, 0, screenWidth, screenHeight, ((int) (overlayOpacity * 255) << 24));
        }
    }

    @Override
    public void close() {
        texture.close();
    }
}
