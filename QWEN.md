# SkateSim Engine - QWEN.md

## 1. Project Overview

**SkateSim** is a sophisticated 3D skateboarding simulation engine built in Kotlin using the LWJGL3 framework. It combines realistic physics simulation with advanced rendering capabilities, a full-featured editor ("Skate Lab"), and an Entity-Component-System (ECS) architecture. The engine targets production-grade tooling comparable to Godot/Unity, specialized for skateboarding game development.

### Current Version
**v0.46.0.9** (2026-04-05) — Project Management & Settings Overhaul

### Tech Stack
| Category       | Technology                                    |
|----------------|-----------------------------------------------|
| **Language**   | Kotlin (JVM 17)                               |
| **Graphics**   | LWJGL 3, OpenGL 3.3+, Dear ImGui              |
| **Physics**    | libbulletjme (Bullet Physics)                 |
| **Math**       | JOML (Java OpenGL Math Library)               |
| **DI**         | Koin                                          |
| **Async**      | Kotlin Coroutines                             |
| **Serialization** | kotlinx.serialization (JSON)               |
| **3D Models**  | Assimp (glTF 2.0, FBX, OBJ, DAE)              |
| **Testing**    | JUnit 5 + MockK                               |
| **Build**      | Gradle (wrapper)                              |

---

## 2. Rules for AI Agents

These are **HARD RULES** that must always be followed. Violating any of these is unacceptable.

### Null Safety
- **NO `!!` operator** — ever. Use safe calls (`?.`), Elvis operator (`?:`), `let`, or `lateinit var` / `Delegates.notNull()` for delayed initialization.
- If a value must be present but is initialized later, use `lateinit var`.

### Dependency Injection
- **Koin only** for all engine dependencies. Never use manual singletons, companion object `Instance` patterns, or static accessors.
- If a class needs a dependency, add it to the constructor and define it in a Koin `module`.
- All editor windows, systems, services, and managers must be registered in `app/KoinModule.kt`.

### Localization
- **NO hardcoded user-facing strings** in ImGui windows, menus, or UI logic.
- All UI strings must be keys in `resources/values/strings.properties`, accessed via `StringManager.getString("key")`.
- String keys follow pattern: `lbl.window_name.element_name`, `btn.action_name`, `component.ComponentName.property`.

