package com.cmb.custommenubackgrounds.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.gui.DrawContext;
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
        this.id = Identifier.of("custommenubackgrounds", "dynamic/" + debugName);
    }

    /** Uploads a freshly decoded frame. Takes ownership of {@code image} (will close it). */
    public void upload(NativeImage image) {
        MinecraftClient client = MinecraftClient.getInstance();
        this.width = image.getWidth();
        this.height = image.getHeight();
        client.execute(() -> {
            if (texture == null) {
                texture = new NativeImageBackedTexture(image);
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
        RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0f);
        context.drawTexture(id, x, y, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
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
