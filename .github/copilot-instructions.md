# Copilot Instructions for `skate`

## Environment assumptions (important)

- Primary dev environment is **WSL/Linux shell**.
- This repo’s `gradlew` currently has **CRLF** line endings, so in WSL use `gradlew.bat` via `cmd.exe /c` unless/until
  line endings are normalized.
- Prefer Linux tooling (`rg`, `grep`, `find`, `sed`, `awk`) when working from WSL.
- Runtime dependencies are currently configured for **Windows natives** in Gradle (`lwjgl` classifiers and
  `imgui-natives-windows`), so do not assume Linux-native runtime setup.

## Build, test, and lint commands

Run from repository root.

```bash
cmd.exe /c gradlew.bat compileKotlin --no-daemon
cmd.exe /c gradlew.bat test
cmd.exe /c gradlew.bat build
cmd.exe /c gradlew.bat run
```

Run a single test class:

```bash
cmd.exe /c gradlew.bat test --tests "com.pafoid.skate.engine.physics3d.SkateboardPhysicsTest"
```

Run a single test method:

```bash
cmd.exe /c gradlew.bat test --tests "com.pafoid.skate.engine.ecs.systems.RagdollSystemTest.update_RagdollStateIsRagdoll_UpdatesBoneTransformsFromPhysics"
```

If/when `gradlew` line endings are normalized for WSL, equivalent commands are:

```bash
./gradlew compileKotlin --no-daemon
./gradlew test
./gradlew build
./gradlew run
```

Lint:

- No dedicated lint plugin/task is configured in `build.gradle.kts` (no `ktlint`, `detekt`, `spotless`).

## High-level architecture

- **Boot + DI:**
    - `Main.kt` starts Koin with `appModule`, `inputModule`, `engineModule` from `app/KoinModule.kt`.
    - `Engine` is injected and started from Koin; avoid manual construction for DI-managed services.
- **Engine frame loop:**
    - `engine/core/Engine.kt` drives updates.
    - `BootManager` initializes renderer/splash/settings/scene, then transitions to running state.
    - Runtime flow is `EditorWorkspace.update(dt)` → `Scene.update(dt)` → renderer → ImGui layer.
- **Hybrid ECS core:**
    - `Scene` extends `GameObject`, so scene-level state lives in components (time, environment, lighting) plus scene
      camera and physics world.
    - Systems derive from `engine/ecs/systems/System` and are ordered by `ExecutionPriority` via `SystemManager`.
- **Scene + resource lifecycle:**
    - `SceneManager` owns open scenes and active scene index.
    - Opening/closing scenes emits scene events; when all scenes are closed it clears resource cache via
      `ResourceManager`.
- **Editor orchestration:**
    - `EditorWorkspace` lazily initializes/registers core + editor systems once a scene exists.
    - `WindowRegistry` is the central list of dockable/editor windows.
- **Event-driven interactions:**
    - `engine/core/EventSystem.kt` supports typed and string-based subscriptions.
    - Event classes are grouped under `engine/events` using namespaced IDs (`input.*`, `physics.*`, `trick.*`,
      `scene.*`, etc.).
- **Dual edit/play mode behavior:**
    - `engine.runtimePlaying` controls runtime mode.
    - `ViewportToolbar` toggles play/pause/stop.
    - `CameraManager` switches active camera between editor camera and scene camera.
- **Physics stepping:**
    - `BulletPhysics3D.update` uses fixed-timestep accumulator stepping.
    - `PhysicsSystem` runs EARLY and syncs body state into `PhysicsComponent` for downstream gameplay systems.

## Key conventions to follow

- **Koin-first DI (mandatory):**
    - Inject dependencies via constructors and register in `KoinModule.kt`.
    - Avoid manual singletons/static “instance” patterns for engine/editor systems.
- **No hardcoded user-facing strings (mandatory):**
    - Use `StringManager.getString(...)`.
    - Add new keys under `src/main/resources/values/strings.properties` (and locale files as needed).