### File Paths
- **Always use absolute Windows paths**: `C:\workspace\kotlin_workspace\skate\src\main\kotlin\...`
- Never use relative paths in tool calls.
- Use backslashes (`\`) for Windows paths.

### Shell Commands
- **PowerShell only** — no Bash/Sh/Zsh.
- Use `gradlew.bat` — never `./gradlew`.
- Use PowerShell cmdlets: `Remove-Item`, `Copy-Item`, `Get-ChildItem`, `Select-String`, `Expand-Archive`.
- **NEVER** use Linux utilities: `find`, `grep`, `tar -xvf`, `ls`, `cat`, `sed`, `awk`.

### Code Style
- Use explicit top-level `import` statements for all classes. Never use fully qualified names (FQN) within code body unless resolving a naming conflict.
- Idiomatic Kotlin: sealed classes, extension functions, coroutines, type-safe builders.
- SOLID principles: strict separation between Physics (Bullet), Rendering (OpenGL), and UI (ImGui).

### Performance
- **Zero-alloc hot loops**: Minimize object creation in `onUpdate` and `onRender`.
- Use LWJGL `MemoryStack` for short-lived native allocations to avoid GC pressure.
- Reuse temp buffers (ImVec2, Vector3f) for per-frame ImGui operations.

---

## 3. Development Environment Protocol

| Setting        | Value                                          |
|----------------|------------------------------------------------|
| **OS**         | Windows 10                                     |
| **Shell**      | PowerShell 7+ (Windows Terminal)               |
| **Build Tool** | `gradlew.bat` (not `./gradlew`)               |
| **Path Style** | Windows-style (`C:\path\to\file`)             |
| **Line Endings**| CRLF (`\r\n`)                                |

### Commands to AVOID
| Command        | PowerShell Equivalent                    |
|----------------|------------------------------------------|
| `find`         | `Get-ChildItem -Recurse | Where-Object`  |
| `grep`         | `Select-String` or `grep_search` tool    |
| `ls`           | `Get-ChildItem` / `dir`                  |
| `cat`          | `Get-Content`                            |
| `rm`           | `Remove-Item`                            |
| `cp`           | `Copy-Item`                              |
| `tar -xvf`     | `Expand-Archive`                         |

---

## 4. Building and Running

### Prerequisites
- Java 17+
- Gradle (wrapper included)

### Commands
```powershell
# Build the project
.\gradlew.bat build

# Run the application
.\gradlew.bat run

# Run tests
.\gradlew.bat test

# Run with verbose output
.\gradlew.bat run --info
```

### Key Configuration
- **Main class**: `com.pafoid.skate.MainKt`
- **JVM args**: `-Xverify:none` (for JNI compatibility with libbulletjme)
- **Max heap for tests**: 2GB
- **Physics timestep**: Fixed (deterministic, independent of render FPS)

---

## 5. Architecture Overview

### Hybrid ECS Pattern

The engine uses a **hybrid ECS pattern** — not pure ECS. It combines component-based data flow with pragmatic design choices:

1. **Scene extends GameObject** — the scene itself can have components (EnvironmentComponent, TimeComponent, etc.)
2. **Components store pure data** — no logic in components (except lifecycle hooks: `init`, `start`, `update`, `destroy`)
3. **Systems iterate components** — some systems (AnimationSystem, InputSystem, AudioSystem) filter and iterate GameObjects with specific component combinations
4. **Systems own some config** — EnvironmentSystem, DayNightCycleSystem, DirectionalLightSystem still own their config classes directly (pending refactor)

This hybrid approach provides immediate benefits (component-based state, clean data flow) while maintaining backward compatibility and allowing incremental migration.

### Component Catalog (16 Total)

| Component                    | Purpose                                              | Written By                    | Read By                            |
|------------------------------|------------------------------------------------------|-------------------------------|------------------------------------|
| **Transform**                | Translation, rotation, scale per GameObject          | GizmoSystem, InputSystem      | All rendering, physics, animation  |
| **RenderComponent**          | Model reference, shadow flags, render mode           | Asset loading, prefab system  | ModelRenderer, ShadowRenderer      |
| **PhysicsComponent**         | Linear/angular velocity, speed, moving/rotating state| PhysicsSystem                 | PlayerController, TrickDetector    |
| **RagdollComponent**         | Ragdoll state (ANIMATED/RAGDOLL/BLENDING), bone bodies, joints | RagdollSystem          | RagdollSystem                      |
| **InputStateComponent**      | Gameplay input: move, jump, tricks, camera, stance   | InputSystem                   | PlayerController, SkateboardPhysics|
| **EditorInputStateComponent**| Editor camera: WASD, mouse, orbit, gizmo tool modes  | InputSystem (editorUpdate)    | EditorCamera, GizmoSystem          |
| **SkeletonComponent**        | Skeleton pose, bone matrix palette for GPU skinning  | AnimationLoader, SceneSerializer | AnimationSystem, ModelRenderer  |
| **Animator**                 | Animation playback, blending, state-driven selection | Asset loading, event system   | AnimationSystem                    |
| **EnvironmentComponent**     | Sky/fog rendering settings, presets                  | EnvironmentWindow, LevelEditorSceneInitializer | SkyDomeRenderer, LightingUniformsLoader |
| **TimeComponent**            | Time of day (0-24h), time scale                      | EnvironmentWindow, GameViewWindow | DayNightCycleSystem, SkyDomeRenderer |
| **LightingStateComponent**   | Ambient light color, useAmbient toggle               | DayNightCycleSystem, EnvironmentWindow | LightingUniformsLoader, EnvironmentWindow |
| **LightingComponent**        | Computed sun direction, color, intensity, shadow intensity | DayNightCycleSystem      | (Future: rendering systems)        |
| **AudioComponent**           | Sound file path, 3D toggle, looping, volume, play/stop state | AudioSystem (playback logic) | AudioSystem                        |
| **SpriteRenderer**           | 2D sprite with color, texture, z-order               | (Manual assignment)           | 2D rendering system                |
| **NonPickable**              | Marker component — excludes GameObject from picking  | (Manual assignment)           | PickingRenderer                    |
| **ModularTile**              | Tile size for modular level building                 | Prefab system                 | Level building tools               |

### System Types

| Type              | Description                                              | Examples                                        |
|-------------------|----------------------------------------------------------|-------------------------------------------------|
| **Iterating**     | Filter GameObjects by components, update their state     | AnimationSystem, InputSystem, AudioSystem, RagdollSystem |
| **Config**        | Own configuration data, may write to components          | EnvironmentSystem, DayNightCycleSystem, DirectionalLightSystem |
| **Rendering**     | Read from components, no state ownership                 | SkyDomeRenderer, LightingUniformsLoader, ModelRenderer |
| **Editor**        | Gizmo and input handling for editor tools                | GizmoSystem, MouseControls                        |

### System Execution Order

Systems use `ExecutionPriority` enum for ordering:
- **EARLY**: InputSystem (input must be ready before gameplay)
- **DEFAULT**: AnimationSystem, PhysicsSystem, AudioSystem, DayNightCycleSystem, EnvironmentSystem, DirectionalLightSystem, RagdollSystem
- **LATE**: GizmoSystem, MouseControls

### Event-Driven Architecture

Systems communicate via an **EventSystem** (type-safe event bus using reified generics):

```kotlin
// Subscribe (type-safe)
eventSystem.subscribe<Landing> { event -> handleLanding(event.velocity) }

// Subscribe (string-based, for scripting)
eventSystem.subscribe("physics.landing") { event -> ... }

// Publish
eventSystem.publish(Landing(velocity, impactForce))
```

**Event Categories:**
| Category      | Examples                                    |
|---------------|---------------------------------------------|
| `input.*`     | `JumpPressed`, `JumpReleased`, `MovementInput`, `TrickInput` |
| `physics.*`   | `Landing`, `Takeoff`, `GroundedChanged`, `Collision` |
| `trick.*`     | `TrickDetected`, `TrickCompleted`, `TrickCancelled` |
| `game.*`      | `StateChanged`, `ScoreChanged`              |
| `ui.*`        | `GameObjectSelected`, `SelectionCleared`, `SceneOpened`, `SceneChanged`, `SceneClosed` |
| `editor.*`    | `TextureApplied`, `AnimationApplied`, `AnimationRemoved` |
| `filesystem.*`| `FileSystemChangedEvent`, `OpenSceneFileEvent` |

### ViewModel Pattern for UI State

UI windows use ViewModels for decoupled state management:
- **SelectionViewModel**: Tracks currently selected GameObject, publishes selection events
- **SceneViewModel**: Tracks current scene lifecycle, publishes scene events

Windows subscribe to ViewModels instead of querying the Scene directly.

### Data Flow Patterns

**Environment Data Flow:**
```
EnvironmentWindow (user input) → EnvironmentComponent → SkyDomeRenderer, LightingUniformsLoader
```

**Time Data Flow:**
```
EnvironmentWindow/GameViewWindow → TimeComponent → DayNightCycleSystem → LightingStateComponent → LightingUniformsLoader
```

**Lighting Data Flow:**
```
DayNightCycleSystem (computes) → LightingStateComponent → LightingUniformsLoader (uploads to shader)
```

**Input Data Flow:**
```
IInputProvider (hardware) → InputSystem → InputStateComponent → PlayerController/SkateboardPhysics
                                                                        ↓ (publishes events)
                                                                 EventSystem → Animator, TrickDetector
```

---

## 6. Project Structure

```
C:\workspace\kotlin_workspace\skate\
├── .ai\                                    # AI agent configuration
├── .gemini\                                # Gemini-specific config
├── .git\                                   # Git repository
├── .gitignore                              # Git ignore rules
├── .qwen\                                  # Qwen-specific config
├── AI_INSTRUCTIONS.md                      # Agent behavioral rules
├── build.gradle.kts                        # Gradle build configuration
├── build_check.bat                         # Build verification script
├── bulletjme.dll                           # Native Bullet Physics library
├── cleanup_sounds.ps1                      # Sound asset cleanup script
├── cleanup.bat                             # Build cleanup script
├── docs\                                   # Project documentation
│   ├── CHANGELOG.md                        # Version history
│   ├── ECS_ARCHITECTURE.md                 # ECS architecture documentation
│   ├── TODO.md                             # Task list and roadmap
│   ├── roadmap.md                          # Detailed development roadmap
│   └── obstacles.md                        # Prefab/obstacle library
├── GEMINI.md                               # Gemini agent context
├── gradle\                                 # Gradle wrapper
├── gradle.properties                       # Gradle properties
├── gradlew                                 # Gradle wrapper (Unix)
├── gradlew.bat                             # Gradle wrapper (Windows)
├── QWEN.md                                 # This file — AI agent project context
├── rename_sounds.ps1                       # Sound asset rename script
├── search_history.json                     # Search history data
├── settings.gradle                         # Gradle settings
├── settings.json                           # Application settings
├── src\
│   ├── main\
│   │   ├── kotlin\com\pafoid\skate\
│   │   │   ├── app\
│   │   │   │   ├── KoinModule.kt           # DI module definitions
│   │   │   │   └── Main.kt                 # Application entry point
│   │   │   ├── editor\
│   │   │   │   ├── commands\               # Undoable commands (TransformCommand, CreateGameObjectCommand, etc.)
│   │   │   │   ├── data\                   # Editor data classes (PrefabData, InputSettings, etc.)
│   │   │   │   ├── gizmos\                 # Gizmo implementations (TranslateGizmo, RotationGizmo, ScaleGizmo, etc.)
│   │   │   │   ├── imgui\                  # ImGui components, menus, themes
│   │   │   │   │   ├── components\         # Reusable ImGui components (EditorComponents.kt)
│   │   │   │   │   ├── menus\              # Menu builders (FileMenuBuilder, EditMenuBuilder, etc.)
│   │   │   │   │   ├── windows\            # Viewport components (ViewportRenderer, ViewportToolbar, etc.)
│   │   │   │   │   └── data\               # Theme and style data
│   │   │   │   ├── search\                 # Search Everywhere system
│   │   │   │   │   ├── history\            # Search history persistence
│   │   │   │   │   └── providers\          # Search providers (GameObject, Asset, Component, Action)
│   │   │   │   ├── systems\                # Editor services (SettingsManager, UndoRedoManager, StringManager, etc.)
│   │   │   │   ├── ui\                     # UI infrastructure
│   │   │   │   │   ├── interfaces\         # IWindowLifecycle interface
│   │   │   │   │   ├── viewmodels\         # SelectionViewModel, SceneViewModel
│   │   │   │   │   └── WindowRegistry.kt   # Central window registry
│   │   │   │   ├── windows\                # Editor windows (20 total)
│   │   │   │   │   ├── assetBrowser\       # Asset browser tabs (Textures, Models, Sounds, Animations, Prefabs)
│   │   │   │   │   └── project\            # Project management (FileSystemItem)
│   │   │   │   ├── EditorCamera.kt         # Editor camera controller
│   │   │   │   ├── LevelEditorSceneInitializer.kt
│   │   │   │   └── PrefabsGenerator.kt
│   │   │   ├── engine\
│   │   │   │   ├── animation\              # Animation loading, bone mapping
│   │   │   │   ├── assets\                 # Asset management
│   │   │   │   │   ├── data\               # Asset data classes (Sprite, Texture, Model)
│   │   │   │   │   ├── database\           # AssetDatabase, ImportPipeline, Importers
│   │   │   │   │   ├── loaders\            # AssimpLoader, ShaderLoader
│   │   │   │   │   └── serialization\      # PoseSerializer, Serializer
│   │   │   │   ├── audio\                  # Audio engine (OpenAL)
│   │   │   │   ├── core\                   # BootManager, Engine
│   │   │   │   ├── ecs\                    # ECS infrastructure
│   │   │   │   │   ├── components\         # All 16 components (Transform, RenderComponent, etc.)
│   │   │   │   │   ├── systems\            # All systems (InputSystem, AnimationSystem, PhysicsSystem, etc.)
│   │   │   │   │   ├── Scene.kt            # Scene class (extends GameObject)
│   │   │   │   │   ├── SceneManager.kt     # Scene lifecycle management
│   │   │   │   │   └── GameObject.kt       # Entity class
│   │   │   │   ├── events\                 # Event system and event definitions
│   │   │   │   ├── input\                  # Input handling
│   │   │   │   │   └── listeners\          # KeyListener, MouseListener, GamepadListener
│   │   │   │   ├── physics3d\              # Physics engine abstraction
│   │   │   │   │   └── constraints\        # Physics constraint interfaces
│   │   │   │   ├── render\                 # Rendering pipeline
│   │   │   │   │   ├── data\               # Render data classes (RenderMode, etc.)
│   │   │   │   │   ├── graph\              # Render graph system
│   │   │   │   │   ├── renderer\           # ModelRenderer, SkyDomeRenderer, ShadowRenderer, etc.
│   │   │   │   │   └── shaders\            # Shader management
│   │   │   │   ├── scenes\                 # Scene-related utilities
│   │   │   │   ├── settings\               # Settings data classes (GameplaySettings, HardwareSettings, etc.)
│   │   │   │   └── utils\                  # Utility classes (SkeletonMath, etc.)
│   │   │   ├── game\
│   │   │   │   ├── level\                  # Level management
│   │   │   │   ├── player\                 # Player state machine
│   │   │   │   ├── project\                # Project management (ProjectManager, ProjectWizard, ProjectData)
│   │   │   │   └── trick\                  # Trick detection and management
│   │   │   └── skateboard\                 # Skate-specific logic
│   │   └── resources\
│   │       ├── shaders\                    # GLSL shaders
│   │       └── values\
│   │           └── strings.properties      # Localized string keys
│   └── test\                               # Unit and integration tests
├── verify_build.bat                        # Build verification script
└── QWEN.md                                 # This file
```

---

## 7. Development Conventions

### Coding Standards

#### Null Safety
- **No `!!` operator** — use `?.`, `?:`, `let`, `run`, `also`, `takeIf`
- Use `lateinit var` for DI-injected dependencies that are set after construction
- Use `Delegates.notNull()` for properties initialized later with observable behavior

#### Dependency Injection
- All dependencies via constructor injection
- Define in Koin module (`app/KoinModule.kt`) using `single { }` or `factory { }`
- `single` for stateful services (managers, engines, systems)
- `factory` for stateless or short-lived objects (windows, components)
- Use `by inject()` or constructor `get()` in module definitions

#### Idiomatic Kotlin
- Use sealed classes for state machines and event types
- Use extension functions for utility behavior on existing types
- Use coroutines with `Dispatchers.IO` for blocking operations
- Use data classes for pure data holders
- Use type-safe builders for complex configuration

#### SOLID Principles
- Single Responsibility: One class, one concern
- Physics logic (Bullet) must be decoupled from Rendering logic (OpenGL)
- UI (ImGui) must be decoupled from game logic
- Use clear interfaces between subsystems

#### Performance
- **Zero-alloc hot loops**: No object creation in `update()` or `render()` methods
- Use `MemoryStack.stackPush()` for native LWJGL allocations
- Reuse temp buffers for ImGui operations
- Avoid `toList()`, `map()`, `filter()` in hot paths — use cached lists

### Concurrency & Performance

1. **No blocking I/O on main thread** — use Kotlin Coroutines with `Dispatchers.IO`
2. **Fixed timestep physics loop** — Bullet physics runs independently of variable render FPS
3. **Thread-safe state syncing** — use atomics, volatiles, or thread-safe queues when sharing data between physics and rendering threads
4. **Memory management** — use `MemoryStack` for short-lived native allocations

### Animation Standards

1. **SLERP for rotations** — always use `Quaternionf.slerp()` for rotation blending to avoid gimbal lock
2. **GPU skinning** — vertex deformation happens in the vertex shader, not on CPU
3. **Data decoupling** — `Animator` is independent of `Mesh` data; multiple characters can share the same animation logic
4. **Blending** — cross-fade between animation clips using configurable blend duration (default: 0.2s)

### State Management

- **Edit Mode vs Play Mode**: Clear distinction between editor tools (Gizmos, inspectors) and active simulation
- **Simulation Intent**: Prioritize skate realism (like *Skater XL* or *Session*) over arcade physics
- **Edit Mode**: Uses `EditorInputStateComponent`, `GizmoSystem`, `MouseControls`
- **Play Mode**: Uses `InputStateComponent`, `PhysicsSystem`, game-specific systems

---

## 8. Git Workflow

### Branch Lifecycle
1. **Create branch** from `master`:
   - Features: `feature/description-of-task`
   - Bugs: `bug/description-of-bug`
2. **Make small, atomic commits** — one logical change per commit
3. **Ask for review** — summarize changes and decisions made
4. **Wait for user confirmation** — user must test before merge
5. **Push feature/bug branch** to remote after approval
6. **Merge to master** — **ALWAYS ask before merging**, never merge automatically
7. **Push master** to remote after merge
8. **Clean up branches** — delete local and remote only after successful merge, with user approval
9. **Return to master** and wait for next task

### Commit Message Style
- Descriptive, present tense
- Example: `Fix GameViewWindow viewport sizing and splash screen stability`
- Include context in body if needed

### Merge Process
- **NEVER merge automatically** — always request explicit user confirmation
- After merge, push master and wait for next instructions

---

## 9. Current Project Status

### Active Phase
**Phase 2: Core Systems** — Advanced rendering, core gameplay mechanics, and core tooling

### Latest Version: v0.46.0.9 (2026-04-05)
**Project Management & Settings Overhaul**
- Implemented ProjectWizardWindow, ProjectSwitcherDialog, ProjectManager
- Replaced monolithic SettingsWindow with EditorSettingsWindow and ProjectSettingsWindow
- Split layout following IntelliJ-style patterns
- Added auto-save, key rebinding, unit system selector
- Fixed KeyBindingsWindow race condition, PrefabsGenerator return types, Project Wizard cancel loop

### Known Issues & Technical Debt
- **Remaining `!!` operators** — scattered throughout codebase, need replacement with proper null handling
- **Resource management** — potential memory leaks in asset loading/unloading
- **Animation blending** — timing issues in crossfade transitions under certain conditions
- **Renderer centralization** — `Renderer.kt` trending monolithic; render graph refactoring completed (A45.0.6)
- **Settings separation** — Engine vs Project settings separation in progress (A46.0.10)

### Pending Refactors
- **EnvironmentSystem iteration** — remove direct config ownership, iterate EnvironmentComponent (planned v0.39+)
- **InputComponent** — consolidate InputStateComponent with proper InputComponent
- **PhysicsComponent** — consolidate physics state, integrate with BulletPhysics3D
- **Complete ECS migration** — all systems iterate components, eliminate Service Locator pattern

---

## 10. Key Systems Reference

### Animation System

**Components:**
- `SkeletonComponent` — holds `SkeletonPose`, bone matrix palette for GPU skinning
- `Animator` — manages playback state, blending, event-driven animation selection

**System: `AnimationSystem`**
- Iterates GameObjects with both `SkeletonComponent` and `Animator`
- Uses cached list (`animatedObjects`) to avoid O(n) filtering every frame
- Supports animation blending with SLERP for rotations
- Global speed multiplier for slow-motion effects

**Interpolation Types:**
- **LINEAR** — lerp for position/scale
- **SLERP** — slerp for rotations (always used for quaternion blending)
- **STEP** — discrete frame changes
- **CUBIC_SPLINE** — smooth curve interpolation

**Formats:** glTF 2.0, FBX, DAE via Assimp

**Event-Driven Animation:**
Animator subscribes to events for automatic state transitions:
- `MovementInput` → walk/run based on magnitude
- `JumpPressed` → jump animation
- `Landing` → landing animation
- `Takeoff` → falling state

### Physics System

**Architecture:**
- `IPhysics3D` abstraction layer over Bullet Physics (libbulletjme)
- `PhysicsComponent` — stores linear/angular velocity, speed, moving/rotating state
- `RagdollComponent` — manages bone bodies, joints, state (ANIMATED/RAGDOLL/BLENDING)

**Skateboard Physics:**
- **Raycast suspension** — 4-point spring system with Hooke's Law: `F = kx + dv`
- **Real-world scaling** — 1.0m = 1.0 unit, gravity -9.81 m/s²
- **Stance engine** — Regular/Goofy stance, Switch/Nollie/Fakie states
- **Pop mechanics** — leveraged tail/nose impulses for ollie physics
- **Fixed timestep** — deterministic physics regardless of render framerate

**System: `PhysicsSystem`**
- Priority: `ExecutionPriority.EARLY`
- Updates `PhysicsComponent` from physics body each frame
- Syncs physics state to gameplay components

**System: `RagdollSystem`**
- Manages ragdoll state transitions
- Handles bone body creation and joint constraints

### Rendering Pipeline

**Architecture:**
- Multi-pass forward renderer with render graph system (A45.0.6 complete)
- Render passes: Shadow → Picking → Geometry → Debug
- FBO-based for editor viewport rendering

**PBR Workflow:**
- Metallic-Roughness model
- Full texture map support (albedo, normal, metallic, roughness, AO, emissive)
- Shader-driven material system

**Lighting System:**
- `LightingStateComponent` — ambient light settings
- `LightingComponent` — computed sun direction, color, intensity, shadow intensity
- `DayNightCycleSystem` — computes lighting based on time of day
- `DirectionalLightSystem` — manages directional light configuration
- `LightingUniformsLoader` — uploads lighting data to shaders

**Fog System:**
- Configured via `EnvironmentComponent`
- Distance-based atmospheric effects with density and gradient

**2D Rendering:**
- `SpriteRenderer` component with color, texture, z-order
- Sprite batching for UI elements

**Selection/Picking:**
- `PickingRenderer` — pixel-perfect picking via dedicated framebuffer
- `NonPickable` marker component excludes GameObjects from picking

### Editor ("Skate Lab")

**Windows (20 total, managed by WindowRegistry):**
| Window                    | Purpose                              |
|---------------------------|--------------------------------------|
| SceneHierarchyWindow      | GameObject tree, visibility/lock     |
| PropertiesWindow          | Component inspector and editor       |
| GameViewWindow            | Viewport with framebuffer rendering  |
| AssetBrowserWindow        | Multi-tab asset browser              |
| EnvironmentWindow         | Sky/fog settings, undo support       |
| ProfilerWindow            | Real-time performance metrics        |
| ConsoleWindow             | Dual-tab logging (Engine/Editor)     |
| PhysicsTunerWindow        | Physics parameter adjustment         |
| InputTestingWindow        | Input state visualization            |
| SystemsWindow             | System debugging                     |
| EditorSettingsWindow      | Editor configuration (split layout)  |
| ProjectSettingsWindow     | Project metadata and gameplay settings |
| KeyBindingsWindow         | Input rebinding                      |
| CommandHistoryWindow      | Undo/redo stack visualization        |
| RenderGraphWindow         | Render pass visualization            |
| AudioInspectorWindow      | Audio component management           |
| ProjectWindow             | File system browser                  |
| SearchEverywhereWindow    | Global search (Ctrl+P)               |
| TrickUIWindow             | Trick display overlay                |
| ProjectWizardWindow       | Project creation wizard              |

**Gizmos:**
- `TranslateGizmo` — 3-axis translation handles
- `RotationGizmo` — 3-axis rotation rings
- `ScaleGizmo` — 3-axis scale handles
- `SelectionGizmo` — selection visualization
- `MeasureTool` — distance measurement
- `PoseGizmo` — bone pose manipulation

**Asset Browser Tabs:**
- Textures, Models, Animations, Sounds, Prefabs

**Scene Management:**
- JSON serialization via `Serializer` and `kotlinx.serialization`
- `LevelEditorSceneInitializer` — sets up default scene with components
- Save/load with undo support

**Console System:**
- Dual-tab logging: Engine and Editor
- Log levels: INFO, WARN, ERROR, ACTION
- Search and filter capabilities

**Profiler:**
- Real-time FPS, frame time, system timing
- Graph views for performance trends

### Asset Management

**Loading and Caching:**
- `ResourceManager` — centralized resource loading with caching
- `AssetDatabase` + `ImportPipeline` — asset import system
- Importers: `TextureImporter`, `ModelImporter`, `AudioImporter`, `ShaderImporter`
- `AssimpLoader` — 3D model loading via Assimp
- `ShaderLoader` — GLSL shader loading

**Supported Formats:**
- **3D Models**: glTF 2.0, FBX, OBJ, DAE
- **Textures**: PNG, JPG
- **Audio**: WAV, OGG
- **Shaders**: GLSL (.vs, .fs)
- **Animations**: Embedded in model files, external JSON pose files
- **Poses**: JSON via `PoseSerializer`

**Hot Reloading:**
- Shader hot-reloading supported
- Asset database tracks file changes

---

## 11. Testing Strategy

### Framework
- **JUnit 5** for test framework
- **MockK** for mocking dependencies

### Test Naming Convention
- Pattern: `MethodName_Scenario_ExpectedBehavior`
- Example: `applyTailImpulse_StationaryOnGround_NoseMovesUpward`

### Test Structure
- **AAA pattern**: Arrange → Act → Assert
- One assertion per test when possible
- Descriptive test class names

### Coverage Priorities
- Physics constants and calculations
- Math operations (vector, matrix, quaternion)
- Collision detection logic
- State machine transitions
- Input processing
- Serialization/deserialization

### Testing as Validation
- Tests are used to **validate correctness** and **prevent regressions**
- Not a mandatory first step (no TDD requirement)
- Write tests for complex logic, edge cases, and bug fixes
- Run `.\gradlew.bat test` to verify

---

## 12. Common Patterns & Examples

### ECS Component Pattern

```kotlin
@Serializable
class MyComponent(
    var someValue: Float = 1.0f
) : Component() {

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        // One-time initialization
    }

    override fun start() {
        // Called after all components initialized
    }

    override fun update(dt: Float) {
        // Per-frame update (game mode)
    }

    override fun editorUpdate(dt: Float) {
        // Per-frame update (edit mode)
    }

    override fun destroy() {
        // Cleanup resources
    }
}
```

### System Pattern

```kotlin
class MySystem(
    private val dependency: SomeService
) : System(priority = ExecutionPriority.DEFAULT) {

    // Cached list to avoid O(n) filtering every frame
    private val eligibleObjects = mutableListOf<GameObject>()
    private var cacheDirty = true

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
        cacheDirty = false
    }

    override fun update(dt: Float) {
        if (cacheDirty) rebuildCache()

        for (go in eligibleObjects) {
            val component = go.getComponent<MyComponent>() ?: continue
            // Process component
        }
    }

    override fun editorUpdate(dt: Float) {
        // Same as update, or different behavior for edit mode
    }

    private fun rebuildCache() {
        eligibleObjects.clear()
        for (go in scene.gameObjectManager.gameObjects) {
            if (go.hasComponent<MyComponent>()) {
                eligibleObjects.add(go)
            }
        }
    }

    fun invalidateCache() {
        cacheDirty = true
    }
}
```

### Event Pattern

```kotlin
// Define event (inherits from GameEvent)
data class MyEvent(val data: String) : GameEvent("my.event")

