package com.cmb.custommenubackgrounds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles reading/writing config/custommenubackgrounds.json and creating the
 * config/custommenubackgrounds/{images,videos,panorama,music} folder tree
 * that the user drops their own files into.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("custommenubackgrounds.json");
    public static final Path RESOURCE_ROOT = CONFIG_DIR.resolve("custommenubackgrounds");

    public static final Path IMAGES_DIR = RESOURCE_ROOT.resolve("images");
    public static final Path VIDEOS_DIR = RESOURCE_ROOT.resolve("videos");
    public static final Path PANORAMA_DIR = RESOURCE_ROOT.resolve("panorama");
    public static final Path MUSIC_DIR = RESOURCE_ROOT.resolve("music");

    private ConfigManager() {
    }

    /** Creates the resource folder tree if it does not already exist. */
    public static void ensureFolders() {
        try {
            Files.createDirectories(IMAGES_DIR);
            Files.createDirectories(VIDEOS_DIR);
            Files.createDirectories(PANORAMA_DIR);
            Files.createDirectories(MUSIC_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Custom Menu Backgrounds: could not create resource folders", e);
        }
    }

    /** Loads the config file, creating a default one on first run. */
    public static ModConfig load() {
        ensureFolders();
        if (!Files.exists(CONFIG_FILE)) {
            ModConfig defaults = new ModConfig();
            save(defaults);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            return loaded != null ? loaded : new ModConfig();
        } catch (IOException e) {
            System.err.println("[CustomMenuBackgrounds] Failed to read config, using defaults: " + e.getMessage());
            return new ModConfig();
        }
    }

    /** Persists the given config to disk, overwriting the previous file. */
    public static void save(ModConfig config) {
        ensureFolders();
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("[CustomMenuBackgrounds] Failed to save config: " + e.getMessage());
        }
    }

    public static Path resolveBackgroundFile(String fileName) {
        return RESOURCE_ROOT.resolve("images").resolve(fileName);
    }
}
