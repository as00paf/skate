# AST-15: Export UX and export settings (design note)

Status: **revision 2, approved by the human (2026-11-10)**; the §8 answers are in §0.1. Revision 1 was rejected as not
grounded in this codebase (it assumed `project.json`/`config.json`, a Gradle build at export time and multi-platform
natives). This revision starts from the export code that exists. Implemented by **AST-29** (after AST-27).

## 0. Human decisions (recorded 2026-11-07, kept)

1. **Splash & icon mandatory:** the icon is **mandatory** for export, with an engine-provided default. The splash is
   **optional**, with an engine-provided default. Warn if missing at export time.
2. **Multi-platform single-export:** keep separate exports per platform (no multi-platform zip).
3. **Custom JVM args field:** not needed for now (a follow-up only if requested).

### 0.1 Answers to the §8 open questions (2026-11-10, verbatim; note approved)

1) Windows only in AST-15; Linux/macOS natives = AST-30.
2) gzip on/off only.
3) Engine splash for 2 s by default unless 'None' or a custom image.
4) Player frame-rate override in the game's settings menu IN V1 (GUI-17, skate-gameui; GameSettings.targetFps
   overrides the project cap): add it to the note's model.
5) Keep run-game.bat; jpackage launcher = AST-31.

Implementation is AST-29 (after AST-27).

## 1. What exists today

| Piece | Where | What it does |
|---|---|---|
| Wizard state | `editor/project/ExportWizard.kt` | `target: ExportTarget`, `compress`, `onlyReferenced`, `outputDir`, per-scene exclusion, `Phase` (EDITING → SAVING → RUNNING → SUCCEEDED/FAILED), `issues()` (`INVALID_OUTPUT`, `GAME_JAR_MISSING`). No ImGui, unit-tested. |
| Wizard window | `editor/ui/windows/ExportWizardWindow.kt` (300 lines) | Target radios (`WINDOWS`, `PACK_ONLY`), the game jar section (found/stale/override, AST-11), compress and only-referenced checkboxes, scene list, output folder + Browse/Default, a **read-only** graphics backend line (`backendLine`, set in Project Settings, EDT-43), progress and result view. It publishes `ProjectEvent.ExportRequested(options)`; the editor saves the project, then runs the export off the main thread. |
| Options | `ExportOptions` in `engine/utils/ProjectExporter.kt` | `target`, `excludedScenes`, `compress`, `onlyReferenced`, `outputDir`. |
| Export | `ProjectExporter.export` | Validates the output, finds the game jar (`GameJarLocator`), `AssetsPacker.pack`, verifies the pack (`PackBuilder.verify`), writes `game.settings.json` (`writeGameSettings`), copies `skate-game.jar`, writes `run-game.bat` (`java -jar skate-game.jar`). `ExportStep`/`ExportProgress`/`ExportFailure`/`ExportDetail` (string keys) report to the wizard. |
| Pack | `AssetsPacker` + `engine/assets/pack/PackBuilder.kt` | `game.bin` = atlas header + entries. Each entry is gzipped when `compress` is on, the format is not already compressed (`png jpg jpeg gif ogg mp3 glb`), and gzip saves ≥ 10 %. `manifest.json` holds a sha256 per entry. The `.skateproject` is always packed (`alwaysPacked`). |
| Project | `editor/project/Project.kt` (`<name>.skateproject`, kotlinx JSON) | Already holds the exported game's settings: `iconPath`, `graphicsBackend`, `graphicsQuality`, `msaaSamples`, `textureQuality`, `shadowQuality`, `fullscreen`, `vsync`, `physicsFPS`, `randomSeed`, `supportedLanguages`, `startupLanguage`. |
| Player settings | `engine/ui/GameSettings.kt` (`game.settings.json` next to `game.bin`) | Player-editable overrides: `locale`, `graphicsBackend`, `graphicsQuality`, `msaaSamples`, `textureQuality`, `randomSeed`, options, languages. |
| Game start | `game/GameMain.kt` | Reads the `.skateproject` **from the pack** (`loadProjectAndIcon`) and the icon bytes from `iconPath`. Creates the window (title, icon, `fullscreen`, `vsync`), sets the fixed step from `physicsFPS`, applies `GameSettings`. |
| Natives | `build.gradle.kts` | LWJGL `natives-windows` only. Bullet (`SpRelease`) and Jolt (`ReleaseSp`) Windows natives, both in `skate-game.jar` (checked by `tools/scripts/verify-game-jar.sh`). |