// Subscribe (type-safe)
eventSystem.subscribe<MyEvent> { event ->
    handleMyEvent(event.data)
}

// Subscribe (string-based, for scripting)
eventSystem.subscribe("my.event") { event ->
    handleMyEvent((event as MyEvent).data)
}

// Subscribe once (auto-removes after first trigger)
eventSystem.subscribeOnce<MyEvent> { event ->
    handleMyEventOnce(event.data)
}

// Publish
eventSystem.publish(MyEvent("some data"))
```

### DI Pattern (Koin Module)

```kotlin
val myModule = module {
    // Singleton (one instance for entire app)
    single { MyService(get()) }

    // Factory (new instance each time)
    factory { MyWindow(get(), get()) }

    // Interface binding
    single<IMyInterface> { MyImplementation(get()) }
}
```

### Window Lifecycle Pattern

```kotlin
class MyWindow(
    private val viewModel: SelectionViewModel
) : IWindowLifecycle, KoinComponent {

    override fun onInit() {
        // Called when window is first created
    }

    override fun onSceneChanged(scene: Scene?) {
        // Called when active scene changes
    }

    override fun onUpdate(dt: Float) {
        // Called every frame (game mode)
    }

    override fun onRender() {
        // ImGui rendering
        val selected = viewModel.selectedGameObject
        ImGui.text("Selected: ${selected?.name ?: "None"}")
    }

    override fun onDestroy() {
        // Cleanup resources
    }
}
```

---

## 13. Obstacle/Prefab Library

Prefabs available in the Level Editor, categorized by geometry type and interaction physics.

### Street Category

*Architecture-based elements with linear edges and flat surfaces.*

**Ledges & Platforms:**
| Prefab          | Description                                  | Physics Notes                    |
|-----------------|----------------------------------------------|----------------------------------|
| Manual Pad      | Low-profile platform (4-10" height)          | High friction top, grindable edges |
| Standard Ledge  | Knee-high concrete block                     | Standard friction                |
| Hubba           | Ledge following stair slope                  | Angled grind surface             |
| Picnic Table    | High obstacle with top surface and benches   | Multiple grind surfaces          |
| Jersey Barrier  | Steep concrete traffic barrier               | Used for wallies or grinds       |

**Rails:**
| Prefab          | Description                                  | Physics Notes                    |
|-----------------|----------------------------------------------|----------------------------------|
| Flat Rail       | Horizontal metal bar (round or square)       | SLIPPERY, GRINDABLE              |
| Handrail        | Slanted rail for stairs/banks                | SLIPPERY, GRINDABLE              |
| Kinked Rail     | Rail with height changes (Flat-Down-Flat)    | SLIPPERY, GRINDABLE              |
| Pole Jam        | Short rail angled steeply from ground        | SLIPPERY, GRINDABLE              |

**Gaps & Sets:**
| Prefab          | Description                                  |
|-----------------|----------------------------------------------|
| Stair Set       | Configurable count (3-stair, 5-stair, etc.)  |
| Euro Gap        | Bank gapping up to higher platform           |
| Flat Gap        | Two platforms separated by void              |

### Transition Category

*Curved surfaces for vertical momentum and flow lines.*

**Ramps:**
| Prefab          | Description                                  |
|-----------------|----------------------------------------------|
| Quarter Pipe    | Single curved transition, flat to vertical   |
| Half-Pipe       | Two quarter pipes facing each other          |
| Vert Ramp       | Massive transition with 90-degree vertical   |
| Mini-Ramp       | Smaller half-pipe (no vertical section)      |
| Bank            | Flat incline (no curve)                      |
| Kicker          | Small launch ramp for horizontal distance    |

**Complex Transition:**
| Prefab          | Description                                  |
|-----------------|----------------------------------------------|
| Bowl            | Enclosed multi-sided transition pit          |
| Spine           | Two quarter pipes back-to-back, no deck      |
| Hip             | Corner where two transition walls meet       |

### Hybrid Structures

*Multi-part prefabs for park centerpieces.*

| Prefab          | Description                                  |
|-----------------|----------------------------------------------|
| Funbox          | Central platform with banks, rail, ledge     |
| Pyramid         | Four-sided bank, multi-directional approach  |
| A-Frame         | Two banks leaning with rail/ledge on top     |
| Wallride        | Vertical surface near kicker or bank         |

### Asset Metadata Tags

| Tag         | Behavior                                              |
|-------------|-------------------------------------------------------|
| `GRINDABLE` | Allows board trucks/deck to lock onto edge            |
| `SLIPPERY`  | Reduced friction (metal rails, waxed ledges)          |
| `WALLRIDE`  | Triggers sticky-physics for vertical riding           |
| `STICKY`    | High-friction surfaces (rubber, soft dirt)            |

---

*Last Updated: 2026-04-06*
*QWEN.md Version: 2.0*
