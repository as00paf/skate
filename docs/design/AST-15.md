# AST-15 — Export UX and export settings (design note)

Status: **proposal, awaiting human approval**. No code has been written.

## Goal

Refine the project export wizard flow and add per-project export settings so users can control splash image, icon, target platform, graphics backend, compression/packing, debug vs release mode, and target FPS before building an exported game.

## Decision summary

| Topic | Decision |
|---|---|
| Settings location | Per-project settings in `Project` (serialised to the project JSON). Mirrors existing `graphicsBackend` field. |
| Wizard flow | A single "Export Project" dialog (modal) with a settings panel on the left and a live preview/summary on the right. One-click "Export" at the bottom. |
| Target platforms | Windows (x86_64), Linux (x86_64), macOS (ARM64/x86_64). Platform selection determines which natives are bundled and which launcher script is generated. |
| Graphics backend | OpenGL (default) or Vulkan. Mirrors the project's `graphicsBackend` setting; the export uses whichever is selected. |
| Physics backend | Bullet, Simple, Jolt. Mirrors EDT-59's physics selection. The export strips natives for unused backends (see PHY-17). |
| Compression/packing | Options: none (raw), zlib (moderate compression, fast decompression), lz4 (fast compression/decompression). Default: zlib. |
| Debug vs release | Release (default): optimized Kotlin/JVM, no debug symbols, stripped logging. Debug: `-Dskate.debug=true`, full stack traces, verbose logging enabled. |
| Target FPS | Numeric field (15–240) or "uncapped" checkbox. Default: uncapped. When capped, the game loop uses `Math.min(targetFPS, currentFPS)` logic. |
| Splash image | File picker for a PNG/JPG splash screen shown on game launch. Stored in the export root as `splash.png`. |
| Icon | File picker for a 256×256 PNG icon. Used as the window icon on all platforms. |

## 1. Export settings model

```kotlin
data class ExportSettings(
    val name: String,                        // project name (from Project)
    val version: String,                     // from Project
    val targetPlatforms: Set<TargetPlatform> = setOf(TargetPlatform.WINDOWS),
    val graphicsBackend: GraphicsBackend = GraphicsBackend.OPENGL,
    val physicsBackend: PhysicsBackend = PhysicsBackend.BULLET,
    val compression: Compression = Compression.ZLIB,
    val mode: ExportMode = ExportMode.RELEASE,
    val targetFPS: Int? = null,              // null = uncapped
    val splashImage: Path? = null,           // relative to project assets
    val icon: Path? = null,                  // relative to project assets
    val includeAIFiles: Boolean = false      // AI agent/skill files (EDT-59)
)

enum class TargetPlatform { WINDOWS, LINUX, MACOS_ARM64, MACOS_X86_64 }
enum class Compression { NONE, ZLIB, LZ4 }
enum class ExportMode { RELEASE, DEBUG }
```

## 2. Export wizard UI

### 2.1 Layout

```
┌───────────────────────┬──────────────────────────────────┐
│ Settings (left panel) │ Preview / Summary (right panel)  │
│                       │                                  │
│ Target platforms:     │ Project: MyGame v1.0             │
│   [x] Windows         │ Platform: Windows x86_64         │
│   [ ] Linux           │ Graphics: OpenGL                 │
│   [ ] macOS ARM       │ Physics: Bullet                  │
│                       │ Compression: zlib                │
│ Graphics backend:     │ Mode: Release                    │
│   (OpenGL) ▼          │ FPS: Uncapped                    │
│                       │                                  │
│ Physics backend:      │ Output size estimate: ~45 MB     │
│   Bullet  Simple      │                                  │
│   Jolt                │ Files to include:                │
│                       │   engine.jar (12 MB)             │
│ Compression:          │   natives/ (8 MB)                │
│   None  zlib  lz4 ▼  │   assets/ (20 MB)                │
│                       │   config.json (0.5 KB)           │
│ Mode:                 │                                  │
│   Release  Debug ▼    │ Splash: splash.png ✓             │
│                       │ Icon: icon.png ✓                 │
│ Target FPS: [uncapped]│                                  │
│                       │ ┌──────────┐ ┌──────────┐       │
│ Splash image:         │ │ Cancel   │ │ Export →  │       │
│   [Browse...] splash. │ └──────────┘ └──────────┘       │
│ png                   │                                  │
│                       │                                  │
│ Icon:                 │                                  │
│   [Browse...] icon.png│                                  │
│                       │                                  │
│ Include AI files:     │                                  │
│   [ ] Yes             │                                  │
│                       │                                  │
└───────────────────────┴──────────────────────────────────┘
```

