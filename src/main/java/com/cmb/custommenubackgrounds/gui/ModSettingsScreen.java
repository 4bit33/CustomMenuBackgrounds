package com.cmb.custommenubackgrounds.gui;


import com.cmb.custommenubackgrounds.CustomMenuBackgroundsClient;
import com.cmb.custommenubackgrounds.background.BackgroundManager;
import com.cmb.custommenubackgrounds.background.BackgroundType;
import com.cmb.custommenubackgrounds.background.ScaleMode;
import com.cmb.custommenubackgrounds.config.ConfigManager;
import com.cmb.custommenubackgrounds.config.ModConfig;
import com.cmb.custommenubackgrounds.gui.widget.LabeledSliderWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        super(Text.translatable("custommenubackgrounds.settings.title"));
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
        // Left: Background type
        this.addDrawableChild(CyclingButtonWidget.<BackgroundType>builder(type -> Text.translatable(
                        "custommenubackgrounds.settings.type." + type.name().toLowerCase()))
                .values(BackgroundType.values())
                .initially(BackgroundType.fromStringSafe(working.backgroundType))
                .build(leftX, y, colWidth, 20,
                        Text.translatable("custommenubackgrounds.settings.type"),
                        (button, value) -> {
                            working.backgroundType = value.name().toLowerCase();
                            working.background = "";
                            this.clearAndInit();
                        }));

        // Right: File picker (matches current type)
        this.addDrawableChild(CyclingButtonWidget.<String>builder(name -> name.isEmpty()
                        ? Text.translatable("custommenubackgrounds.settings.file.none")
                        : Text.translatable("custommenubackgrounds.settings.file", name))
                .values(availableFilesForCurrentType())
                .initially(working.background == null ? "" : working.background)
                .build(rightX, y, colWidth, 20, Text.empty(),
                        (button, value) -> working.background = value));
        y += rowHeight;

        // --- ROW 2 ---
        // Left: Select / Import Background File (In-game File Browser)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.browse_file"),
                button -> openFilePicker(false)
        ).dimensions(leftX, y, colWidth, 20).build());

        // Right: Scale mode
        this.addDrawableChild(CyclingButtonWidget.<ScaleMode>builder(mode -> Text.literal(mode.name()))
                .values(ScaleMode.values())
                .initially(ScaleMode.fromStringSafe(working.scaleMode))
                .build(rightX, y, colWidth, 20,
                        Text.translatable("custommenubackgrounds.settings.scale_mode"),
                        (button, value) -> working.scaleMode = value.name().toLowerCase()));
        y += rowHeight;

        // --- ROW 3 ---
        // Left: Brightness
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.0f, 2.0f, working.brightness,
                (prefix, v) -> Text.translatable("custommenubackgrounds.settings.brightness", format(v)),
                v -> working.brightness = v));

        // Right: Overlay opacity
        this.addDrawableChild(new LabeledSliderWidget(rightX, y, colWidth, 20,
                0.0f, 1.0f, working.overlayOpacity,
                (prefix, v) -> Text.translatable("custommenubackgrounds.settings.overlay", format(v)),
                v -> working.overlayOpacity = v));
        y += rowHeight;

        // --- ROW 4 ---
        // Left: Playback / Video speed
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.1f, 3.0f, working.videoSpeed,
                (prefix, v) -> Text.translatable("custommenubackgrounds.settings.speed", format(v)),
                v -> working.videoSpeed = v));

        // Right: Video volume
        this.addDrawableChild(new LabeledSliderWidget(rightX, y, colWidth, 20,
                0.0f, 1.0f, working.videoVolume,
                (prefix, v) -> Text.translatable("custommenubackgrounds.settings.volume", format(v)),
                v -> working.videoVolume = v));
        y += rowHeight;

        // --- ROW 5 ---
        // Left: Panorama rotation speed
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.0f, 3.0f, working.panoramaRotationSpeed,
                (prefix, v) -> Text.translatable("custommenubackgrounds.settings.panorama_speed", format(v)),
                v -> working.panoramaRotationSpeed = v));

        // Right: Custom music toggle
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(working.customMusicEnabled)
                .build(rightX, y, colWidth, 20,
                        Text.translatable("custommenubackgrounds.settings.music_enabled"),
                        (button, value) -> working.customMusicEnabled = value));
        y += rowHeight;

        // --- ROW 6 ---
        // Left: Custom music file picker
        this.addDrawableChild(CyclingButtonWidget.<String>builder(name -> name.isEmpty()
                        ? Text.translatable("custommenubackgrounds.settings.file.none")
                        : Text.translatable("custommenubackgrounds.settings.file", name))
                .values(availableFilesIn(ConfigManager.MUSIC_DIR, ".ogg"))
                .initially(working.customMusicFile == null ? "" : working.customMusicFile)
                .build(leftX, y, colWidth, 20, Text.empty(),
                        (button, value) -> working.customMusicFile = value));

        // Right: Select / Import Music File (In-game File Browser)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.browse_music"),
                button -> openFilePicker(true)
        ).dimensions(rightX, y, colWidth, 20).build());
        y += rowHeight;

        // --- ROW 7 ---
        // Left: Custom music volume
        this.addDrawableChild(new LabeledSliderWidget(leftX, y, colWidth, 20,
                0.0f, 1.0f, working.customMusicVolume,
                (prefix, v) -> Text.translatable("custommenubackgrounds.settings.music_volume", format(v)),
                v -> working.customMusicVolume = v));

        // Right: Open resource folder
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.open_folder"),
                button -> Util.getOperatingSystem().open(ConfigManager.RESOURCE_ROOT.toFile())
        ).dimensions(rightX, y, colWidth, 20).build());
        y += rowHeight + 6;

        // --- BOTTOM CONTROLS ---
        // Preview / Reset / Save row
        int buttonWidth = (totalWidth - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.preview"),
                button -> BackgroundManager.get().previewConfig(working.copy())
        ).dimensions(leftX, y, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.reset"),
                button -> {
                    BackgroundManager.get().revertToSaved();
                    this.working = BackgroundManager.get().getConfig().copy();
                    this.clearAndInit();
                }
        ).dimensions(leftX + buttonWidth + 4, y, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.save"),
                button -> {
                    BackgroundManager.get().applyConfig(working.copy());
                    CustomMenuBackgroundsClient.refreshMusic();
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
        Path targetDir = isMusic ? ConfigManager.MUSIC_DIR : switch (type) {
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

        MinecraftClient.getInstance().setScreen(new InGameFilePickerScreen(
                this,
                targetDir,
                ext,
                fileName -> {
                    if (isMusic) {
                        working.customMusicFile = fileName;
                    } else {
                        working.background = fileName;
                    }
                    BackgroundManager.get().previewConfig(working.copy());
                    this.clearAndInit();
                }
        ));
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

    private static List<String> availableFilesIn(Path dir, String... extensions) {
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
            names.add(0, "");
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
