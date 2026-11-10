# EDT-59 — New Project wizard and templates (design note)

Status: **revision 2, awaiting the human's approval.** No code has been written.
Revision 2 (2026-11-10, after the lead's review): rewritten against the code on `integration/30g`; folds in EDT-70 (copy
the engine defaults or not) and the AST-26 typed asset folders (draft on `agents/assets`, `docs/design/AST-26.md`). The
human decisions of 2026-11-07 are in §9, unchanged.

## Goal

The New Project wizard offers **templates** (Empty, Skate Game, Feature Showcase, Tetris, Marble Run; later the TLG-13
demos), which separates the skate game from the Feature Showcase. It also collects the project's settings: target FPS or
uncapped, physics backend, graphics backend, AI agent/skill files, splash image, and (EDT-70) whether the engine default
assets are copied. Every template creates a project that opens in the editor and plays without errors. The editor, the
CLI and the MCP server create projects through **one** shared service, so the three cannot drift.

## 1. What exists today

| Piece | Where | What it does |
|---|---|---|
| Project file | `editor/project/Project.kt` | `@Serializable` data class saved as `<name>.skateproject` by `ProjectManager.saveProject` (`Serializer.encodeFile(FileKind.PROJECT, …)`), read by `ProjectFileReader` (migrations + project-relative paths, AST-09). Has `graphicsBackend`, `physicsFPS`, `vsync`, `fullscreen`, `iconPath`, `randomSeed`, quality settings, languages. **No** physics backend, target FPS or splash field. |
| Wizard state | `editor/project/ProjectWizard.kt` | Name and location validation (`INVALID_NAME_CHARS`, folder exists and is writable), `graphicsBackend`, `getProjectStructureItems()` (a fixed list that says `main.scene`, while the real file is `MainScene.scene`). |
| Wizard UI | `editor/ui/windows/ProjectWizardWindow.kt` | `EditorModal` (EDT-51 §3) with name, location (default `EditorPaths.defaultProjectsDir()` = `Documents/Skate Projects`), Browse (Swing `JFileChooser`), `GraphicsBackendField`, structure preview; footer Open Project / Cancel / Create. Publishes `ProjectEvent.CreateProjectRequested(name, folderPath, graphicsBackend)`. |
| Create flow | `editor/ui/handlers/ProjectActionHandler.createWithLoading` | Loading screen (EDT-56): **background** step `ProjectManager.prepareProjectFolder` (folders via `ProjectLayout.create`, `Assets/strings.properties`, `EngineAssetCopier.copyBundledAssets`), then **main-thread** step `CreateProjectCommand` → `ProjectManager.createProject`. |
| Default content | `engine/assets/PrefabsGenerator.createDefaultScenes` | Writes **two** scenes into every new project: `MainScene` (skate park, skater, cameras: `spawnDefaultsSync`) **and** the `FeatureShowcase` scene (`game/samples/FeatureShowcase.kt`). The mixing of the two is what EDT-59 removes. |
| Layout | `engine/assets/ProjectLayout.kt` | `Assets/` (with `EngineDefaults/`), `Scenes/`, `Builds/`. AST-26/27 replace `EngineDefaults/` with typed folders. |
| CLI / MCP | `tools/cli/…/SkateCliService.createProject`, `ShowcaseProjectBuilder`, `TetrisProjectBuilder`, `MarbleRunProjectBuilder`, `ProjectFiles`; `tools/mcp/…/SkateMcp.kt` (`create_project`, `create_sample`) | `skate create` re-implements project creation (blank `MainScene` from `SceneManager.createNewScene`), separately from `ProjectManager`. The three sample builders live in the `:tools:cli` Gradle subproject, which depends on the engine (`implementation(project(":"))`). **The editor cannot see them.** |
| Physics backend | `engine/physics3d/Physics3DFactory.kt` | `enum class PhysicsBackend { BULLET, SIMPLE, JOLT }`, global `Physics3DFactory.defaultBackend` (BULLET). PHY-17 slices a–e are done; slice f (per-project selection, `docs/design/PHY-17.md` §"Plumbing") waits for this note. |
| Exported game | `game/GameMain.kt` | Reads the packed `Project`: `Window(…, vsync = project.vsync)`, `fixedTimeStep = 1 / physicsFPS`, `graphicsBackend`. No frame cap: the `Window` loop (`engine/core/Window.kt`, `while (!glfwWindowShouldClose)`) is uncapped apart from VSync. |
| Splash | `engine/render/renderer/SplashRenderer.kt`, `Assets.Bundled.SPLASH` (`assets/textures/splash_screen.png`) | The engine draws a fixed splash. No per-project image. |

