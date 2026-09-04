package com.cmb.cui.client.render;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Stub for 1.21.11: true cubemap panorama rendering requires the new
 * {@code CubeMapRenderer} / {@code POSITION_TEX_PANORAMA} pipeline with
 * {@code GpuBuffer} + {@code CommandEncoder}. The old 1.21.1 implementation
 * used {@code BufferRenderer} / {@code VertexFormat} / {@code RenderSystem}
 * which no longer exists.
 *
 * <p>For MVP (static image / GIF / video) this stub is sufficient — it
 * ensures the project compiles on 1.21.11 and shows a visible fallback
 * (first face stretched) instead of crashing. A proper cubemap reimplementation
 * can be added in Phase 5 without touching the public API of
 * {@link PanoramaBackgroundRenderer}.</p>
 */
final class PanoramaCubeMap {

    private PanoramaCubeMap() {}

    // spinDegrees/brightness are part of the stable signature for the future full
    // cubemap reimplementation (Phase 5); unused while this is a flat fallback.
    @SuppressWarnings("unused")
    static void render(DrawContext context, Identifier[] faces, int screenWidth, int screenHeight, float spinDegrees, float brightness) {
        // Simple fallback: draw the first available face as a fullscreen stretched image.
        // This keeps the title screen usable while the full rotating cube is reimplemented.
        Identifier first = null;
        if (faces != null) {
            for (Identifier id : faces) {
                if (id != null) { first = id; break; }
            }
        }
        if (first != null) {
            try {
                // 1.21.11 DrawContext requires a RenderPipeline explicitly
                context.drawTexture(RenderPipelines.GUI_TEXTURED, first, 0, 0, 0f, 0f, screenWidth, screenHeight, screenWidth, screenHeight);
            } catch (Throwable t) {
                context.fill(0, 0, screenWidth, screenHeight, 0xFF101010);
            }
        } else {
            context.fill(0, 0, screenWidth, screenHeight, 0xFF101010);
        }
        // Overlay is handled by caller (PanoramaBackgroundRenderer), so nothing else here.
    }
}