- **Event-driven editor actions:**
    - Prefer `UI -> Event -> ActionHandler -> Command -> UndoRedoManager`.
    - For state-changing editor operations, use commands rather than direct mutation.
- **Command pattern conventions:**
    - Commands live in `editor/commands/`.
    - Follow existing command contract (`execute`, `undo`, display metadata methods).
    - Keep one command per file.
- **ECS scheduling discipline:**
    - Choose the correct `ExecutionPriority`.
    - If a system caches scene/gameobject references, implement/maintain `invalidateCaches()`.
- **Edit vs Play separation:**
    - Keep editor-only behaviors out of runtime simulation paths.
    - Gate editor interactions around runtime mode where applicable.
- **Null-safety + Kotlin style:**
    - Avoid `!!`; use safe calls/Elvis/explicit checks.
    - Keep explicit imports; avoid FQNs in code bodies except for conflict resolution.
- **Performance-sensitive loops:**
    - Minimize per-frame allocations in update/render hot paths.
    - Reuse temp vectors/ImGui structs where possible.
- **Dead code cleanup:**
    - When refactoring architecture patterns, remove old callbacks/interfaces/helpers that no longer have call sites.

## Practical change map (where to edit)

- DI wiring / service registration: `src/main/kotlin/com/pafoid/skate/app/KoinModule.kt`
- Engine lifecycle: `src/main/kotlin/com/pafoid/skate/engine/core/`
- ECS core: `src/main/kotlin/com/pafoid/skate/engine/ecs/`
- Editor windows/handlers/commands: `src/main/kotlin/com/pafoid/skate/editor/`
- Events: `src/main/kotlin/com/pafoid/skate/engine/events/`
- Localization strings: `src/main/resources/values/strings*.properties`

## Test conventions

- Tests use JUnit 5 + MockK.
- Common naming style is scenario-based (for example `Method_Scenario_ExpectedBehavior`).
- When adding logic-heavy behavior (physics, state transitions, event flows), add or update focused tests in
  `src/test/kotlin`.
- Prefer targeted test execution while iterating:
    - `cmd.exe /c gradlew.bat test --tests "fully.qualified.TestClass"`
    - `cmd.exe /c gradlew.bat test --tests "fully.qualified.TestClass.testMethod"`

## Common implementation playbooks

- **Adding a new editor action (state-changing):**
    1. Define/extend event type in `engine/events/*Action*.kt`
    2. Subscribe/handle in the relevant `*ActionHandler`
    3. Implement a command in `editor/commands/`
    4. Execute through `UndoRedoManager`
    5. Register handler/service in `KoinModule.kt`
    6. Add/adjust tests

- **Adding a new UI window or UI control:**
    1. Add localized string keys first
    2. Implement/update window in `editor/ui/windows/`
    3. Register in `KoinModule.kt`
    4. If dockable, wire into `WindowRegistry`
    5. Add any required action/event/command plumbing

- **Adding/changing ECS system behavior:**
    1. Update component data model if needed
    2. Update system with correct `ExecutionPriority`
    3. Handle cache invalidation if game object sets can change
    4. Validate ordering interactions with other systems
    5. Add focused system/component tests

## Known gotchas

- In WSL, direct `./gradlew` may fail until line endings are normalized; use `cmd.exe /c gradlew.bat ...` by default.
- Runtime/native dependencies are Windows-targeted in current Gradle config, so runtime behavior is expected to align
  with Windows host setup.
- Legacy docs (`QWEN.md`, `AI_INSTRUCTIONS.md`) contain Windows/PowerShell guidance; for this environment prefer WSL
  command style while keeping architecture/convention rules intact.

## Reference docs to honor

- `AI_INSTRUCTIONS.md`
- `QWEN.md`
- `GEMINI.md`
- `docs/ECS_ARCHITECTURE.md`
- `docs/TODO.md`