There is no `project.json`, no `skate-tooling/templates` module and no Gradle in a game project. A game project is data
(`.skateproject`, scenes, assets, scripts run by the script host). Templates do not ship `src/main`, `build.gradle.kts`
or Gradle wrappers.

## 2. Decision summary (proposed)

| Topic | Proposal |
|---|---|
| Templates | Empty, Skate Game, Feature Showcase, Tetris, Marble Run. TLG-13 adds Parkour, FPS and Car racing later. The Skate Game no longer contains the Feature Showcase, and the Feature Showcase is its own template. |
| Where templates live | New main-source package `src/main/kotlin/com/pafoid/skate/templates/` (**new**, owned by skate-tooling), so the editor, CLI and MCP share it. The CLI sample builders move there. |
| Creation service | **New** `templates/ProjectCreator.kt`, used by `ProjectManager`, `SkateCliService` and `SkateMcp`. It replaces the duplicated creation code in `SkateCliService.createProject`. |
| Wizard layout | `ProjectWizardWindow` stays an `EditorModal`. Template cards on the left; name, location and settings on the right; the structure preview comes from the template. |
| Target FPS | **New** `Project.targetFps: Int? = null` (null = uncapped, else 15–240). Default: uncapped. |
| Physics backend | **New** `Project.physicsBackend: PhysicsBackend = BULLET` (PHY-17 slice f). Bullet, Simple and Jolt are all selectable; Jolt is done (PHY-17 a–e) and falls back to Bullet if its natives fail to load. |
| Graphics backend | Existing `Project.graphicsBackend` and `GraphicsBackendField`, unchanged. |
| AI agent/skill files | Checkbox, default off. Copies `AGENTS.md` and skill files from **new** resources. A creation-time action, not stored in the project. |
| Splash image | Optional file picker (PNG/JPG). Copied to `Assets/Textures/splash.<ext>`. **New** `Project.splashImagePath: String = ""` (empty = engine default). Export warns if missing, per AST-15. |
| Engine defaults (EDT-70) | Checkbox "Copy engine default assets", behaviour from AST-26 §4.2. A creation-time action, as AST-26 proposes (open question 2). |
| Folder layout | AST-26 typed folders: `Assets/{Models,Shaders,Sounds,Textures,Materials,Scripts,Animations}` (+ `Prefabs` if AST-26 Q1 says so); no `EngineDefaults` folder. Templates put their files in the type folders. |

## 3. Templates

### 3.1 SPI (new, `templates/ProjectTemplate.kt`)

Signatures only; the bodies are the implementation task.

```kotlin
interface ProjectTemplate {
    val id: String                 // "empty", "skate-game", "feature-showcase", "tetris", "marble-run"
    val nameKey: String            // "template.<id>.name"   (assets/strings/editor/core*.properties)
    val descriptionKey: String     // "template.<id>.description"
    val icon: String               // an Icons glyph: the card's thumbnail icon (decision 4)
    val category: TemplateCategory // EMPTY, GAME, SAMPLE

    /** The folders and files it adds, for the wizard's structure preview. */
    fun structure(projectName: String): List<ProjectStructureItem>

    /** Writes the template's own files (scripts, sounds, strings). File I/O only: safe off the main thread. */
    fun writeFiles(ctx: TemplateContext)

    /** Builds the scenes, first = default scene. Main thread in the editor (prefab textures load), any thread headless. */
    fun buildScenes(ctx: TemplateContext): List<Scene>

    /** Template-specific overrides of the new Project (description, resolution, physics rate). */
    fun configure(project: Project) {}
}

enum class TemplateCategory { EMPTY, GAME, SAMPLE }

class TemplateContext(
    val projectDir: File,
    val prefabs: PrefabsGenerator,   // loadAssets = true in the editor, false headless (today's ShowcaseProjectBuilder)
    val settings: NewProjectSettings,
)
```

