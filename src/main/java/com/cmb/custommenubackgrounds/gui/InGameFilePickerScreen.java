package com.cmb.custommenubackgrounds.gui;

import com.cmb.custommenubackgrounds.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * An in-game file browser screen that lets users select and import background
 * and music files directly inside Minecraft's UI without depending on OS
 * window focus or external file dialogs.
 */
public class InGameFilePickerScreen extends Screen {

    private final Screen parent;
    private final Path destinationDir;
    private final String[] extensions;
    private final Consumer<String> onSelected;

    private Path currentDir;
    private FileListWidget listWidget;
    private Path selectedPath;

    public InGameFilePickerScreen(Screen parent, Path destinationDir, String[] extensions, Consumer<String> onSelected) {
        super(Text.translatable("custommenubackgrounds.picker.title"));
        this.parent = parent;
        this.destinationDir = destinationDir;
        this.extensions = extensions;
        this.onSelected = onSelected;

        Path home = Paths.get(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        if (Files.exists(downloads)) {
            this.currentDir = downloads;
        } else {
            this.currentDir = destinationDir;
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // Quick jump toolbar
        int y = 30;
        int btnW = 74;
        int gap = 4;
        int startX = centerX - (btnW * 5 + gap * 4) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal(".. (Up)"), b -> navigateUp())
                .dimensions(startX, y, btnW, 20).build());

        Path home = Paths.get(System.getProperty("user.home"));
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Downloads"), b -> setDir(home.resolve("Downloads")))
                .dimensions(startX + (btnW + gap), y, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Pictures"), b -> setDir(home.resolve("Pictures")))
                .dimensions(startX + (btnW + gap) * 2, y, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Desktop"), b -> setDir(home.resolve("Desktop")))
                .dimensions(startX + (btnW + gap) * 3, y, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Mod Folder"), b -> setDir(destinationDir))
                .dimensions(startX + (btnW + gap) * 4, y, btnW, 20).build());

        // File List Widget (y=55 to height-40)
        this.listWidget = new FileListWidget(this.client, this.width, this.height - 95, 55, 20);
        this.addSelectableChild(this.listWidget);
        populateList();

        // Bottom actions
        int bottomY = this.height - 35;
        int actionW = 110;
        int actionStartX = centerX - (actionW * 3 + gap * 2) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("custommenubackgrounds.picker.select"), b -> confirmSelection())
                .dimensions(actionStartX, bottomY, actionW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("custommenubackgrounds.picker.system"), b -> openSystemDialog())
                .dimensions(actionStartX + actionW + gap, bottomY, actionW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> this.close())
                .dimensions(actionStartX + (actionW + gap) * 2, bottomY, actionW, 20).build());
    }

    private void navigateUp() {
        if (currentDir.getParent() != null) {
            setDir(currentDir.getParent());
        }
    }

    private void setDir(Path newDir) {
        if (newDir != null && Files.exists(newDir) && Files.isDirectory(newDir)) {
            this.currentDir = newDir;
            this.selectedPath = null;
            populateList();
        }
    }

    private void populateList() {
        if (this.listWidget == null) return;
        this.listWidget.children().clear();

        try {
            File currentFile = currentDir.toFile();
            File[] files = currentFile.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::isFile).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File f : files) {
                    if (f.isDirectory()) {
                        this.listWidget.children().add(new FileEntry(f.toPath(), true));
                    } else if (matchesExtension(f.getName())) {
                        this.listWidget.children().add(new FileEntry(f.toPath(), false));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomMenuBackgrounds] Error reading directory: " + e.getMessage());
        }
    }

    private boolean matchesExtension(String name) {
        String lower = name.toLowerCase();
        for (String ext : extensions) {
            if (lower.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void confirmSelection() {
        if (selectedPath == null) return;
        try {
            if (Files.isDirectory(selectedPath)) {
                setDir(selectedPath);
                return;
            }
            Path dest = destinationDir.resolve(selectedPath.getFileName().toString());
            if (!selectedPath.equals(dest)) {
                Files.copy(selectedPath, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            onSelected.accept(selectedPath.getFileName().toString());
            this.close();
        } catch (Exception e) {
            System.err.println("[CustomMenuBackgrounds] Failed to copy selected file: " + e.getMessage());
        }
    }

    private void openSystemDialog() {
        java.awt.EventQueue.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                chooser.setCurrentDirectory(currentDir.toFile());
                int result = chooser.showOpenDialog(null);
                if (result == javax.swing.JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    File f = chooser.getSelectedFile();
                    MinecraftClient.getInstance().execute(() -> {
                        selectedPath = f.toPath();
                        confirmSelection();
                    });
                }
            } catch (Exception e) {
                System.err.println("[CustomMenuBackgrounds] System chooser failed: " + e.getMessage());
            }
        });
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Перевизначаємо щоб вимкнути ванільний blur 1.21.1 - провідник був заблюрений
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Не викликаємо ванільний blur, малюємо свій напівпрозорий фон
        context.fill(0, 0, this.width, this.height, 0xDD121420);
        if (this.listWidget != null) {
            this.listWidget.render(context, mouseX, mouseY, delta);
        }
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        String pathStr = currentDir.toString();
        if (pathStr.length() > 60) {
            pathStr = "..." + pathStr.substring(pathStr.length() - 57);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(pathStr), this.width / 2, 20, 0xAAAAAA);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    // --- LIST WIDGET & ENTRIES ---
    private class FileListWidget extends AlwaysSelectedEntryListWidget<FileEntry> {
        public FileListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return this.width - 40;
        }
    }

    private class FileEntry extends AlwaysSelectedEntryListWidget.Entry<FileEntry> {
        final Path path;
        final boolean isDir;
        final String displayName;
        private long lastClickTime = 0;

        FileEntry(Path path, boolean isDir) {
            this.path = path;
            this.isDir = isDir;
            this.displayName = isDir ? "📁  " + path.getFileName().toString() + "/" : "📄  " + path.getFileName().toString();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isSelected = selectedPath != null && selectedPath.equals(path);
            int color = isSelected ? 0xFFFFA0 : (isDir ? 0x88EEFF : 0xFFFFFF);
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            context.drawTextWithShadow(textRenderer, displayName, x + 5, y + 5, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectedPath = path;
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 300) {
                confirmSelection();
                return true;
            }
            lastClickTime = now;
            return true;
        }

        public Text getNarration() {
            return Text.literal(displayName);
        }
    }
}
