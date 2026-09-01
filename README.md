# Custom Menu Backgrounds

Fabric-мод для Minecraft Java Edition, який дозволяє повністю замінити фон
головного меню: статичне зображення, GIF, відео (WebM/MP4), власну панораму
з 6 граней та власну музику меню — без ручного редагування ресурс-паків.

## Версії (важливо прочитати)

- **Цільова версія Minecraft: 1.21.1**, Fabric Loader `0.16.9`, Fabric API
  `0.105.0+1.21.1`, Yarn mappings `1.21.1+build.3`.
- На момент написання (серпень 2026) актуальна версія гри — **26.2** (Mojang
  перейшла на річну схему версіонування, напр. 26.1 / 26.2). Я свідомо
  зібрав проєкт під 1.21.1, тому що це остання версія, для якої я міг
  впевнено перевірити точні назви мапованих класів/методів (`TitleScreen`,
  `RotatingCubeMapRenderer`, `DrawContext` тощо), які використовуються в
  mixin-ах. Портування на 26.x вимагає лише:
  1. оновити `minecraft_version`, `yarn_mappings`, `loader_version`,
     `fabric_version` у `gradle.properties`;
  2. перевірити (і за потреби перейменувати через "Refactor" у IntelliJ
     після Gradle-синку) цілі `@Redirect`/`@Inject` у
     `TitleScreenMixin.java` та `PanoramaCancelMixin.java` — Mixin одразу
     покаже помилку запуску, якщо ціль не знайдена, тобто зламані мапінги
     не проходять непомітно.

## Структура проєкту

```
build.gradle, settings.gradle, gradle.properties   — Gradle/Loom конфігурація
src/main/resources/fabric.mod.json                 — маніфест мода
src/main/resources/custommenubackgrounds.mixins.json
src/main/java/com/cmb/custommenubackgrounds/
    CustomMenuBackgroundsClient.java   — точка входу (ClientModInitializer)
    config/         — ModConfig (POJO) + ConfigManager (Gson, папки ресурсів)
    background/     — BackgroundManager + по одному рендереру на тип фону
    audio/          — CustomMusicPlayer (OGG через STBVorbis + OpenAL)
    gui/            — ModSettingsScreen + LabeledSliderWidget
    mixin/          — TitleScreenMixin, PanoramaCancelMixin
```

Код навмисно модульний: `BackgroundManager` нічого не знає про Mixin, а
`TitleScreenMixin` нічого не знає про декодування відео/GIF — кожен
рендерер (`StaticImageBackgroundRenderer`, `GifBackgroundRenderer`,
`VideoBackgroundRenderer`, `PanoramaBackgroundRenderer`) реалізує один і
той самий інтерфейс `BackgroundRenderer` (`tick`/`render`/`close`), тож
додати новий тип фону в майбутньому — це один новий клас плюс один `case`
у `BackgroundManager.rebuild()`.

## 1. Встановлення Fabric

1. Встановіть Java 21+.
2. Завантажте **Fabric Loader** з https://fabricmc.net/use/installer/ і
   встановіть його для потрібної версії Minecraft (`1.21.1`), обравши
   профіль лаунчера "fabric-loader-...".
3. Завантажте відповідну версію **Fabric API** з Modrinth/CurseForge для
   тієї ж версії гри і покладіть `.jar` у папку `mods/`.

## 2. Встановлення мода

1. Зберіть мод (`./gradlew build`, див. нижче) або завантажте вже зібраний
   `.jar`.
2. Покладіть `custommenubackgrounds-<version>.jar` у папку `.minecraft/mods/`
   поруч із Fabric API.
3. Запустіть Minecraft через профіль Fabric — при першому запуску мод сам
   створить структуру папок, описану нижче.

## 3. Розташування кастомних файлів

Мод створює (якщо їх ще немає) такі папки:

```
.minecraft/config/custommenubackgrounds.json      — файл конфігурації
.minecraft/config/custommenubackgrounds/
    images/     — PNG / JPG / WebP (статичні фони) та GIF (анімовані)
    videos/     — WebM / MP4
    panorama/   — panorama_0.png ... panorama_5.png (власна панорама)
    music/      — OGG (кастомна музика меню)
```

Просто киньте файл у відповідну папку — він одразу зʼявиться у списку
вибору файлу в меню налаштувань мода (кнопка **"Menu Backgrounds..."** у
правому нижньому куті головного меню).

## 4. Формати файлів