`TemplateRegistry` (**new**, `templates/TemplateRegistry.kt`) is an object listing the five templates in display order,
`get(id)` and `all()`. It has no reflection or plugin discovery; TLG-13 adds entries.

### 3.2 The five templates

| Id | Class (new file in `templates/`) | Built from | Scenes | Own files |
|---|---|---|---|---|
| `empty` | `EmptyTemplate` | today's `SkateCliService.createProject` | `MainScene`: `SceneSun.addDefaults(Scene(…))`, as `SceneManager.createNewScene` makes it | none |
| `skate-game` | `SkateGameTemplate` | the `MainScene` half of `PrefabsGenerator.createDefaultScenes` (`ScenePhysicsComponent`, `GridLines`, `EnvironmentComponent` + sky, `DayNightCycleComponent`, `SceneSun`, `spawnDefaultsSync()`: the GPL-23 park, skater, cameras) | `MainScene` | none |
| `feature-showcase` | `FeatureShowcaseTemplate` | `tools/cli/…/ShowcaseProjectBuilder.kt` (moved) + `FeatureShowcase.populate` | `FeatureShowcase` | `Assets/Scripts/ShowcaseSeed.kts` (`FeatureShowcase` copies it from `/samples/showcase/`) |
| `tetris` | `TetrisTemplate` | `tools/cli/…/TetrisProjectBuilder.kt` (moved) | `Game`, `GameOver` | `Assets/Scripts/tetris.kts`, `gameover.kts` (resources move from `tools/cli/src/main/resources/samples/tetris/` to `src/main/resources/samples/tetris/`); `Assets/Sounds/Sfx*.wav` (generated); strings |
| `marble-run` | `MarbleRunTemplate` | `tools/cli/…/MarbleRunProjectBuilder.kt` (moved) | `Run` | none |

- The scene-building code moves **as is**. Only the project-file writing (which `ProjectCreator` now does) and the
  asset-reference prefix change (AST-27 switches them to `engine:` references).
- After the move, `PrefabsGenerator.createDefaultScenes` has no caller and is removed. New editor projects get the
  Feature Showcase only from its template.
- `ProjectFiles` (`tools/cli`) moves to `templates/ProjectFiles.kt` (internal); it writes scene and project files
  project-relative (AST-09, TLG-14).
- Template sample scripts and resources stay classpath resources (`src/main/resources/samples/**`), so an installed
  editor has them without a repository checkout.

### 3.3 `ProjectCreator` (new, `templates/ProjectCreator.kt`)

```kotlin
data class NewProjectSettings(
    val template: String = "skate-game",
    val targetFps: Int? = null,
    val physicsBackend: PhysicsBackend = PhysicsBackend.BULLET,
    val graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
    val includeAiFiles: Boolean = false,
    val splashImage: File? = null,
    val copyEngineDefaults: Boolean = true,   // EDT-70; default from AST-26 Q4
    val versionControl: Boolean = false,      // §9 decision 2
)

class ProjectCreator(private val serializer: Serializer) {
    /** Background-safe step: folders, strings, engine defaults (copy mode), template files, splash, AI and VCS files. */
    fun prepare(name: String, parent: File, settings: NewProjectSettings): Result<File>

    /** Scenes + .skateproject. Returns the Project (absolute paths in memory, saved project-relative). */
    fun finish(projectDir: File, name: String, settings: NewProjectSettings, prefabs: PrefabsGenerator): Result<Project>
}
```

Steps of `prepare` (in this order):

1. Validate: `ProjectWizard` rules for the name and folder; template id known; `targetFps` null or 15–240; splash file
   exists and is PNG/JPG.
