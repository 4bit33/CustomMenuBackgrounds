package com.cmb.cui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CUI configuration &amp; asset storage — per spec §8-9:
 * <pre>
 * .minecraft/cui/
 *   ├─ config/cui.json          (main config, replaces config/custommenubackgrounds.json)
 *   ├─ images/                  (PNG/JPG/WebP/GIF)
 *   ├─ videos/                  (WebM/MP4)
 *   ├─ panorama/                (panorama_0..5.png)
 *   ├─ audio/  (+ alias music/) (OGG)
 *   └─ cache/
 * </pre>
 * Legacy path {@code config/custommenubackgrounds(.json)} is still read
 * for migration so existing installs don't lose data.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- New CUI root: .minecraft/cui/ (§8-9) ---
    public static final Path CUI_ROOT = FabricLoader.getInstance().getGameDir().resolve("cui");
    public static final Path CONFIG_DIR = CUI_ROOT.resolve("config");
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("cui.json");
    public static final Path RESOURCE_ROOT = CUI_ROOT;

    public static final Path IMAGES_DIR = CUI_ROOT.resolve("images");
    public static final Path VIDEOS_DIR = CUI_ROOT.resolve("videos");
    public static final Path PANORAMA_DIR = CUI_ROOT.resolve("panorama");
    public static final Path AUDIO_DIR = CUI_ROOT.resolve("audio");
    /** Legacy alias — `cui/music` kept for back-compat; canonical is {@link #AUDIO_DIR}. */
    public static final Path MUSIC_DIR = CUI_ROOT.resolve("music");
    public static final Path CACHE_DIR = CUI_ROOT.resolve("cache");

    // --- Legacy path (config/custommenubackgrounds.json) for migration ---
    private static final Path LEGACY_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("custommenubackgrounds.json");
    private static final Path LEGACY_RESOURCE_ROOT = FabricLoader.getInstance().getConfigDir().resolve("custommenubackgrounds");

    private ConfigManager() {
    }

    /** Creates the CUI folder tree if it does not already exist. */
    public static void ensureFolders() {
        try {
            Files.createDirectories(IMAGES_DIR);
            Files.createDirectories(VIDEOS_DIR);
            Files.createDirectories(PANORAMA_DIR);
            Files.createDirectories(AUDIO_DIR);
            Files.createDirectories(MUSIC_DIR);
            Files.createDirectories(CACHE_DIR);
            Files.createDirectories(CONFIG_DIR);
            // Keep both audio and music in sync for back-compat
            syncAudioDirs();
            migrateFromLegacyIfNeeded();
            // After migration, also sync cui/music <-> cui/audio if one has files the other doesn't
            syncAudioDirs();
        } catch (IOException e) {
            throw new RuntimeException("[CUI] could not create resource folders", e);
        }
    }

    private static void syncAudioDirs() throws IOException {
        // Mirror files between cui/audio and cui/music so either location works
        // Don't overwrite existing files, just fill missing ones
        if (Files.isDirectory(AUDIO_DIR)) {
            try (var stream = Files.list(AUDIO_DIR)) {
                for (Path f : (Iterable<Path>) stream::iterator) {
                    if (Files.isRegularFile(f)) {
                        Path target = MUSIC_DIR.resolve(f.getFileName());
                        if (!Files.exists(target)) Files.copy(f, target);
                    }
                }
            }
        }
        if (Files.isDirectory(MUSIC_DIR)) {
            try (var stream = Files.list(MUSIC_DIR)) {
                for (Path f : (Iterable<Path>) stream::iterator) {
                    if (Files.isRegularFile(f)) {
                        Path target = AUDIO_DIR.resolve(f.getFileName());
                        if (!Files.exists(target)) Files.copy(f, target);
                    }
                }
            }
        }
    }

    private static void migrateFromLegacyIfNeeded() {
        try {
            boolean hasLegacyConfig = Files.exists(LEGACY_CONFIG_FILE);
            boolean needConfigMigration = !Files.exists(CONFIG_FILE) && hasLegacyConfig;
            if (needConfigMigration) {
                System.out.println("[CUI] Migrating legacy config: " + LEGACY_CONFIG_FILE + " -> " + CONFIG_FILE);
                Files.createDirectories(CONFIG_DIR);
                Files.copy(LEGACY_CONFIG_FILE, CONFIG_FILE);
            }
            // Always migrate assets if legacy dirs have content, even if config already exists
            // (user may have placed files in old location after initial migration)
            migrateDir(LEGACY_RESOURCE_ROOT.resolve("images"), IMAGES_DIR);
            migrateDir(LEGACY_RESOURCE_ROOT.resolve("videos"), VIDEOS_DIR);
            migrateDir(LEGACY_RESOURCE_ROOT.resolve("panorama"), PANORAMA_DIR);
            migrateDir(LEGACY_RESOURCE_ROOT.resolve("music"), AUDIO_DIR);
            migrateDir(LEGACY_RESOURCE_ROOT.resolve("music"), MUSIC_DIR);
            // Also ensure cui/music and cui/audio stay in sync
            syncAudioDirs();
        } catch (IOException e) {
            System.err.println("[CUI] Legacy migration failed: " + e.getMessage());
        }
    }

    private static void migrateDir(Path src, Path dst) throws IOException {
        if (!Files.isDirectory(src)) return;
        if (!Files.exists(dst)) Files.createDirectories(dst);
        try (var stream = Files.list(src)) {
            for (Path f : (Iterable<Path>) stream::iterator) {
                Path target = dst.resolve(f.getFileName());
                if (!Files.exists(target) && Files.isRegularFile(f)) {
                    Files.copy(f, target);
                }
            }
        }
    }

    /** Loads the config file, creating a default one on first run. Handles legacy migration. */
    public static ModConfig load() {
        ensureFolders();
        // Prefer new file, fallback to legacy if new missing
        Path fileToLoad = Files.exists(CONFIG_FILE) ? CONFIG_FILE : LEGACY_CONFIG_FILE;
        if (!Files.exists(fileToLoad)) {
            ModConfig defaults = new ModConfig();
            save(defaults);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded == null) loaded = new ModConfig();
            // if we loaded legacy, immediately persist to new location
            if (fileToLoad.equals(LEGACY_CONFIG_FILE)) {
                System.out.println("[CUI] Loaded legacy config, saving to new location");
                save(loaded);
            }
            return loaded;
        } catch (IOException e) {
            System.err.println("[CUI] Failed to read config, using defaults: " + e.getMessage());
            return new ModConfig();
        }
    }

    /** Persists the given config to disk, overwriting the previous file. */
    public static void save(ModConfig config) {
        ensureFolders();
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("[CUI] Failed to save config: " + e.getMessage());
        }
    }

    /** Resolves a music file checking both `cui/audio` and `cui/music` (and legacy). */
    public static Path resolveMusicFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        Path p = AUDIO_DIR.resolve(fileName);
        if (Files.isRegularFile(p)) return p;
        p = MUSIC_DIR.resolve(fileName);
        if (Files.isRegularFile(p)) return p;
        p = LEGACY_RESOURCE_ROOT.resolve("music").resolve(fileName);
        if (Files.isRegularFile(p)) return p;
        // fallback to audio even if not exists (caller will handle not-found)
        return AUDIO_DIR.resolve(fileName);
    }

    /** Returns combined list of .ogg files from both audio and music dirs (deduped). */
    public static List<String> listMusicFiles() {
        Set<String> set = new LinkedHashSet<>();
        for (Path dir : new Path[]{AUDIO_DIR, MUSIC_DIR, LEGACY_RESOURCE_ROOT.resolve("music")}) {
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .map(pl -> pl.getFileName().toString())
                        .filter(n -> n.toLowerCase().endsWith(".ogg"))
                        .forEach(set::add);
            } catch (IOException ignored) {}
        }
        List<String> list = new ArrayList<>(set);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }
}
