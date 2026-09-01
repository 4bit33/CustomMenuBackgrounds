package com.cmb.custommenubackgrounds.background;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Draws a six-face rotating cube map, the same visual technique vanilla's
 * title screen panorama uses, but with an explicit list of face identifiers
 * (rather than vanilla's single-base-name convention) so the mod can mix
 * user-supplied faces with vanilla fallbacks per-face, and an
 * externally-controlled rotation angle so it can be driven by the mod's
 * "panorama rotation speed" setting instead of the system clock.
 *
 * Face order (matching vanilla's panorama_0..5): +X, -X, +Y (top), -Y
 * (bottom), +Z, -Z, viewed from inside the cube.
 */
final class PanoramaCubeMap {

    private static final float NEAR = 0.05f;
    private static final float FAR = 10.0f;

    private PanoramaCubeMap() {
    }

    static void render(DrawContext context, Identifier[] faces, int screenWidth, int screenHeight, float spinDegrees, float brightness) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();

        // Slight constant pitch, like vanilla's panorama, so the horizon isn't dead-center.
        float pitch = 15.0f;
        float yaw = spinDegrees % 360.0f;

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0f);

        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(90.0f),
                (float) screenWidth / (float) screenHeight,
                NEAR, FAR);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.systems.VertexSorter.BY_DISTANCE);

        matrices.loadIdentity();
        matrices.multiply(new org.joml.Quaternionf().rotationX((float) Math.toRadians(pitch)));
        matrices.multiply(new org.joml.Quaternionf().rotationY((float) Math.toRadians(yaw)));

        for (int face = 0; face < 6; face++) {
            drawFace(matrices, faces[face], face);
        }

        RenderSystem.restoreProjectionMatrix();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableDepthTest();
        matrices.pop();
    }

    private static void drawFace(MatrixStack matrices, Identifier texture, int face) {
        RenderSystem.setShaderTexture(0, texture);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        matrices.push();
        switch (face) {
            case 0 -> matrices.multiply(new org.joml.Quaternionf().rotationY((float) Math.toRadians(90)));
            case 1 -> matrices.multiply(new org.joml.Quaternionf().rotationY((float) Math.toRadians(-90)));
            case 2 -> matrices.multiply(new org.joml.Quaternionf().rotationX((float) Math.toRadians(-90)));
            case 3 -> matrices.multiply(new org.joml.Quaternionf().rotationX((float) Math.toRadians(90)));
            case 4 -> { /* front face, no extra rotation */ }
            case 5 -> matrices.multiply(new org.joml.Quaternionf().rotationY((float) Math.toRadians(180)));
        }

        Matrix4f model = matrices.peek().getPositionMatrix();
        float d = 1.0f;
        buffer.vertex(model, -d, -d, -d).texture(0, 0);
        buffer.vertex(model, -d, d, -d).texture(0, 1);
        buffer.vertex(model, d, d, -d).texture(1, 1);
        buffer.vertex(model, d, -d, -d).texture(1, 0);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }
}