2. Folder: `<parent>/<name>/`. Fails if `<name>.skateproject` already exists (as today; see open question 6). Other
   content in the folder only produces a warning (§9 decision 3), shown by the wizard **before** Create.
3. `ProjectLayout.create(projectDir)`: AST-27's typed folders.
4. `Assets/strings.properties` with `project.name=<name>`.
5. Engine defaults: when `copyEngineDefaults`, AST-27's copier (today `EngineAssetCopier.copyBundledAssets`). Otherwise
   nothing.
6. `template.writeFiles(ctx)`.
7. Splash: copy to `Assets/Textures/splash.<ext>` (with the `_2` rule from AST-26 §3.1 on a clash).
8. AI files (§5) and version-control files (§6) when selected.

Steps of `finish`:

1. `template.buildScenes(ctx)` and write each scene to `Scenes/<name>.scene`.
2. Build the `Project`: `name`, `projectPath`, `defaultScene` = first scene, `scenesPath`, `iconPath` (AST-26:
   `engine:Textures/app_icon.png`), `graphicsBackend`, `physicsBackend`, `targetFps`, `splashImagePath`,
   `randomSeed = RandomService.newSeed()`. Then apply `template.configure(project)` and write the `.skateproject`.

Paths are built with `File(dir, name)`, not with `"\\"` concatenation as in today's `ProjectManager.createProject`.

Callers:

- **Editor.** `ProjectManager.prepareProjectFolder(name, folder, settings)` calls `prepare`. `createProject(name,
  folder, settings, preparedDir)` calls `finish` with `engine.prefabsGenerator`, then keeps today's open logic
  (`currentProject`, reseed, `ApplyMappings`, `saveProject`, `loadDefaultScenes`, `ProjectEvent.Created`).
  `CreateProjectRequested` carries `NewProjectSettings` instead of only `graphicsBackend`. `createWithLoading` keeps
  its background/main split.
