package com.cmb.cui.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a rotating skybox from up to 6 user-supplied panorama images
 * ("panorama_0.png" .. "panorama_5.png"), falling back to Minecraft's own
 * vanilla panorama textures for any face the user did not provide. This
 * mirrors vanilla's PanoramaRenderer but reads its faces from the mod's own
 * resource folder instead of the built-in resource pack, and exposes a
 * configurable rotation speed.
 */
public class PanoramaBackgroundRenderer implements BackgroundRenderer {

    private static final Identifier[] VANILLA_FACES = new Identifier[]{
            Identifier.ofVanilla("textures/gui/title/background/panorama_0.png"),
            Identifier.ofVanilla("textures/gui/title/background/panorama_1.png"),
            Identifier.ofVanilla("textures/gui/title/background/panorama_2.png"),
            Identifier.ofVanilla("textures/gui/title/background/panorama_3.png"),
            Identifier.ofVanilla("textures/gui/title/background/panorama_4.png"),
            Identifier.ofVanilla("textures/gui/title/background/panorama_5.png"),
    };

    private final Identifier[] faces = new Identifier[6];
    private final List<Identifier> ownedTextures = new ArrayList<>();
    private final float rotationSpeed;
    private float spin = 0f;

    public PanoramaBackgroundRenderer(Path panoramaDir, float rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
        for (int i = 0; i < 6; i++) {
            Path candidate = panoramaDir.resolve("panorama_" + i + ".png");
            if (Files.isRegularFile(candidate)) {
                faces[i] = loadCustomFace(candidate, i);
            } else {
                faces[i] = VANILLA_FACES[i];
            }
        }
    }

    private Identifier loadCustomFace(Path file, int index) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.of("cui", "panorama/face_" + index);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            ownedTextures.add(id);
            return id;
        } catch (IOException e) {
            System.err.println("[CUI] Failed to load panorama face " + index + ": " + e.getMessage());
            return VANILLA_FACES[index];
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        spin += deltaSeconds * 10f * rotationSpeed; // degrees/sec at speed=1, matches vanilla's gentle drift
    }

    @Override
    public void render(DrawContext context, int screenWidth, int screenHeight, float brightness, float overlayOpacity) {
        // A full perspective cubemap render (the same technique vanilla's
        // PanoramaRenderer uses) needs its own projection matrix and render
        // pass rather than DrawContext's 2D quad drawing. Vanilla's own
        // CubeMapRenderer only accepts a single base Identifier and derives
        // face names from a fixed "_0".."_5" suffix convention, which does not
        // fit our case of mixing user-supplied faces with vanilla fallbacks
        // under unrelated names - so PanoramaCubeMap below is a small,
        // self-contained re-implementation of that same technique that takes
        // 6 explicit face identifiers.
        PanoramaCubeMap.render(context, faces, screenWidth, screenHeight, spin, brightness);

        if (overlayOpacity > 0f) {
            context.fill(0, 0, screenWidth, screenHeight, ((int) (overlayOpacity * 255) << 24));
        }
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : ownedTextures) {
            client.execute(() -> client.getTextureManager().destroyTexture(id));
        }
        ownedTextures.clear();
    }
}
