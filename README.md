# CUI - CustomUI
![icon](src/main/resources/assets/cui/icon.jpg)

[Українська версія](README.uk.md)

Fabric mod for Minecraft Java Edition that allows you to completely replace the main menu background: static image, GIF, video (WebM/MP4), custom 6-sided panorama, and custom menu music — without manual resource pack editing. Also includes an extensible framework for UI customization.

## Versions (Important to read)

- **Target Minecraft Version: 1.21.11**, Fabric Loader `0.18.1`, Fabric API
  `0.141.6+1.21.11`, Yarn mappings `1.21.11+build.6`.
- The project is updated to game version 1.21.11, which is the current stable version.
  To port to newer versions (e.g., 26.x), you need to:
  1. Update `minecraft_version`, `yarn_mappings`, `loader_version`,
     `fabric_version` in `gradle.properties`;
  2. Check (and rename if necessary via "Refactor" in IntelliJ
     after Gradle sync) targets in mixins in the `com.cmb.cui.mixin` package.
     Mixin will immediately show a startup error if the target is not found.

## Project Structure

```
build.gradle, settings.gradle, gradle.properties   — Gradle/Loom configuration
src/main/resources/fabric.mod.json                 — Mod manifest
src/main/resources/cui.mixins.json                 — Mixin configuration
src/main/java/com/cmb/cui/
    client/CUIClient.java             — Entry point (ClientModInitializer)
    client/render/                    — BackgroundManager + renderers
    config/                           — ModConfig (POJO) + ConfigManager
    audio/                            — CustomMusicPlayer (OpenAL)
    gui/                              — ModSettingsScreen + ModMenuIntegration
    mixin/                            — Mixins (TitleScreen, Panorama, Music)
```

The code is intentionally modular: `BackgroundManager` knows nothing about Mixin, and
`TitleScreenMixin` knows nothing about video/GIF decoding — each
renderer (`StaticImageBackgroundRenderer`, `GifBackgroundRenderer`,
`VideoBackgroundRenderer`, `PanoramaBackgroundRenderer`) implements the
same `BackgroundRenderer` interface (`tick`/`render`/`close`), so
adding a new background type in the future is just one new class plus one `case`
in `BackgroundManager.rebuild()`.

## 1. Installing Fabric

1. Install Java 21+.
2. Download **Fabric Loader** from https://fabricmc.net/use/installer/ and
   install it for the required Minecraft version (`1.21.11`), selecting the
   "fabric-loader-..." launcher profile.
3. Download the corresponding version of **Fabric API** from Modrinth/CurseForge for
   the same game version and place the `.jar` in the `mods/` folder.

## 2. Installing the Mod

1. Build the mod (`./gradlew build`, see below) or download a pre-built
   `.jar`.
2. Place `cui-<version>.jar` in the `.minecraft/mods/` folder
   next to Fabric API.
3. Launch Minecraft via the Fabric profile — on the first run, the mod will
   automatically create the folder structure described below.

## 3. Custom File Locations

The mod creates (if they don't already exist) the following structure in the game folder:

```
.minecraft/cui/
    config/cui.json    — Configuration file
    images/            — PNG / JPG / WebP (static backgrounds) and GIF (animated)
    videos/            — WebM / MP4
    panorama/          — panorama_0.png ... panorama_5.png (custom panorama)
    audio/             — OGG (custom menu music)
    music/             — (alias for audio/ for backward compatibility)
```

Just drop a file into the corresponding folder — it will immediately appear in the
file selection list in the mod settings menu (**"Menu Backgrounds..."** button in the
bottom right corner of the main menu or via ModMenu).

## 4. File Formats

| Background Type | Formats | Note |
|-----------------|---------|------|
| Static Image    | PNG, JPG, WebP | PNG is decoded natively, JPG via `javax.imageio`, WebP requires a WebP reader in JDK/classpath |
| Animated GIF    | GIF | All frames and delays are decoded once upon loading |
| Video           | WebM, MP4 | Via built-in FFmpeg (JavaCV), decoding is a separate thread |
| Panorama        | PNG, 6 files `panorama_0..5.png` | Missing faces are replaced with vanilla ones |
| Music           | OGG (Vorbis) | Decoded completely into memory once, no streaming |

**About WebP:** Standard JDK doesn't always have a built-in `ImageIO` reader for
WebP. If `.webp` files don't load, add the `org.sejda.imageio:webp-imageio`
dependency (or equivalent) to `build.gradle` — the place is marked with a
comment in `ImageDecoding.java`.

**About Video:** `VideoBackgroundRenderer` uses JavaCV/FFmpeg
(`build.gradle`), which adds native FFmpeg binaries for Windows/Linux/macOS
into the jar — this significantly increases the build size (dozens of MB).

## 5. Settings

The settings menu (button on the main screen) allows you to:

- Select background type, specific file, and scaling mode
  (Fill / Fit / Stretch / Center);
- Adjust brightness, overlay opacity, animation/video speed,
  video volume, panorama rotation speed;
- Enable/disable and configure custom menu music;
- **Preview** — apply changes immediately without saving to disk;
- **Reset** — cancel unsaved changes (re-read `cui.json`);
- **Save** — save and apply configuration;
- Open the `cui/` folder in the OS file manager.

`cui/config/cui.json` format (example):

```json
{
  "backgroundType": "video",
  "background": "background.webm",
  "scaleMode": "fill",
  "brightness": 1.0,
  "overlayOpacity": 0.35,
  "videoLoop": true,
  "videoVolume": 0.0,
  "videoSpeed": 1.0,
  "panoramaRotationSpeed": 1.0,
  "customMusicEnabled": false,
  "customMusicFile": "",
  "customMusicLoop": true,
  "customMusicVolume": 1.0
}
```

## 6. Launching and Compilation

The project is a standard Fabric Loom Gradle project, open it in IntelliJ
IDEA via "Open" → select `build.gradle`.

```bash
# Launch client directly from Gradle (dev environment with mappings)
./gradlew runClient

# Build the final mod jar (will be in build/libs/)
./gradlew build
```

## Performance

- **Video** is never re-encoded every render frame: a separate thread
  (`VideoBackgroundRenderer` → `decodeLoop`) decodes the next frame only
  when the previous one is already consumed and the time for one video frame has passed;
  `render()` only redraws the texture already loaded into the GPU.
- **GIF** is fully decoded once upon loading (all frames + delays),
  playback is just switching already prepared frames.
- **Panorama** holds exactly 6 face textures loaded once.
- All renderers implement `close()`, which is called by
  `BackgroundManager` when exiting the main menu — GPU textures are destroyed,
  the video decoding thread stops, and the OpenAL source and custom music
  buffer are released.

## Known Limitations / TODO

- WebP requires a separate ImageIO reader (see section 4).
- Music is decoded entirely into memory — for very long tracks, it's worth
  replacing it with a streaming OpenAL buffer.
- The mod uses Mixin to integrate into `TitleScreen`, `MusicTracker`, and
  `RotatingCubeMapRenderer` (via `ScreenPanoramaMixin` and `PanoramaCancelMixin`).
