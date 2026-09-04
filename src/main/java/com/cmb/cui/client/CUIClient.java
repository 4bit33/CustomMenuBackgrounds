package com.cmb.cui.client;

import com.cmb.cui.audio.CustomMusicPlayer;
import com.cmb.cui.client.render.BackgroundManager;
import com.cmb.cui.config.ConfigManager;
import com.cmb.cui.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class CUIClient implements ClientModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final CustomMusicPlayer MUSIC_PLAYER = new CustomMusicPlayer();
    /** Tracks whether we *intend* to be playing custom music, regardless of AL source state. */
    private static boolean customMusicActive = false;
    /** File name currently loaded (to avoid restarting the same track when re-entering menus). */
    private static String currentMusicFileName;
    /** Last effective volume pushed to the player; used to avoid redundant gain updates per tick. */
    private static float lastAppliedVolume = -1f;
    private static long lastMusicRetryMs = 0;
    private static final long MUSIC_RETRY_COOLDOWN_MS = 5000;
    /**
     * Headroom so custom tracks sit a bit lower than vanilla at the same slider
     * position (user request). Effective gain = config slider * vanilla Music
     * slider * this.
     */
    private static final float MUSIC_HEADROOM = 0.7f;

    @Override
    public void onInitializeClient() {
        ConfigManager.ensureFolders();
        // Keep custom menu music playing across ALL menu screens (not just TitleScreen) when enabled.
        // Fabric tick is the simplest place to enforce this without sprinkling logic across every Screen mixin.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inMenu = client.world == null;
            ModConfig cfg = BackgroundManager.get().getConfig();
            boolean wantMusic = cfg.customMusicEnabled && cfg.customMusicFile != null && !cfg.customMusicFile.isBlank();
            if (inMenu && wantMusic && !isCustomMusicActive()) {
                long now = System.currentTimeMillis();
                if (now - lastMusicRetryMs >= MUSIC_RETRY_COOLDOWN_MS) {
                    lastMusicRetryMs = now;
                    refreshMusic();
                    // On success reset cooldown so the loop-restart check isn't throttled;
                    // on failure keep it to avoid log spam.
                    if (isCustomMusicActive()) {
                        lastMusicRetryMs = 0;
                    }
                }
            } else if (!inMenu && isCustomMusicActive()) {
                stopMusic();
            } else if (inMenu && !wantMusic && isCustomMusicActive()) {
                stopMusic();
            }
            // Free background GPU/decoder resources on world join; any menu
            // screen lazily rebuilds them on return (see ScreenPanoramaMixin).
            if (!inMenu && !BackgroundManager.get().isVanilla()) {
                BackgroundManager.get().onMenuClosed();
            }
            // If source stopped unexpectedly but should loop, restart (throttled)
            if (inMenu && wantMusic && cfg.customMusicLoop && isCustomMusicActive() && !MUSIC_PLAYER.isActuallyPlaying()) {
                long now = System.currentTimeMillis();
                if (now - lastMusicRetryMs >= MUSIC_RETRY_COOLDOWN_MS) {
                    lastMusicRetryMs = now;
                    refreshMusic();
                }
            }
            // Live-sync gain with the vanilla Music slider (+ our own slider) so
            // Options -> Music & Sounds -> Music controls custom music too.
            if (isCustomMusicActive() && MUSIC_PLAYER.isActuallyPlaying()) {
                float eff = effectiveMusicVolume(cfg, client);
                if (Math.abs(eff - lastAppliedVolume) > 0.001f) {
                    lastAppliedVolume = eff;
                    MUSIC_PLAYER.setVolume(eff);
                }
            }
        });
    }

    /**
     * Effective gain for custom music: our own slider * vanilla Music category
     * slider * headroom. Vanilla slider at 0 fully mutes custom music.
     */
    public static float effectiveMusicVolume(ModConfig cfg, MinecraftClient client) {
        float ours = cfg != null ? Math.max(0f, Math.min(1f, cfg.customMusicVolume)) : 1f;
        float vanilla = 1f;
        try {
            if (client != null && client.options != null) {
                vanilla = client.options.getSoundVolume(SoundCategory.MUSIC);
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0f, Math.min(1f, ours * vanilla * MUSIC_HEADROOM));
    }

    /**
     * Returns true when the mod is actively managing its own music playback
     * and vanilla's MusicTracker should stay silent.  Used by
     * {@code MusicTrackerMixin} on every client tick.
     */
    public static boolean isCustomMusicActive() {
        return customMusicActive;
    }

    /**
     * Starts/stops the configured custom menu music track.
     * Safe to call repeatedly — if the same file is already playing it just
     * updates the volume instead of restarting from the beginning, so
     * returning to the main menu doesn't replay the track.
     */
    public static void refreshMusic() {
        ModConfig config = BackgroundManager.get().getConfig();

        if (!(config.customMusicEnabled
                && config.customMusicFile != null
                && !config.customMusicFile.isBlank())) {
            if (customMusicActive) {
                stopMusic();
            }
            return;
        }

        // Same track already playing -> keep position, just apply volume live
        if (customMusicActive
                && config.customMusicFile.equals(currentMusicFileName)
                && MUSIC_PLAYER.isActuallyPlaying()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            float eff = effectiveMusicVolume(config, mc);
            lastAppliedVolume = eff;
            MUSIC_PLAYER.setVolume(eff);
            // Still suppress vanilla in case it started something
            if (mc != null && mc.getMusicTracker() != null) {
                mc.getMusicTracker().stop();
            }
            return;
        }

        MUSIC_PLAYER.stop();
        customMusicActive = false;
        currentMusicFileName = null;

        Path file = ConfigManager.resolveMusicFile(config.customMusicFile);
        if (file == null || !Files.isRegularFile(file)) {
            System.err.println("[CUI] Music file not found: " + config.customMusicFile
                    + " (checked " + ConfigManager.AUDIO_DIR + " and " + ConfigManager.MUSIC_DIR + ")");
            // Also list available for debugging
            try {
                System.err.println("[CUI] Available audio files: " + ConfigManager.listMusicFiles());
            } catch (Throwable ignored) {}
            return;
        }
        MinecraftClient mc0 = MinecraftClient.getInstance();
        float eff0 = effectiveMusicVolume(config, mc0);
        System.out.println("[CUI] Playing custom music: " + file + " loop=" + config.customMusicLoop + " vol=" + config.customMusicVolume + " effective=" + eff0);
        try {
            MUSIC_PLAYER.play(file, config.customMusicLoop, eff0);
            lastAppliedVolume = eff0;
            customMusicActive = true;
            currentMusicFileName = config.customMusicFile;
            // Stop whatever vanilla is currently playing so there is no overlap
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getMusicTracker() != null) {
                client.getMusicTracker().stop();
            }
        } catch (IOException e) {
            LOGGER.error("[CUI] Failed to play custom music", e);
            customMusicActive = false;
            currentMusicFileName = null;
        } catch (Throwable t) {
            LOGGER.error("[CUI] Unexpected error playing music", t);
            customMusicActive = false;
            currentMusicFileName = null;
        }
    }

    /** Stops custom music and lets vanilla resume. Called when leaving the title screen. */
    public static void stopMusic() {
        MUSIC_PLAYER.stop();
        customMusicActive = false;
        currentMusicFileName = null;
        lastAppliedVolume = -1f;
    }
}
