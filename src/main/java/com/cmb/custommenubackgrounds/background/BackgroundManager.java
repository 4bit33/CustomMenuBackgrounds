package com.cmb.custommenubackgrounds.background;

import com.cmb.custommenubackgrounds.config.ConfigManager;
import com.cmb.custommenubackgrounds.config.ModConfig;
import net.minecraft.client.gui.DrawContext;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Owns the currently-active {@link BackgroundRenderer} and (re)builds it
 * whenever the user changes settings. Never touches the vanilla title
 * screen / panorama code directly - {@code TitleScreenMixin} asks this class
 * whether it should paint something and, if so, calls {@link #render}
 * instead of (or on top of) vanilla's own background.
 */
public final class BackgroundManager {

    private static BackgroundManager instance;

    private ModConfig config;
    private BackgroundRenderer active;
    private BackgroundType activeType = BackgroundType.VANILLA;

    private BackgroundManager() {
        this.config = ConfigManager.load();
        rebuild();
    }

    public static BackgroundManager get() {
        if (instance == null) {
            instance = new BackgroundManager();
        }
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    /** Applies (and persists) a new config, rebuilding the active renderer. */
    public void applyConfig(ModConfig newConfig) {
        this.config = newConfig;
        ConfigManager.save(newConfig);
        rebuild();
    }

    /** Applies a new config for live preview only - rebuilds the renderer but does not write it to disk. */
    public void previewConfig(ModConfig previewConfig) {
        this.config = previewConfig;
        rebuild();
    }

    /** Discards unsaved preview changes by reloading whatever is currently on disk. */
    public void revertToSaved() {
        this.config = ConfigManager.load();
        rebuild();
    }

    public boolean isVanilla() {
        return activeType == BackgroundType.VANILLA || active == null;
    }

    public void tick(float deltaSeconds) {
        if (active != null) {
            active.tick(deltaSeconds);
        }
    }

    public void render(DrawContext context, int width, int height) {
        if (active != null) {
            active.render(context, width, height, config.brightness, config.overlayOpacity);
        }
    }

    /** Rebuilds the active renderer from the current config, with graceful fallback. */
    public void rebuild() {
        closeActive();

        BackgroundType type = BackgroundType.fromStringSafe(config.backgroundType);
        ScaleMode scaleMode = ScaleMode.fromStringSafe(config.scaleMode);

        try {
            switch (type) {
                case STATIC_IMAGE -> {
                    Path file = ConfigManager.IMAGES_DIR.resolve(config.background);
                    active = new StaticImageBackgroundRenderer(file, scaleMode);
                    activeType = BackgroundType.STATIC_IMAGE;
                }
                case GIF -> {
                    Path file = ConfigManager.IMAGES_DIR.resolve(config.background);
                    active = new GifBackgroundRenderer(file, scaleMode);
                    activeType = BackgroundType.GIF;
                }
                case VIDEO -> {
                    Path file = ConfigManager.VIDEOS_DIR.resolve(config.background);
                    try {
                        active = new VideoBackgroundRenderer(file, scaleMode, config.videoLoop, config.videoSpeed);
                        activeType = BackgroundType.VIDEO;
                    } catch (IOException videoError) {
                        System.err.println("[CustomMenuBackgrounds] " + videoError.getMessage()
                                + " - falling back to static background.");
                        fallBackToStaticOrVanilla(scaleMode);
                    }
                }
                case PANORAMA -> {
                    active = new PanoramaBackgroundRenderer(ConfigManager.PANORAMA_DIR, config.panoramaRotationSpeed);
                    activeType = BackgroundType.PANORAMA;
                }
                case VANILLA -> {
                    active = null;
                    activeType = BackgroundType.VANILLA;
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomMenuBackgrounds] Failed to build background, falling back to vanilla: " + e.getMessage());
            active = null;
            activeType = BackgroundType.VANILLA;
        }
    }

    private void fallBackToStaticOrVanilla(ScaleMode scaleMode) {
        // Per spec: if video fails/unsupported, fall back to a static image
        // if one is configured, otherwise fall back to the vanilla panorama.
        if (config.background != null && !config.background.isBlank()) {
            Path candidate = ConfigManager.IMAGES_DIR.resolve(config.background);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                active = new StaticImageBackgroundRenderer(candidate, scaleMode);
                activeType = BackgroundType.STATIC_IMAGE;
                return;
            }
        }
        active = null;
        activeType = BackgroundType.VANILLA;
    }

    private void closeActive() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    /** Called when leaving the title screen entirely, to free GPU/decoder resources. */
    public void onTitleScreenClosed() {
        closeActive();
        activeType = BackgroundType.VANILLA;
        // Renderer will be lazily rebuilt from config next time the title
        // screen opens, via rebuild() - see TitleScreenMixin.
    }

    /** Called when (re)entering the title screen, so a fresh renderer is ready. */
    public void onTitleScreenOpened() {
        if (active == null && BackgroundType.fromStringSafe(config.backgroundType) != BackgroundType.VANILLA) {
            rebuild();
        }
    }
}
