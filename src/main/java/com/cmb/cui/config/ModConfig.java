package com.cmb.cui.config;

/**
 * Plain data object persisted to config/custommenubackgrounds.json via Gson.
 * Field names intentionally match the format requested in the mod spec.
 */
public class ModConfig {

    public String backgroundType = "vanilla";      // vanilla | static_image | gif | video | panorama
    public String background = "";                  // file name inside the relevant resource folder
    public String scaleMode = "fill";                // fill | fit | stretch | center

    public float brightness = 1.0f;                  // 0.0 - 2.0
    public float overlayOpacity = 0.35f;              // 0.0 - 1.0

    public boolean videoLoop = true;
    public float videoVolume = 0.0f;                  // 0.0 - 1.0
    public float videoSpeed = 1.0f;                   // 0.25 - 2.0, also used for GIF playback speed

    public float panoramaRotationSpeed = 1.0f;        // multiplier, 0 = frozen

    public boolean customMusicEnabled = false;
    public String customMusicFile = "";
    public boolean customMusicLoop = true;
    public float customMusicVolume = 1.0f;

    public ModConfig copy() {
        ModConfig c = new ModConfig();
        c.backgroundType = this.backgroundType;
        c.background = this.background;
        c.scaleMode = this.scaleMode;
        c.brightness = this.brightness;
        c.overlayOpacity = this.overlayOpacity;
        c.videoLoop = this.videoLoop;
        c.videoVolume = this.videoVolume;
        c.videoSpeed = this.videoSpeed;
        c.panoramaRotationSpeed = this.panoramaRotationSpeed;
        c.customMusicEnabled = this.customMusicEnabled;
        c.customMusicFile = this.customMusicFile;
        c.customMusicLoop = this.customMusicLoop;
        c.customMusicVolume = this.customMusicVolume;
        return c;
    }
}