### 2.2 Behaviour

- **Live preview**: the right panel updates as settings change, showing the estimated output size and file list.
- **Platform-specific notes**: selecting a platform shows any caveats (e.g., "macOS requires codesigning for distribution").
- **Validation**: export is disabled if required fields are missing (splash/icon not yet mandatory; physics backend must have natives available).
- **Save settings to project**: the export settings are persisted in `Project.exportSettings` so they survive editor restarts.

## 3. Export process

### 3.1 Steps

1. **Validate** all settings and asset references (splash, icon paths exist).
2. **Create output directory** (`<project>/export/<platform>/<date>/`).
3. **Build the game JAR**: `gradlew.bat gameJar` with the selected mode's flags.
4. **Select natives**: copy only the selected platform's + physics backend's natives into `natives/`. Unused backends' natives are excluded (PHY-17).
5. **Copy assets**: pack all project assets using the selected compression. Update `PackBuilder` to support the new compression options.
6. **Write config.json**: serialised `ExportSettings` + project metadata at the export root.
7. **Place splash/icon**: copy splash.png and icon.png to the export root.
8. **Generate launcher script**:
   - Windows: `.bat` file that sets `java -jar engine.jar`, reads `config.json` for flags.
   - Linux/macOS: `.sh` file with the same logic, plus executable permission.
9. **Verify**: run a quick smoke test (launch the exported game in headless mode, verify it connects to the editor's debug port if in debug mode).

### 3.2 Launcher script template (Windows)

```bat
@echo off
setlocal
for /f "tokens=2 delims=:," %%a in ('findstr /c:"\"graphicsBackend\"" config.json') do set GRAPHICS=%%a
for /f "tokens=2 delims=:," %%a in ('findstr /c:"\"physicsBackend\"" config.json') do set PHYSICS=%%a
for /f "tokens=2 delims=:," %%a in ('findstr /c:"\"targetFPS\"" config.json') do set FPS=%%a

set JAVA_OPTS=-Xmx1g -Dskate.graphics=%GRAPHICS% -Dskate.physics=%PHYSICS%
if not "%FPS%"=="" set JAVA_OPTS=%JAVA_OPTS% -Dskate.fps=%FPS%
if "%MODE%"=="debug" set JAVA_OPTS=%JAVA_OPTS% -Dskate.debug=true

java %JAVA_OPTS% -jar engine.jar
endlocal
```

## 4. Cross-domain ownership

| Piece | Owner |
|---|---|
| `ExportSettings` data model, serialisation | AST |
| Export wizard ImGui window | EDT (with AST review) |
| Native selection logic (strip unused backends) | AST + PHY-17 |
| Compression options in pack builder | AST |
| Launcher script generation | TLG (tooling) |
| Project setting persistence | EDT (EDT-59) |

## 5. Test plan

1. **Settings serialisation**: create `ExportSettings`, serialise to JSON, deserialise, verify all fields match.
2. **Export with each compression mode**: export the same project with none/zlib/lz4; verify the output JARs are functionally identical and sizes differ as expected.
3. **Platform-native selection**: export for Windows + Linux; verify only Windows natives appear in the Windows export, only Linux in the Linux export.
4. **Physics-backend stripping**: export with Bullet + Jolt selected; verify both natives are present. Export with Simple only; verify no natives folder exists.
5. **Launcher script**: run the generated `.bat`/`.sh` on the target platform; verify the game starts and reads config.json correctly.
6. **Debug mode**: export in debug mode, launch, verify verbose logging is active and `-Dskate.debug=true` is set.

## 6. Risks

- **macOS codesigning**: distributing macOS builds requires codesigning and notarisation. This is out of scope for AST-15 but should be documented as a follow-up (TLG task).
- **Compression trade-offs**: lz4 is faster but may not compress as well as zlib on game assets. Benchmark both with the Feature Showcase assets before finalising defaults.
- **Large exports**: projects with many high-res textures can produce large exports. Consider adding a "texture downscale" option in a follow-up task.

## 7. Decisions

Human decisions recorded on 2026-11-07:

1. **Splash & icon mandatory**: Icon **mandatory** for export with an engine-provided default; splash **optional** with an engine-provided default. Warn if missing at export time.
2. **Multi-platform single-export**: Keep separate exports per platform (no multi-platform zip).
3. **Custom JVM args field**: Not needed for now (defer to a follow-up only if requested).

## 8. Open questions

_No remaining open questions._
