# EDT-59 — New Project wizard and templates (design note)

Status: **proposal, awaiting human approval**. No code has been written.

## Goal

Design a new project creation wizard with templates that separates the skate game from the Feature Showcase as distinct template options. The wizard collects settings (target FPS/uncapped, physics backend, graphics backend, include AI agent/skill files, splash screen image) and generates a project from a skate-tooling template. Each generated template must create a project that opens in the editor and runs without errors.

## Decision summary

| Topic | Decision |
|---|---|
| Templates | Empty, Skate game, Feature Showcase, Tetris, Marble Run (later demos as needed). The skate game template is now separate from the Feature Showcase. |
| Settings collection | Modal dialog with a settings panel on the left and a live preview/summary on the right (mirroring AST-15's export wizard pattern). |
| Target FPS | Numeric field (15–240) or "uncapped" checkbox. Default: uncapped. Stored in `project.json` as `"targetFPS": null`. |
| Physics backend | Bullet, Simple, Jolt (Jolt available once PHY-17 lands). Default: Bullet. Stored in `Project.graphicsBackend` → new field `Project.physicsBackend`. |
| Graphics backend | OpenGL (default), Vulkan. Mirrors existing `graphicsBackend` setting. |
| AI agent/skill files | Checkbox to include AI agent/skill files (`AGENTS.md`, skill definitions). Default: unchecked. Stored as `"includeAIFiles": true/false`. |
| Splash screen image | File picker for PNG/JPG. Optional (warn if missing at export time, per AST-15). Stored as a relative path in `project.json`. |

## 1. Template structure

Each template is a directory under `skate-tooling/templates/<name>/` containing:

```
templates/
├── empty/
│   ├── project.json              # base project config (overridden by wizard settings)
│   ├── src/
│   │   └── main/
│   │       └── kotlin/
│   │           └── Main.kt       # minimal entry point
│   ├── assets/                   # empty assets folder
│   ├── scenes/                   # empty scenes folder
│   └── build.gradle.kts          # base Gradle config (copied as-is)
├── skate-game/
│   ├── project.json
│   ├── src/main/kotlin/
│   │   ├── Main.kt               # entry point with skate game init
│   │   └── game/
│   │       ├── GameApp.kt        # SkateGame application setup
│   │       └── prefabs/          # skate-specific prefabs (from GPL-23)
│   ├── assets/
│   │   ├── models/               # base skate models
│   │   ├── materials/            # default materials
│   │   └── scenes/               # MainScene (park from GPL-23)
│   └── build.gradle.kts
├── feature-showcase/
│   ├── project.json
│   ├── src/main/kotlin/
│   │   └── Main.kt
│   ├── assets/                   # full showcase assets (models, textures, sounds)
│   ├── scenes/                   # MainScene + demo scenes
│   └── build.gradle.kts
├── tetris/
│   ├── project.json
│   ├── src/main/kotlin/
│   │   └── Main.kt
│   ├── assets/
│   └── scenes/
│       └── TetrisScene.skn
└── marble-run/
    ├── project.json
    ├── src/main/kotlin/
    │   └── Main.kt
    ├── assets/
    └── scenes/
        └── MarbleRunScene.skn
```

### 1.1 Template registry

A `TemplateRegistry` in skate-tooling manages available templates:

```kotlin
data class Template(
    val id: String,                // "empty", "skate-game", "feature-showcase", etc.
    val name: String,              // display name (e.g., "Skate Game")
    val description: String,       // shown in the wizard
    val iconPath: Path?,           // optional thumbnail icon
    val category: TemplateCategory // GAME, DEMO, EMPTY
)

enum class TemplateCategory { EMPTY, GAME, DEMO }

class TemplateRegistry {
    private val templates: Map<String, Template> = mapOf(
        "empty" to Template("empty", "Empty Project", "Start from scratch with no assets or scenes.", null, TemplateCategory.EMPTY),
        "skate-game" to Template("skate-game", "Skate Game", "A skateboarding game project with the park scene and skate prefabs.", null, TemplateCategory.GAME),
        "feature-showcase" to Template("feature-showcase", "Feature Showcase", "The engine feature showcase demo (park, lighting, particles).", null, TemplateCategory.DEMO),
        "tetris" to Template("tetris", "Tetris", "A Tetris sample project demonstrating 2D rendering and UI.", null, TemplateCategory.DEMO),
        "marble-run" to Template("marble-run", "Marble Run", "A physics-based marble run demo.", null, TemplateCategory.DEMO)
    )

    fun get(id: String): Template? = templates[id]
    fun getAll(): List<Template> = templates.values.toList()
}
```

## 2. Wizard UI design

### 2.1 Layout

```
┌───────────────────────────────┬──────────────────────────────────────┐
│  New Project                  │  Preview                             │
│                               │                                      │
│  Template:                    │  Project name: MyGame                │
│  ┌────────┐ ┌────────┐       │  Location: /home/user/projects/      │
│  │ Empty  │ │ Skate  │       │                                      │
│  └────────┘ └────────┘       │  Settings:                           │
│  ┌────────┐ ┌────────┐       │    Target FPS: [uncapped]            │
│  │Feature │ │ Tetris │       │    Physics: Bullet ▼                 │
│  │Showcase│ │ Marble │       │    Graphics: OpenGL ▼                │
│  └────────┘ └────────┘       │    AI files: [ ] Yes                 │
│                               │    Splash: [Browse...]               │
│  Settings:                    │                                      │
│                               │  Description:                        │
│  Target FPS:                  │  A skateboarding game project with   │
│  [uncapped] ▼                 │  the park scene and skate prefabs.   │
│       30                      │                                      │
│       60                      │ ┌──────────┐ ┌──────────┐          │
│      120                      │ │ Cancel   │ │ Create →  │          │
│     uncapped                  │ └──────────┘ └──────────┘          │
│                               │                                      │
│  Physics backend:             │                                      │
│  (Bullet) ▼                   │                                      │
│    Bullet                     │                                      │
│    Simple                     │                                      │
│    Jolt*                      │                                      │
│                               │                                      │
│  Graphics backend:            │                                      │
│  (OpenGL) ▼                   │                                      │
│    OpenGL                     │                                      │
│    Vulkan                     │                                      │
│                               │                                      │
│  Include AI files:            │                                      │
│  [ ] Yes                      │                                      │
│                               │                                      │
│  Splash image:                │                                      │
│  [Browse...] splash.png       │                                      │
│                               │                                      │
│  Icon (optional):             │                                      │
│  [Browse...] icon.png         │                                      │
│                               │                                      │
└───────────────────────────────┴──────────────────────────────────────┘

* Jolt only available after PHY-17 is merged. Shown as disabled with a tooltip.
```

### 2.2 Behaviour

- **Template selection**: clicking a template card selects it and updates the preview panel (description, settings).
- **Live validation**: "Create" button is disabled if:
  - No template selected.
  - Project name is empty or contains invalid characters (`/`, `\`, `:`).
  - Location path doesn't exist or isn't writable.
  - Physics backend is Jolt but PHY-17 hasn't been merged (shown as a warning, not a hard block).
- **Splash image**: optional; if provided, the path is validated (file must exist) and stored relative to the project root in `project.json`.

## 3. Project generation

### 3.1 Steps

1. **Validate inputs**: template, name, location, settings.
2. **Create directory structure**: `<location>/<name>/` with subdirectories from the selected template.
3. **Apply settings overrides**: modify `project.json` in the template with the user's settings:
   ```json
   {
     "name": "MyGame",
     "version": "1.0.0",
     "targetFPS": null,
     "physicsBackend": "bullet",
     "graphicsBackend": "opengl",
     "includeAIFiles": false,
     "splashImage": "assets/splash.png"
   }
   ```
4. **Copy template files**: recursively copy all files from the template directory to the project root.
5. **Apply AI file inclusion**: if `includeAIFiles` is true, add `AGENTS.md`, skill definition files and any other AI-related files to the project root.
6. **Copy splash image**: if a splash image was selected, copy it to `<project>/assets/splash.png`.
7. **Generate Gradle wrapper**: ensure `gradlew.bat` / `gradlew` is present (copied from skate-tooling's template).
8. **Open in editor** (optional): offer to open the newly created project immediately.

### 3.2 Settings override logic

```kotlin
class ProjectGenerator {
    fun generate(template: Template, name: String, location: Path, settings: NewProjectSettings): Path {
        val projectDir = location.resolve(name)
        
        // Create directory structure
        createTemplateStructure(template, projectDir)
        
        // Override project.json with user settings
        val projectJson = projectDir.resolve("project.json")
        val baseConfig = loadTemplateConfig(template, projectJson)
        val mergedConfig = mergeConfigs(baseConfig, settings)
        saveJSON(projectJson, mergedConfig)
        
        // Handle splash image
        if (settings.splashImage != null) {
            copyFile(settings.splashImage, projectDir.resolve("assets/splash.png"))
        }
        
        // Handle AI files
        if (settings.includeAIFiles) {
            copyAITemplateFiles(projectDir)
        }
        
        return projectDir
    }

    private fun mergeConfigs(base: Map<String, Any>, settings: NewProjectSettings): Map<String, Any> {
        return base.toMutableMap().apply {
            this["targetFPS"] = settings.targetFPS  // null for uncapped
            this["physicsBackend"] = settings.physicsBackend.name.lowercase()
            this["graphicsBackend"] = settings.graphicsBackend.name.lowercase()
            this["includeAIFiles"] = settings.includeAIFiles
        }
    }
}
```

## 4. Project JSON format (updated)

The `project.json` schema is extended with new fields:

```json
{
  "name": "MyGame",
  "version": "1.0.0",
  "targetFPS": null,
  "physicsBackend": "bullet",
  "graphicsBackend": "opengl",
  "includeAIFiles": false,
  "splashImage": "assets/splash.png",
  "scenes": ["scenes/MainScene.skn"],
  "mainClass": "com.example.mygame.Main"
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | string | — | Project display name |
| `version` | string | `"1.0.0"` | Project version |
| `targetFPS` | int/null | `null` | Target FPS; null = uncapped |
| `physicsBackend` | string | `"bullet"` | One of: `bullet`, `simple`, `jolt` |
| `graphicsBackend` | string | `"opengl"` | One of: `opengl`, `vulkan` |
| `includeAIFiles` | boolean | `false` | Whether AI agent/skill files are included |
| `splashImage` | string/null | `null` | Relative path to splash image in assets |
| `scenes` | array | `[]` | List of scene file paths |
| `mainClass` | string | — | Main Kotlin class for the exported game |

## 5. Template evolution

### 5.1 Current templates (EDT-59)

| Template | Description | Dependencies |
|---|---|---|
| Empty | Blank project with Gradle setup and minimal `Main.kt` | None |
| Skate Game | Park scene, skate prefabs, basic game loop | GPL-23 (park), GPL-24 (game systems) |
| Feature Showcase | Full showcase demo (lighting, particles, post-processing) | REN-27, REN-29 |
| Tetris | 2D Tetris sample (UI, rendering) | GUI-xx, REN-xx |
| Marble Run | Physics-based marble run demo | PHY-17, ANI-xx |

### 5.2 Future templates (post-EDT-59)

- **Multiplayer demo**: template with NET-01 networking setup.
- **VR demo**: template with VR input and rendering configuration.
- **Custom user template**: users can save their current project as a template for reuse.

## 6. Test plan

1. **Empty template**: create an empty project; verify it opens in the editor without errors; verify `project.json` has correct default settings.
2. **Skate game template**: create a skate game project; verify MainScene loads and the park is visible; verify skate prefabs are present in the asset browser.
3. **Feature Showcase template**: create a showcase project; verify all demo scenes load; verify lighting, particles and post-processing work correctly.
4. **Settings override**: create a project with custom FPS (60), Jolt physics (if available) and Vulkan graphics; verify `project.json` reflects these settings and the game runs with them.
5. **Splash image**: create a project with a splash image; verify it appears on game launch (in exported game).
6. **AI files**: create a project with AI files included; verify `AGENTS.md` and skill files are present in the project root.
7. **Invalid inputs**: try creating a project with an empty name, invalid characters in the name, or non-existent location; verify the wizard blocks creation and shows appropriate errors.

## 7. Risks

- **Template bloat**: each template adds files to the repository. Keep templates minimal; assets should be downloadable (AST-16) rather than bundled.
- **Jolt availability**: if PHY-17 isn't merged before EDT-59, Jolt must be shown as disabled in the wizard with a tooltip explaining it will be available after PHY-17.
- **Splash image path resolution**: splash images are stored as relative paths in `project.json`. Ensure the editor resolves them correctly at both design time and export time.
- **Cross-platform Gradle wrapper**: templates must include both `.bat` (Windows) and `.sh` (Linux/macOS) Gradle wrappers; verify they work on all target platforms.

## 8. Decisions

Human decisions recorded on 2026-11-07:

1. **Default project location**: Configurable editor setting; default to `~/SkateProjects/` with a "Browse..." button.
2. **`.gitignore` inclusion**: Include `.gitignore` only if the version control option is selected in the wizard (not always).
3. **Existing directory handling**: Warn (don't block) if the target directory exists and is non-empty.
4. **Template preview**: Thumbnail icons for templates are sufficient — no full preview screenshots needed.

## 9. Open questions

_No remaining open questions._