Gaps against the AST-15 request:

- **Splash:** the game shows none. `SplashRenderer` + `splash.glsl` exist (`RenderResourcesFactory`) and the editor
  uses them.
- **Target FPS:** no frame cap exists; `Window.loop` runs as fast as `vsync` allows.
- **Debug vs release:** nothing.
- **Physics backend:** chosen by `Physics3DFactory.defaultBackend` (`BULLET`). It is not a `Project` field, and the game
  never sets it.
- **Platforms:** Windows only; the natives make any other target impossible today.
- **Compression:** an on/off checkbox (gzip).

## 2. Where each setting lives

Rule: **what the game needs at run time goes in `Project`** (it is packed and `GameMain` already reads it). **What only
shapes the build goes in `ExportOptions`**, remembered per project in the editor's own state, not in the
`.skateproject`.

| Setting | Home | Field (new fields in **bold**) | Default |
|---|---|---|---|
| Icon | `Project` | `iconPath` (exists) | `engine:Textures/app_icon.png` (AST-26). Mandatory: a blank or missing icon falls back to the default with a warning (decision 1). |
| Splash image | `Project` | **`splashPath: String = ""`** | Blank = the engine default `engine:Textures/splash_screen.png`. Optional (decision 1). |
| Splash duration | `Project` | **`splashSeconds: Float = 2f`** | 0 disables the splash. |
| Graphics backend | `Project` | `graphicsBackend` (exists, EDT-43) | as today |
| Physics backend | `Project` | **`physicsBackend: PhysicsBackend = BULLET`** (the same field EDT-59 proposes; whichever lands first adds it) | `BULLET` |
| Target FPS | `Project` | **`targetFps: Int = 0`** (0 = uncapped; else 15..240) | 0 |
| Player frame-rate override | `GameSettings` (`game.settings.json`) | **`targetFps: Int? = null`** (null = use the project cap; 0 = uncapped; else 15..240). Set from the game's settings menu (GUI-17, skate-gameui; answer 4). | null |
| Fullscreen, VSync, quality, MSAA, languages | `Project` | exist | as today |
| Target | `ExportOptions` | `target` (exists; `WINDOWS`, `PACK_ONLY`) | `WINDOWS` |
| Build mode | `ExportOptions` | **`mode: BuildMode = RELEASE`** (`RELEASE`, `DEBUG`) | `RELEASE` |
| Compression | `ExportOptions` | `compress` (exists) | on |
| Only referenced, scenes, output folder | `ExportOptions` | exist | as today |

- **Remembering `ExportOptions`:** `ExportWizard` keeps them per project for the session today. They are saved in
  `EditorSettings` keyed by project path, so they survive restarts without dirtying the `.skateproject`. They are build
  choices, not game data.
- **Effective frame cap:** `GameSettings.targetFps ?: Project.targetFps` (one pure function,
  `FrameCap.effective(project, settings)`, used by `GameMain` and by the settings menu when the player changes it, so
  the new cap applies without a restart). Out-of-range player values are clamped to 15..240; 0 stays uncapped.
- **Schema:** the new `Project` fields have defaults, so `.skateproject` files without them decode unchanged
  (`ignoreUnknownKeys`/defaults). No schema bump is needed: `Project` has no version field, and the fields are
  additive. The project file serializer's existing decode tests are extended to cover a file without the new fields.

## 3. Wizard flow

The wizard keeps its single window (`ExportWizardWindow`, EDT-owned) and its `ExportWizard` state class. Sections, top
to bottom:

```
Target        (o) Windows game   ( ) Pack only
              Game jar: found at …/skate-game.jar   [Browse] [Clear]      (as today)

Game          Icon:   [thumb] Assets/Textures/icon.png  [Change] [Default]   ⚠ when missing → default used
              Splash: [thumb] (engine default)          [Change] [None] [Default]   Duration [2.0] s
              Graphics: OpenGL ▼    Physics: Bullet ▼    Target FPS: [Uncapped ▼ | 30 | 60 | 120 | 144 | custom]
              (values are the project's; changing them here changes the project, saved before the export)

Build         Mode: (o) Release ( ) Debug      [x] Compress   [x] Only referenced assets
              Scenes: [x] MainScene (default, always) [x] Level2 …
              Output: [ …/Builds ] [Browse] [Default]

Summary       N assets, ~X MB (estimated from AssetsPacker.selectFiles sizes), warnings list
                                                            [Cancel] [Export]
```