- **CLI.** `skate create <name> [--dir] [--template <id>] [--physics bullet|simple|jolt] [--graphics opengl|vulkan]
  [--fps <n>|uncapped] [--engine-defaults copy|reference] [--ai-files]`. `skate sample <id>` becomes `create` with that
  template and the sample name. Samples keep AST-26 §6's reference mode. Headless: `PrefabsGenerator(engine,
  loadAssets = false)`.
- **MCP.** `create_project` gains an optional `template`, and `create_sample` delegates to `ProjectCreator`.

## 4. Project settings added to `Project`

| Field (new) | Type, default | Meaning | Used by |
|---|---|---|---|
| `physicsBackend` | `PhysicsBackend`, `BULLET` | Backend of Play and the exported game | Editor: sets `Physics3DFactory.defaultBackend` on project open and reloads open scenes when it changes (PHY-17 plumbing table). Game: `GameMain` sets it before `openProject`. Pack manifest field per PHY-17 (AST). |
| `targetFps` | `Int?`, `null` | Frame cap of the exported game; null = uncapped (VSync still applies when `vsync` is on) | `GameMain` passes it to `Window`, and the `Window` loop sleeps to the frame budget. AST-15's export settings show this field instead of keeping their own `targetFPS`. |
| `splashImagePath` | `String`, `""` | Project-relative splash image; empty = engine default | Export packs it (`ReferenceCollector`) and the game's splash draws it. The splash flow itself is AST-15's. The key is added to `AssetPaths.PATH_KEYS` (its guard test requires that). |

- Older `.skateproject` files decode with the defaults, because kotlinx serialization fills missing fields with them.
  Schema migrations stay untouched: no migration is needed.
- **Project Settings** (`ProjectSettingsWindow`, EDT-57): rows for Physics backend, Target FPS and Splash image. They
  are undoable through new `SetPhysicsBackendRequested` / `SetTargetFpsRequested` / `SetSplashImageRequested` events
  and commands, modelled on `SetGraphicsBackendRequested` / `SetPhysicsRateRequested` in
  `editor/commands/project/ProjectSettingCommands.kt`.

## 5. AI agent/skill files

When **Include AI agent/skill files** is checked, `prepare` copies **new** classpath resources from
`src/main/resources/templates/ai/` to the project root:

- `AGENTS.md`: what a Skate project is (the `.skateproject` fields, `Assets/<Type>/` folders, `Scenes/`, `Builds/`);
  how scripts work, with a link to the script API (from `docs/scripting-api.md`, copied as `Docs/scripting-api.md`);
  the headless CLI (`skate validate`, `skate run`, `skate pack`); and the rule "do not edit `.scene`/`.skateproject` by
  hand while the editor has the project open".
- `.claude/skills/skate-project/SKILL.md`: the same content as a skill.
- `.github/copilot-instructions.md`: a pointer to `AGENTS.md`.

Every copied file is rendered with `{{projectName}}` and `{{template}}` substitutions. `ProjectCreator` skips any file
that already exists (decision 3: the folder may be non-empty). The set of files is open question 5.

## 6. Version control (§9 decision 2)

- The wizard has a **Version control** checkbox, default off.
- When it is checked, `prepare` writes a `.gitignore` (`Builds/`, `.skate/cache/`, OS files) and, per the EDT-60 human
  decision "Git LFS on by default", a `.gitattributes` with LFS rules for models, textures and audio.
- Once EDT-60 phase 1 adds its JGit service, the same checkbox also runs `git init` and makes a first commit. Until
  then the wizard writes only the two files and its tooltip says so (open question 7).
- With the box unchecked, neither file is written.

## 7. Wizard UI (`ProjectWizardWindow`, rewritten)

```
┌ New Project ──────────────────────────────────────────────────────────── ? ┐
│ Templates               │ Name      [MyGame                      ] ✓       │
│ ┌──────┐ ┌──────┐       │ Location  [C:\…\Skate Projects  ] [Browse]  ✓    │
│ │ ▢    │ │ 🛹   │       │ ⚠ The folder MyGame exists and is not empty      │
│ │Empty │ │Skate │       │ ── Settings ──────────────────────────────────── │
│ └──────┘ └──────┘       │ Target FPS        (•) Uncapped  ( ) [60 ]        │
│ ┌──────┐ ┌──────┐       │ Physics           [Bullet ▾]  Bullet/Simple/Jolt │
│ │ ★    │ │ ▦    │       │ Graphics          [OpenGL ▾]  (GraphicsBackendField)
│ │Showc.│ │Tetris│       │ Splash image      [Browse…] splash.png  [x]      │
│ └──────┘ └──────┘       │ [x] Copy engine default assets            (EDT-70)│
│ ┌──────┐                │ [ ] Include AI agent/skill files                 │
│ │ ●    │                │ [ ] Version control (.gitignore)                 │
│ │Marble│                │ ── Description ───────────────────────────────── │
│ └──────┘                │ A small skatepark with the skater and cameras.   │
│                         │ ── Project structure ─────────────────────────── │
│                         │ MyGame/  MyGame.skateproject  Assets/…  Scenes/… │
├─────────────────────────┴──────────────────────────────────────────────────┤
│                                   [Open Project]  [Cancel]  [Create Project]│
└────────────────────────────────────────────────────────────────────────────┘
```

- **Cards:** `Icons` glyph + translated name (decision 4: thumbnail icons, no screenshots). Clicking a card selects it
  and updates the description and structure preview (`template.structure`). Skate Game is preselected (open question 1).
- **Settings:** each control uses the EDT-51 tooltip rules (`tipped`, `Tooltips.show`; the EDT-57 guard applies). The
  Copy engine defaults tooltip explains both AST-26 modes. The Jolt entry's tooltip explains that it falls back to
  Bullet if its natives cannot load.
- **Validation** (in `ProjectWizard`, so it is unit-tested without ImGui):
  - Create is disabled when the name is invalid, the location is invalid, `<name>.skateproject` already exists in the
    target folder, the FPS is outside 15–240, or the splash file is missing or not PNG/JPG.
  - A non-empty existing folder is a warning only (decision 3).
- **Location:** the default comes from a **new** Editor Settings field "Default project location" (decision 1). It is
  shown in `EditorSettingsWindow`, and its default value is open question 3. Browse keeps `JFileChooser`.
- **Footer, Esc/Enter, open state:** unchanged (EDT-51 §3, `EditorModal`).
- **Strings:** `wizard.project.*` and `template.<id>.name|description` in `assets/strings/editor/core.properties` and
  `core_fr.properties` (`EditorStringKeysTest`).

## 8. Tests (focused unit tests)

- `TemplateRegistry`: ids are unique and in display order; every template has its strings in en and fr.
- Each template: `ProjectCreator.prepare` + `finish` into a temp folder with headless prefabs produce a `.skateproject`
  that `ProjectFileReader.read` decodes. Every scene decodes with `Serializer`, and every reference is project-relative
  or `engine:` (the `ProjectRelativePathsGuardTest` rule). The tests run in both engine-default modes.
- Skate Game has no `FeatureShowcase` scene, and Feature Showcase has no `MainScene`. `ProjectManagerCreateTest
  .ProjectManager_CreateProject_ContainsAndOpensTheFeatureShowcase` changes to select the `feature-showcase` template.
- Settings mapping: `NewProjectSettings` → `Project.physicsBackend`, `targetFps`, `splashImagePath`, `graphicsBackend`;
  the splash file is copied; AI files and `.gitignore`/`.gitattributes` are present only when selected, and existing
  files are not overwritten.
- `ProjectWizard` validation: FPS bounds, splash type, existing project file, non-empty folder → warning only.
- CLI: `skate create --template <id>` for every id, followed by `skate validate` (no errors) and `skate run --frames N`
  (no errors), in `tools/cli` tests. These cover "opens and runs" headlessly.
- Commands: `SetPhysicsBackend` / `SetTargetFps` / `SetSplashImage` do and undo.
- Old `.skateproject` fixtures without the new fields decode with the defaults.

## 9. Decisions

Human decisions recorded on 2026-11-07:

1. **Default project location**: Configurable editor setting; default to `~/SkateProjects/` with a "Browse..." button.
2. **`.gitignore` inclusion**: Include `.gitignore` only if the version control option is selected in the wizard (not always).
3. **Existing directory handling**: Warn (don't block) if the target directory exists and is non-empty.
4. **Template preview**: Thumbnail icons for templates are sufficient — no full preview screenshots needed.

Human decisions recorded on 2026-11-10 (from EDT-70 and AST-26, applied here):

5. New Project has a setting to copy the engine default assets into the project or not.
6. Typed asset folders under `Assets/` (`Models`, `Shaders`, `Sounds`, `Textures`, `Materials`, `Scripts`,
   `Animations`); no `EngineDefaults` folder anywhere; new projects only, no migration.

## 10. Slicing

| Task | Owner | Contents | Deps |
|---|---|---|---|
| **EDT-59a** | skate-tooling | New `templates/` package: `ProjectTemplate`, `TemplateRegistry`, `NewProjectSettings`, `ProjectCreator`, the five templates; move `ShowcaseProjectBuilder`, `TetrisProjectBuilder`, `MarbleRunProjectBuilder`, `ProjectFiles` and the Tetris script resources out of `tools/cli`; `SkateCliService.createProject`, `skate sample` and the MCP tools delegate to it; CLI flags §3.3; template string keys; tests §8. | approval; sequenced with AST-27 (below) |
| **EDT-59b** | skate-editor | `ProjectManager.prepareProjectFolder` / `createProject` on `ProjectCreator`; `CreateProjectRequested` + `CreateProjectCommand` carry `NewProjectSettings`; `ProjectWizard` state and validation; `ProjectWizardWindow` rewrite (§7); "Default project location" Editor Setting; strings. | EDT-59a |
| **EDT-59c** | skate-editor | `Project.physicsBackend`, `targetFps`, `splashImagePath`; Project Settings rows + commands (§4); set `Physics3DFactory.defaultBackend` on project open and reload scenes on change (PHY-17 slice f, editor part). | EDT-59a |
| **EDT-59d** | skate-assets | `splashImagePath` in `AssetPaths.PATH_KEYS`; `physicsBackend` in the pack manifest (PHY-17 plumbing table); remove `PrefabsGenerator.createDefaultScenes`; AST-15 export settings read `Project.targetFps` / `splashImagePath`. | EDT-59c |
| **EDT-59e** | lead to assign (`engine/core`, `game/`) | `Window` frame cap from `targetFps`; `GameMain` applies `physicsBackend` and `targetFps` before `openProject`. | EDT-59c |
| **EDT-59f** | skate-tooling | AI files (§5): resources + copy step + tests. Version-control files (§6): `.gitignore`/`.gitattributes`; `git init` once EDT-60 phase 1 lands. | EDT-59a |
| **EDT-70** | skate-tooling (wizard row: skate-editor in EDT-59b) | "Copy engine default assets" through `NewProjectSettings.copyEngineDefaults` → AST-27 copier; `skate create --engine-defaults`; tests in both modes. | AST-27, EDT-59b |

**Sequencing with AST-27.** AST-27 rewrites the sample builders' references (`projectAssetsDir + Assets.Bundled.X` →
`engine:`) and `ProjectLayout`. Doing EDT-59a (the move) **first** gives AST-27 one location to edit instead of two. If
AST-27 lands first, EDT-59a moves the already-converted builders. The lead picks the order. Neither order blocks the
other.

## 11. Risks

- **Layering:** `templates/` imports `editor.project.Project` and `game.samples.FeatureShowcase`. That is allowed
  (`EngineGameLayeringGuardTest` only forbids `engine` → `game`), but `engine/**` must never import `templates`. EDT-59a
  adds that rule to the guard.
- **Thread split:** `buildScenes` loads textures in the editor, so it stays in the main-thread step of
  `createWithLoading`. A template must not do scene work in `writeFiles`.
- **Behaviour change:** new projects stop getting the Feature Showcase next to the skate MainScene. That is the
  separation EDT-59 asks for. `docs/samples.md` and the help article for the wizard are updated in EDT-59a/b.
- **AST-26 is still a draft.** If its open questions change the layout (Prefabs folder, shaders), the template file
  destinations follow `AssetFolders.folderFor` rather than hard-coded folders.

## 12. Open questions for the human

1. **Preselected template.** Recommended: **Skate Game**, which matches what a new project contains today (the skate
   MainScene). Alternative: Empty.
2. **Is the "copy engine defaults" choice stored in the project?** EDT-70's "Done when" says it is stored in project
   settings; AST-26 §4.2 proposes a creation-time action that is not stored (references are `engine:` in both modes, and
   a default can be copied later). Recommended: follow AST-26 (not stored), and the lead updates EDT-70's "Done when".
3. **Default value of "Default project location".** Decision 1 says `~/SkateProjects/`; the editor today uses
   `Documents/Skate Projects` (`EditorPaths.defaultProjectsDir`), where existing users' projects are. Recommended: keep
   `Documents/Skate Projects` as the setting's default; switch to `~/SkateProjects/` if that is what you meant.
4. **Target FPS in editor Play.** Recommended: exported game only for now. The editor stays on VSync, so the editor UI
   is never throttled. Alternative: cap Play too.
5. **Which AI files.** Recommended: `AGENTS.md`, a Claude skill (`.claude/skills/skate-project/SKILL.md`) and a
   Copilot pointer (`.github/copilot-instructions.md`); no `.mcp.json` until the MCP server ships with the editor
   install.
6. **Existing `<name>.skateproject` in the target folder.** Decision 3 says to warn and not block for a non-empty
   folder. Recommended: still **block** when that folder already holds a project file of the same name (creating would
   overwrite it), and warn for anything else.
7. **Version control before EDT-60 phase 1.** Recommended: the checkbox writes `.gitignore` and `.gitattributes` now,
   and also runs `git init` once JGit lands. Alternative: hide the checkbox until then.
8. **Templates and engine-default mode.** AST-26 §6 generates CLI samples in reference mode. Recommended: in the
   wizard, every template (Tetris, Marble Run…) follows the user's "Copy engine default assets" choice, and `skate
   sample` keeps reference mode.
