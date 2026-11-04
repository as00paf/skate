# Copilot Instructions

Kotlin/JVM 17 game engine + editor ("Skate") using LWJGL (OpenGL, GLFW, OpenAL, Assimp, stb), Dear ImGui, JOML, and Bullet physics (libbulletjme). Windows-only natives (`natives-windows`).

## Build, run, test

- Build: `.\gradlew.bat build`
- Run editor: `.\gradlew.bat run` (main class `com.pafoid.skate.MainKt`)
- Standalone game fat jar: `.\gradlew.bat gameJar` (main class `com.pafoid.skate.game.GameMainKt`)
- All tests: `.\gradlew.bat test` (JUnit 5, mockk, kotlinx-coroutines-test)
- Single class: `.\gradlew.bat test --tests "com.pafoid.skate.architecture.UiMutationPipelineGuardTest"`
- Single method: `.\gradlew.bat test --tests "com.pafoid.skate.architecture.EngineLayeringGuardTest.EngineLayering_SceneDoesNotConstructBulletPhysicsDirectly"`
- No linter is configured.

## Architecture

Source root: `src/main/kotlin/com/pafoid/skate/`

- `engine/` – runtime: `core` (Engine, Window, JobSystem, EventSystem), `ecs` (Scene, GameObject, SceneManager, components, systems), `assets` (loaders, serialization), `render` (render graph, renderer passes; GLSL in `src/main/resources/shaders`), `physics3d`, `audio`, `input`.
- `editor/` – ImGui editor built on the engine: `commands`, `systems` (UndoRedoManager), `ui` (windows, menus, handlers, service), `imgui`, `project`, `search`, `gizmos`, `settings`.
- `game/` – sample game (player, skateboard, camera, prefabs).
- `app/` – application/screen wiring.

## Key conventions (enforced by tests in `src/test/.../architecture/`)

- **Engine must not depend on editor**: `InputSystem.kt`, `AudioSystem.kt`, `SceneManager.kt` must not import `com.pafoid.skate.editor.*`.
- **Physics via factory**: `Scene.kt` must not import or construct `BulletPhysics3D`; use `Physics3DFactory`.
- **UI mutation pipeline**: UI entry points (`editor/search/providers`, `editor/ui/windows`, `editor/ui/menus`, `editor/imgui`) must not call `.executeCommand(` or project mutations directly. Mutations are `Command` classes (`editor/commands`: `execute`, `undo`, `getDisplayName`, `getTargetName`, `getCategory` defaulting to `UNDOABLE`; variants `AsyncCommand`, `ExecuteOnlyCommand`, `ExecutionTrackedCommand`) dispatched via the service layer (`editor/ui/service`) to `UndoRedoManager.executeCommand`. Add command tests in `src/test/.../editor/commands`.
- **Strings**: no hardcoded UI text; use `StringManager` keys. Tables live in per-area files so parallel work does not collide: `assets/strings/editor/<area>.properties` plus `<area>_fr.properties` (editor), and `assets/strings/game/` (runtime/game UI, also merged by the editor). Every key lives in exactly one area file and must have a French entry in the matching `_fr` file with the same placeholders; `StringTablesTest` enforces this. Files are UTF-8; area names must not end in `_xx` (reserved for locales). Add keys to your domain's file:

  | File (`assets/strings/…`) | Owner | Keys |
  |---|---|---|
  | `editor/core` | EDT | menus, windows, dialogs, settings, hierarchy, viewport, console, search, project wizard |
  | `editor/components` | owner of the component (ECS by default) | `component.<Type>.*` inspector labels |
  | `editor/render` | REN | environment, lighting, day/night, render graph, post-process, `material.*` |
  | `editor/physics` | PHY | physics tuner |
  | `editor/audio` | AUD | audio inspector, mixer |
  | `editor/input` | INP | input testing, key bindings, dead zones, input system |
  | `editor/animation` | ANI | animator, animation system, bone tree |
  | `editor/gameui` | GUI | `ui.*` UI inspector and Create UI menu |
  | `editor/gameplay` | GPL | `prefab.*`, prefab library, player, tricks |
  | `editor/scripting` | SCR | script editor |
  | `game/hud` | GPL / GUI | HUD, pause menu, tricks, stance, `ui.default.*` |
  | `game/showcase` | TLG | `showcase.*` Feature Showcase overlay |

  A new area is a new `<area>.properties` + `<area>_fr.properties` pair in the same folder; it is picked up automatically.
- Test names follow `Area_Behavior` style (e.g. `EngineLayering_...`).
- Backlog: `GameEngine.md` (domain sections, task IDs like `REN-01`, owner agents). `docs/roadmap.md` is the older high-level list.
- Domain agents: `.github/agents/*.agent.md` (OpenCode copies in `.opencode/agents/` via `tools/herdr/sync-agents.sh`). Run the herd with `tools/herdr/herd.sh`; see `tools/herdr/README.md`.