- **Game section edits `Project` fields** through the same commands as the Project Settings window (undoable,
  EDT-owned). The export already saves the project first (`Phase.SAVING`, `ExportFailure.SAVE_FAILED`), so the export
  and the project always agree.
  - The backend line stops being read-only: it becomes the same combo as in Project Settings.
  - Vulkan keeps its "experimental" warning (`backendLine`).
- **Summary:** asset count and an estimated size from `AssetsPacker.selectFiles` on a background job (file sizes, no
  compression estimate), refreshed when scenes or "only referenced" change.
  - It also lists warnings: missing icon (default used), missing splash file, Jolt chosen (the game falls back to
    Bullet if its native fails, `PhysicsBackendFallback`), stale game jar.
- **New `ExportWizard.Issue`:** `ICON_UNREADABLE` (blocks export only when the default icon itself cannot be read: a
  broken engine install). Everything else is a warning, per decision 1.
- **Platforms:** only "Windows game" and "Pack only" exist, matching `ExportTarget`. No Linux/macOS entries until the
  natives exist (AST-30, answer 1). Decision 2 (one export per platform) holds trivially.
- **EDT-59** (new-project wizard) uses the same physics/graphics/FPS/splash fields. AST-15 does not depend on its
  layout, only on the fields.

## 4. Export pipeline changes (`ProjectExporter`)

Steps stay `ExportStep.COLLECTING → PACKING → VERIFYING → COPYING_JAR → WRITING_LAUNCHER → DONE`. Changes:

1. **Before packing:** resolve the icon and splash.
   - A blank or unreadable `iconPath` falls back to `engine:Textures/app_icon.png`; the result reports
     `ExportWarning.ICON_DEFAULT`.
   - The same for the splash, except that blank means "use the default" and `splashSeconds = 0` means "none".
   - **Both become pack roots** (`AssetsPacker.selectFiles` already adds `iconPath`; `splashPath` is added the same
     way), so they ship even with "only referenced".
   - `engine:` resolution and packing come from AST-26.
2. **Warnings:** `ExportResult` gets `warnings: List<ExportWarning>` (string keys like `ExportDetail`, so the wizard
   shows them translated). A successful export with warnings shows them in the result view.
3. **`writeGameSettings`** is unchanged in shape. Game-side settings come from the packed `Project`. `game.settings.json`
   stays the **player's** override file. The export does not write `targetFps` into it (null = the project cap); the
   player sets it from the settings menu (GUI-17, answer 4). `physicsBackend` is not a player setting.
4. **Build mode:**
   - **Release:** `run-game.bat` = `java -jar skate-game.jar` (today). `atlas_report.txt` is not written to the output
     folder (it lists every packed path); it goes to the editor log instead.
   - **Debug:** `run-game.bat` = `java -Dskate.debug=true -jar skate-game.jar`, then `pause` on a non-zero exit so
     the console stays open. `atlas_report.txt` is kept in the output folder.
   - `GameMain` reads `skate.debug`: the logger at `DEBUG`, full stack traces, and the FPS/frame-time overlay on. Same
     jar; no separate debug build (there is no Gradle at export time; the jar comes from `GameJarLocator`).
   - The mode is not recorded in `PackManifest`. It lives only in the launcher, so a player can switch by editing one
     line.
5. **Compression:** keep gzip on/off (`PackBuilder.build(compress)`, ≥ 10 % gain rule, `alreadyCompressed` skip list).
   No zlib/lz4 choice: gzip is DEFLATE (the same family as zlib), and lz4 would add a dependency to the game jar for a
   load-time gain nobody has measured (answer 2: gzip on/off only).
   - AST-26's dedupe (identical files stored once) lands in `PackBuilder` before this.
6. **Size estimate** for the summary: `AssetsPacker.selectFiles` + file sizes. `ExportResult.packedBytes` already gives
   the real figure afterwards.

## 5. Run-time support (`GameMain` and engine core)

