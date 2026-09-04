package com.cmb.cui.gui;


import com.cmb.cui.client.CUIClient;
import com.cmb.cui.client.render.BackgroundManager;
import com.cmb.cui.client.render.BackgroundType;
import com.cmb.cui.client.render.ScaleMode;
import com.cmb.cui.config.ConfigManager;
import com.cmb.cui.config.ModConfig;
import com.cmb.cui.gui.widget.LabeledSliderWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The mod's own settings menu, reachable from the title screen. All widgets
 * write into a working copy of {@link ModConfig}; "Preview" applies that
 * copy live (without touching disk), "Save" persists it, "Reset" discards
 * unsaved edits and reloads whatever is currently saved on disk.
 */
public class ModSettingsScreen extends Screen {

    private final Screen parent;
    private ModConfig working;

    public ModSettingsScreen(Screen parent) {
        super(Text.translatable("cui.settings.title"));
        this.parent = parent;
        this.working = BackgroundManager.get().getConfig().copy();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 35;
        int rowHeight = 24;
        int colWidth = 155;
        int gap = 8;
        int totalWidth = colWidth * 2 + gap;

        int leftX = centerX - totalWidth / 2;
        int rightX = leftX + colWidth + gap;

        // --- ROW 1 ---
        // Left: Background type — 1.21.11 builder now takes initial value in constructor, not .initially()
        this.addDrawableChild(CyclingButtonWidget.builder(
                        (BackgroundType type) -> Text.translatable("cui.settings.type." + type.name().toLowerCase()),
                        BackgroundType.fromStringSafe(working.backgroundType))
                .values(BackgroundType.values())
                .build(leftX, y, colWidth, 20,
                        Text.translatable("cui.settings.type"),
                        (button, value) -> {
                            working.backgroundType = value.name().toLowerCase();
                            working.background = "";
                            this.clearAndInit();
                        }));

        // Right: File picker (matches current type)
        this.addDrawableChild(CyclingButtonWidget.builder(
                        (String name) -> name.isEmpty()
                                ? Text.translatable("cui.settings.file.none")
                                : Text.translatable("cui.settings.file", name),
                        working.background == null ? "" : working.background)
                .values(availableFilesForCurrentType())
                .build(rightX, y, colWidth, 20, Text.empty(),
                        (button, value) -> working.background = value));
        y += rowHeight;

        // --- ROW 2 ---
        // Left: Select / Import Background File (In-game File Browser)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("cui.settings.browse_file"),
                button -> openFilePicker(false)
        ).dimensions(leftX, y, colWidth, 20).build());

        // Right: Scale mode
        this.addDrawableChild(CyclingButtonWidget.builder(
                        (ScaleMode mode) -> Text.literal(mode.name()),
                        ScaleMode.fromStringSafe(working.scaleMode))
                .values(ScaleMode.values())
                .build(rightX, y, colWidth, 20,
                        Text.translatable("cui.settings.scale_mode"),
                        (button, value) -> working.scaleMode = value.name().toLowerCase()));
        y += rowHeight;

        // --- ROW 3 ---
        // Left: Brightness
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.0f, 2.0f, working.brightness,
                (prefix, v) -> Text.translatable("cui.settings.brightness", format(v)),
                v -> working.brightness = v));

        // Right: Overlay opacity
        this.addDrawableChild(new LabeledSliderWidget(rightX, y, colWidth, 20,
                0.0f, 1.0f, working.overlayOpacity,
                (prefix, v) -> Text.translatable("cui.settings.overlay", format(v)),
                v -> working.overlayOpacity = v));
        y += rowHeight;

        // --- ROW 4 ---
        // Left: Playback / Video speed
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.1f, 3.0f, working.videoSpeed,
                (prefix, v) -> Text.translatable("cui.settings.speed", format(v)),
                v -> working.videoSpeed = v));

        // Right: Video volume
        this.addDrawableChild(new LabeledSliderWidget(rightX, y, colWidth, 20,
                0.0f, 1.0f, working.videoVolume,
                (prefix, v) -> Text.translatable("cui.settings.volume", format(v)),
                v -> working.videoVolume = v));
        y += rowHeight;

        // --- ROW 5 ---
        // Left: Panorama rotation speed
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.0f, 3.0f, working.panoramaRotationSpeed,
                (prefix, v) -> Text.translatable("cui.settings.panorama_speed", format(v)),
                v -> working.panoramaRotationSpeed = v));

        // Right: Custom music toggle
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(working.customMusicEnabled)
                .build(rightX, y, colWidth, 20,
                        Text.translatable("cui.settings.music_enabled"),
                        (button, value) -> working.customMusicEnabled = value));
        y += rowHeight;

        // --- ROW 6 ---
        // Left: Custom music file picker (merged audio + music dirs)
        this.addDrawableChild(CyclingButtonWidget.builder(
                        (String name) -> name.isEmpty()
                                ? Text.translatable("cui.settings.file.none")
                                : Text.translatable("cui.settings.file", name),
                        working.customMusicFile == null ? "" : working.customMusicFile)
                .values(availableMusicFiles())
                .build(leftX, y, colWidth, 20, Text.empty(),
                        (button, value) -> working.customMusicFile = value));

        // Right: Select / Import Music File (In-game File Browser)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("cui.settings.browse_music"),
                button -> openFilePicker(true)
        ).dimensions(rightX, y, colWidth, 20).build());
        y += rowHeight;

        // --- ROW 7 ---
        // Left: Custom music volume
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.0f, 1.0f, working.customMusicVolume,
                (prefix, v) -> Text.translatable("cui.settings.music_volume", format(v)),
                v -> working.customMusicVolume = v));

        // Right: Open resource folder
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("cui.settings.open_folder"),
                button -> Util.getOperatingSystem().open(ConfigManager.RESOURCE_ROOT.toFile())
        ).dimensions(rightX, y, colWidth, 20).build());
        y += rowHeight + 6;

        // --- BOTTOM CONTROLS ---
        // Preview / Reset / Save row
        int buttonWidth = (totalWidth - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("cui.settings.preview"),
                button -> BackgroundManager.get().previewConfig(working.copy())
        ).dimensions(leftX, y, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("cui.settings.reset"),
                button -> {
                    BackgroundManager.get().revertToSaved();
                    this.working = BackgroundManager.get().getConfig().copy();
                    this.clearAndInit();
                }
        ).dimensions(leftX + buttonWidth + 4, y, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("cui.settings.save"),
                button -> {
                    BackgroundManager.get().applyConfig(working.copy());
                    CUIClient.refreshMusic();
                    this.close();
                }
        ).dimensions(leftX + 2 * (buttonWidth + 4), y, buttonWidth, 20).build());
        y += rowHeight + 4;

        // Done / back
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(centerX - 100, y, 200, 20).build());
    }

    private void openFilePicker(boolean isMusic) {
        BackgroundType type = BackgroundType.fromStringSafe(working.backgroundType);
        // For music, canonical is cui/audio per spec §8, but we also keep cui/music in sync
        Path targetDir = isMusic ? ConfigManager.AUDIO_DIR : switch (type) {
            case VIDEO -> ConfigManager.VIDEOS_DIR;
            case GIF, STATIC_IMAGE -> ConfigManager.IMAGES_DIR;
            default -> ConfigManager.IMAGES_DIR;
        };

        String[] ext = isMusic
                ? new String[]{".ogg"}
                : switch (type) {
                    case VIDEO -> new String[]{".webm", ".mp4"};
                    case GIF -> new String[]{".gif"};
                    case STATIC_IMAGE -> new String[]{".png", ".jpg", ".jpeg", ".webp"};
                    default -> new String[]{".png", ".jpg", ".jpeg", ".webp"};
                };

        // OS-native file chooser via LWJGL tinyfd (avoids AWT HeadlessException) — replaces in-game browser per user request
        Path home = Paths.get(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        String defaultPath = (Files.isDirectory(downloads) ? downloads : home) + File.separator;
        // filter patterns must be "*.ext" for tinyfd
        String[] filterPatterns = Arrays.stream(ext).map(s -> "*" + s.toLowerCase()).toArray(String[]::new);
        String filterDesc = isMusic ? "OGG files (*.ogg)"
                : type == BackgroundType.VIDEO ? "Video files (*.webm *.mp4)"
                : type == BackgroundType.GIF ? "GIF files (*.gif)"
                : "Image files (*.png *.jpg *.jpeg *.webp)";
        String title = isMusic ? "Select music file" : "Select background file";

        // tinyfd is blocking and uses native OS dialog — no AWT, so no HeadlessException under Fabric
        String selectedPath;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pb = null;
            if (filterPatterns.length > 0) {
                pb = stack.mallocPointer(filterPatterns.length);
                for (String pat : filterPatterns) {
                    pb.put(stack.UTF8(pat));
                }
                pb.flip();
            }
            selectedPath = TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, pb, filterDesc, false);
        } catch (Throwable t) {
            System.err.println("[CUI] tinyfd failed, falling back to folder open: " + t.getMessage());
            Util.getOperatingSystem().open(targetDir.toFile());
            return;
        }

        if (selectedPath == null || selectedPath.isBlank()) return;

        File selected = new File(selectedPath);
        String lower = selected.getName().toLowerCase();
        boolean ok = false;
        for (String e : ext) if (lower.endsWith(e)) { ok = true; break; }
        if (!ok) {
            System.err.println("[CUI] Selected file has unsupported extension: " + selected.getName());
            return;
        }
        try {
            Files.createDirectories(targetDir);
            String fileName = selected.getName();
            Path dest = targetDir.resolve(fileName);
            Files.copy(selected.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            // For music, also mirror to legacy music dir so both remain valid
            if (isMusic) {
                try {
                    Path other = ConfigManager.MUSIC_DIR;
                    if (!other.equals(targetDir)) {
                        Files.createDirectories(other);
                        Files.copy(selected.toPath(), other.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ignored) {}
                working.customMusicFile = fileName;
            } else {
                working.background = fileName;
            }
            BackgroundManager.get().previewConfig(working.copy());
            this.clearAndInit();
        } catch (IOException e) {
            System.err.println("[CUI] Failed to import file: " + e.getMessage());
        }
    }

    protected void clearAndInit() {
        this.clearChildren();
        this.init();
    }

    private List<String> availableFilesForCurrentType() {
        BackgroundType type = BackgroundType.fromStringSafe(working.backgroundType);
        return switch (type) {
            case VIDEO -> availableFilesIn(ConfigManager.VIDEOS_DIR, ".webm", ".mp4");
            case GIF -> availableFilesIn(ConfigManager.IMAGES_DIR, ".gif");
            case STATIC_IMAGE -> availableFilesIn(ConfigManager.IMAGES_DIR, ".png", ".jpg", ".jpeg", ".webp");
            default -> List.of("");
        };
    }

    private static List<String> availableMusicFiles() {
        List<String> files = ConfigManager.listMusicFiles();
        List<String> withEmpty = new java.util.ArrayList<>();
        withEmpty.addFirst("");
        withEmpty.addAll(files);
        return withEmpty;
    }

    private static List<String> availableFilesIn(Path dir, String... extensions) {
        if (!Files.isDirectory(dir)) {
            return List.of("");
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<String> names = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> {
                        String lower = name.toLowerCase();
                        for (String ext : extensions) {
                            if (lower.endsWith(ext)) return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            names.addFirst("");
            return names;
        } catch (IOException e) {
            return List.of("");
        }
    }

    private static String format(float v) {
        return String.format("%.2f", v);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x60000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
