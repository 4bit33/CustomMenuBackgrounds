package com.cmb.cui.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

/**
 * Small helper around {@link NativeImageBackedTexture} so the individual
 * background renderers don't have to repeat texture registration /
 * upload / draw boilerplate. One instance owns exactly one GPU texture and
 * re-uploads its pixel buffer only when {@link #upload(NativeImage)} is
 * called (i.e. once per decoded frame, never once per render call).
 */
public final class TextureUtil {

    private final Identifier id;
    private NativeImageBackedTexture texture;
    private int width;
    private int height;

    public TextureUtil(String debugName) {
        this.id = Identifier.of("cui", "dynamic/" + debugName);
    }

    /** Uploads a freshly decoded frame. Takes ownership of {@code image} (will close it). */
    public void upload(NativeImage image) {
        MinecraftClient client = MinecraftClient.getInstance();
        this.width = image.getWidth();
        this.height = image.getHeight();
        client.execute(() -> {
            if (texture == null) {
                texture = new NativeImageBackedTexture(id::toString, image);
                client.getTextureManager().registerTexture(id, texture);
            } else {
                // Replace the backing image and re-upload just this texture's data.
                texture.setImage(image);
                texture.upload();
            }
        });
    }

    public void drawRect(DrawContext context, int x, int y, int width, int height, float brightness) {
        if (texture == null) {
            return;
        }
        // 1.21.11: RenderPipelines.GUI_TEXTURED + color tint for brightness.
        // Brightness 1.0 = white (no tint), 0.0 = black, >1 clamped to white.
        float clamped = Math.max(0f, Math.min(2f, brightness));
        int v = (int) (clamped * 255f);
        if (v > 255) v = 255;
        int color = (0xFF << 24) | (v << 16) | (v << 8) | v;
        // Use whole-texture region; tint via color param where pipeline supports it
        // drawTexture with color: we need the 9-arg variant without regionWidth/regionHeight
        // For 1.21.11 the textured draw without color has no tint, so we use color overload
        try {
            // New 1.21.11 signature: drawTexture(RenderPipeline, Identifier, x,y, u,v, w,h, texW,texH, color)
            context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, width, height, width, height, color);
        } catch (Throwable t) {
            // Fallback to non-tinted variant
            context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, width, height, width, height);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (texture != null) {
                client.getTextureManager().destroyTexture(id);
                texture = null;
            }
        });
    }
}