| Feature | Change | Owner |
|---|---|---|
| Splash | Before the first scene loads, `GameMain` draws `splashPath` (or the engine default) for `splashSeconds` with `SplashRenderer` (exists, `RenderResources.splash`), loading the image through `AssetPaths` from the pack. Skipped when `splashSeconds = 0`. Scene loading runs during it (the splash is not extra wait). | `GameMain`: TLG/GPL (game/); REN reviews the `SplashRenderer` use |
| Target FPS | `Window.loop` gets an optional frame limiter fed by the effective cap (`GameSettings.targetFps ?: Project.targetFps`, §2), changeable at run time: when it is `> 0`, sleep the rest of `1/targetFps` after `swapBuffers`, with a coarse `Thread.sleep` + short spin. It works with vsync on (the cap only lowers the rate). The editor's Play mode applies the project's cap too, so it plays like the export. | engine core (`engine/core/Window.kt`): lead assigns |
| Physics backend | `GameMain` sets `Physics3DFactory.defaultBackend = project.physicsBackend` before creating scenes. The fallback to Bullet is already handled (`PhysicsBackendFallback`) and logged. The editor's Play mode does the same. | `GameMain` + PHY review |
| Debug mode | `skate.debug` → logger level, stack traces, overlay. | `GameMain` + engine core |

## 6. Ownership and slicing

| Slice | Owner | Contents |
|---|---|---|
| AST-29 (was AST-15a) | skate-assets | `Project.splashPath`, `splashSeconds`, `targetFps` (and `physicsBackend` if EDT-59 has not added it), `ExportOptions.mode`, `ExportResult.warnings` + `ExportWarning` keys, icon/splash fallback and pack roots, launcher per mode, `atlas_report.txt` per mode, size estimate helper, `EditorSettings` persistence of `ExportOptions`. |
| EDT | skate-editor | `ExportWizardWindow` sections of §3, the Game section through the Project Settings commands, summary and warnings view, translations (`assets/strings/editor/assets*.properties`). |
| Run time | lead-assigned (TLG/GPL for `GameMain`, core for `Window`) | §5. |
| GUI-17 | skate-gameui | The frame-rate option in the game's settings menu: writes `GameSettings.targetFps` (with "Project default" = null) and applies the effective cap live (answer 4). |
| AST-30 | skate-assets (+ build) | Linux/macOS natives and export targets (answer 1). |
| AST-31 | skate-assets | jpackage launcher and exe icon (answer 5). |

Deps: AST-26/27 (`engine:` icon and splash defaults). EDT-59 shares `physicsBackend`, but AST-15 does not wait for it.

## 7. Tests (unit, no integration)

- `ExportWizard`: Game-section values round-trip to `Project`; issues and warnings for missing icon/splash; target
  and mode persistence through a fake `EditorSettings`.
- `Project` decode: a `.skateproject` without the new fields gets the defaults.
- `ProjectExporter` (temp project, fake jar search like the existing tests):
  - a blank icon → default packed + `ICON_DEFAULT` warning;
  - a splash file is packed with "only referenced";
  - `splashSeconds = 0` → no splash packed;
  - Release vs Debug launcher text;
  - `atlas_report.txt` only in Debug.
- `GameSettings` decode: a `game.settings.json` without `targetFps` gets null; `FrameCap.effective` (player null →
  project cap; player 0 → uncapped; player value wins and is clamped).
- Frame limiter: pure timing function (`sleepFor(frameStart, now, targetFps)`) with a fake clock.
- `GameMain` helpers: `physicsBackend` applied before scene creation (extracted function, fake factory).

## 8. Open questions of revision 2 (all answered in §0.1)

1. **Linux/macOS exports:** natives are Windows-only (`natives-windows`, the Bullet/Jolt Windows natives).
   Recommended: no other platform in AST-15. A separate task adds per-platform native classifiers to `gameJar`, and
   then a platform radio per decision 2.
2. **Compression choices:** recommended: keep gzip on/off. Add lz4 only if a measured load-time problem appears
   (AST-18 measures pack load times).
3. **Splash defaults:** recommended: the engine default splash (`splash_screen.png`) shows for 2 s unless the user
   picks "None". Or should the default be no splash, with the engine image only when asked?
4. **Player override of target FPS:** recommended: not in v1. The project's cap applies. A later GUI option could add
   `GameSettings.targetFps`.
5. **Exe launcher / exe icon:** the export ships `run-game.bat`, so the icon is only the window icon. Recommended: keep
   the `.bat` for now. A native launcher (jpackage) is a separate task, because it changes what is shipped (a bundled
   JRE).
