package com.cmb.custommenubackgrounds;

import com.cmb.custommenubackgrounds.audio.CustomMusicPlayer;
import com.cmb.custommenubackgrounds.background.BackgroundManager;
import com.cmb.custommenubackgrounds.config.ConfigManager;
import com.cmb.custommenubackgrounds.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomMenuBackgroundsClient implements ClientModInitializer {

    public static final String MOD_ID = "custommenubackgrounds";

    private static final CustomMusicPlayer MUSIC_PLAYER = new CustomMusicPlayer();
    /** Tracks whether we *intend* to be playing custom music, regardless of AL source state. */
    private static boolean customMusicActive = false;

    @Override
    public void onInitializeClient() {
        ConfigManager.ensureFolders();
    }

    public static CustomMusicPlayer musicPlayer() {
        return MUSIC_PLAYER;
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
     * Safe to call repeatedly — will stop any previous track first.
     */
    public static void refreshMusic() {
        ModConfig config = BackgroundManager.get().getConfig();
        MUSIC_PLAYER.stop();
        customMusicActive = false;

        if (config.customMusicEnabled
                && config.customMusicFile != null
                && !config.customMusicFile.isBlank()) {
            Path file = ConfigManager.MUSIC_DIR.resolve(config.customMusicFile);
            if (!Files.isRegularFile(file)) {
                System.err.println("[CustomMenuBackgrounds] Music file not found: " + file);
                return;
            }
            try {
                MUSIC_PLAYER.play(file, config.customMusicLoop, config.customMusicVolume);
                customMusicActive = true;
                // Stop whatever vanilla is currently playing so there is no overlap
                MinecraftClient client = MinecraftClient.getInstance();
                client.getMusicTracker().stop();
            } catch (IOException e) {
                System.err.println("[CustomMenuBackgrounds] Failed to play custom music: " + e.getMessage());
                customMusicActive = false;
            }
        }
    }

    /** Stops custom music and lets vanilla resume. Called when leaving the title screen. */
    public static void stopMusic() {
        MUSIC_PLAYER.stop();
        customMusicActive = false;
    }
}