| Тип фону         | Формати               | Примітка |
|------------------|------------------------|----------|
| Статичне зображення | PNG, JPG, WebP      | PNG декодується нативно, JPG — через `javax.imageio`, WebP — потребує наявності WebP-рідера в JDK/класпасі (див. нижче) |
| Анімований GIF   | GIF                    | Усі кадри й затримки декодуються один раз при завантаженні |
| Відео            | WebM, MP4              | Через вбудований FFmpeg (JavaCV), декодування — окремий потік |
| Панорама         | PNG, 6 файлів `panorama_0..5.png` | Відсутні грані замінюються ванільними |
| Музика           | OGG (Vorbis)           | Декодується повністю у памʼять один раз, стрімінгу немає (для коротких треків меню це не потрібно) |

**Про WebP:** стандартний JDK не завжди має вбудований `ImageIO` рідер для
WebP. Якщо `.webp`-файли не завантажуються, додайте залежність
`org.sejda.imageio:webp-imageio` (або аналог) до `build.gradle` — місце
позначено коментарем у `ImageDecoding.java`.

**Про відео:** `VideoBackgroundRenderer` використовує JavaCV/FFmpeg
(`build.gradle`), що додає нативні бінарники FFmpeg під Windows/Linux/macOS
у jar — це суттєво збільшує розмір збірки (десятки МБ). Якщо це небажано,
можна прибрати платформо-специфічні `org.bytedeco:ffmpeg:...` рядки для
платформ, які вам не потрібні.

## 5. Налаштування

Меню налаштувань (кнопка на головному екрані) дозволяє:

- вибрати тип фону, конкретний файл і режим масштабування
  (Fill / Fit / Stretch / Center);
- регулювати яскравість, затемнення (overlay), швидкість
  анімації/відео, гучність відео, швидкість обертання панорами;
- увімкнути/вимкнути й налаштувати кастомну музику меню;
- **Preview** — застосувати зміни одразу, без збереження на диск;
- **Reset** — скасувати незбережені зміни (перечитати `custommenubackgrounds.json`);
- **Save** — зберегти й застосувати конфігурацію;
- відкрити папку `config/custommenubackgrounds/` в файловому менеджері ОС.

Формат `config/custommenubackgrounds.json` (приклад):

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

## 6. Запуск і компіляція

Проєкт — стандартний Fabric Loom Gradle-проєкт, відкривається в IntelliJ
IDEA через "Open" → виберіть `build.gradle`.

```bash
# генерація gradlew-скриптів і wrapper-jar (потрібен локально встановлений
# Gradle 8.8+ один раз; пісочниця, в якій готувався цей проєкт, не мала
# мережевого доступу до services.gradle.org, тому wrapper-jar не включено)
gradle wrapper --gradle-version 8.8

# запуск клієнта прямо з Gradle (dev-середовище з мапінгами)
./gradlew runClient

# збірка готового мод-jar (буде у build/libs/)
./gradlew build
```

Якщо ви відкриваєте проєкт в IntelliJ IDEA з увімкненим Gradle-плагіном,
IDE зазвичай сама пропонує згенерувати wrapper при першому імпорті — тоді
окремий крок `gradle wrapper` не потрібен.

## Продуктивність

- **Відео** ніколи не перекодовується щокадру рендеру: окремий потік
  (`VideoBackgroundRenderer` → `decodeLoop`) декодує наступний кадр лише
  тоді, коли попередній уже спожитий і минув час одного кадру відео
  (з урахуванням `videoSpeed`); `render()` лише перемальовує вже
  завантажену в GPU текстуру.
- **GIF** повністю декодується один раз при завантаженні (усі кадри +
  затримки), відтворення — це просто перемикання вже готових кадрів.
- **Панорама** тримає рівно 6 текстур граней, завантажених один раз.
- Усі рендерери реалізують `close()`, який викликається
  `BackgroundManager` при виході з головного меню
  (`onTitleScreenClosed()`) — GPU-текстури знищуються
  (`TextureManager.destroyTexture`), потік декодування відео
  зупиняється (`Thread.interrupt()` + `grabber.release()`), OpenAL-джерело
  й буфер кастомної музики звільняються (`alDeleteSources`/`alDeleteBuffers`).

## Відомі обмеження / TODO

- WebP вимагає окремого ImageIO-рідера (див. розділ 4).
- Музика декодується повністю в памʼять — для дуже довгих треків варто
  замінити на стрімінговий OpenAL-буфер (кілька буферів у черзі).
- Мод не намагається сумісно працювати з іншими модами, що теж
  мixin-ять `TitleScreen.render` тим самим методом одночасно — у разі
  конфлікту порядок `@Redirect` вирішується Mixin `priority`
  (за потреби додайте `priority` в `@Mixin`).
