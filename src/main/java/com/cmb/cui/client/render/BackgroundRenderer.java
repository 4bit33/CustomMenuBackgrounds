package com.cmb.cui.client.render;

import net.minecraft.client.gui.DrawContext;

/**
 * A single strategy for painting the main-menu background. Implementations
 * must be cheap to {@link #tick(float)} every frame and must release all
 * native/GPU resources in {@link #close()} - the manager guarantees close()
 * is called exactly once, when the background changes or the title screen
 * is left.
 */
public interface BackgroundRenderer extends AutoCloseable {

    /**
     * Advances internal animation state. Called once per rendered frame,
     * before {@link #render}. deltaSeconds is wall-clock time elapsed since
     * the previous call, already scaled by the user's configured playback
     * speed where relevant.
     */
    void tick(float deltaSeconds);

    /**
     * Paints the background to fill (screenWidth x screenHeight). Must not
     * assume any particular GL state beyond what Minecraft's screen render
     * pass already sets up.
     */
    void render(DrawContext context, int screenWidth, int screenHeight, float brightness, float overlayOpacity);

    /** Releases GPU textures / decoder threads / native handles. */
    @Override
    void close();
}
