# Architecture Change Impact Map

This document maps recent architectural changes to likely regression surfaces.

## Objective

Provide a shared, current view of where regressions are most likely so triage stays focused and efficient.

## Recovery Status Summary

- `REG-001` through `REG-017`: resolved
- Remaining unresolved item: none

## Change Surfaces, Implemented Recovery Changes, and Current Status

## 1) Event-Driven Editor Action Flow

Expected pattern:

`UI -> Event -> ActionHandler -> Command -> UndoRedoManager`

High-risk regressions:
- UI directly mutating state instead of command execution
- Missing event subscriptions in handlers
- Handler init not wired in DI lifecycle
- Undo/redo metadata or inverse operations missing

Likely impacted areas:
- `engine/events/*Action*.kt`
- `editor/ui/handlers/*ActionHandler*.kt`
- `editor/commands/*`
- `app/KoinModule.kt`

Implemented recovery changes:
- `SceneActionHandler` and `ViewportActionHandler` are Koin-managed startup singletons with `init()` subscription wiring.
- Scene file/menu and search actions route through `SceneAction` events and command execution via `UndoRedoManager`.

Current status: `resolved` (`REG-002`, `REG-004`)

## 2) Scene and Resource Lifecycle

High-risk regressions:
- Scene switches not updating active state correctly
- Scene close paths leaking resources
- Resource cache not clearing when all scenes close
- Missing scene-changed events

Likely impacted areas:
- `engine/ecs/SceneManager.kt`
- `engine/ecs/Scene.kt`
- `engine/events/Scene*.kt`
- `engine/assets/ResourceManager.kt`

Implemented recovery changes:
- Removed duplicate scene-action subscription path; scene actions now subscribe once via DI lifecycle.
- Scene close flow emits `SceneClosing` before destroy, then `SceneClosed`, and emits `SceneChanged` on active-scene transitions.
- File-open action no longer creates a scene shell when file selection is canceled.

Current status: `resolved` (`REG-003`)

## 3) Hybrid ECS Scheduling and Cache Behavior

High-risk regressions:
- Incorrect system execution priority after refactor
- Stale system caches after game object churn/reload
- Invalid assumptions about object presence/lifecycle

Likely impacted areas:
- `engine/ecs/systems/SystemManager.kt`
- `engine/ecs/systems/System.kt`
- Systems that cache object lists

Implemented recovery changes:
- `EditorWorkspace` rebinds `SystemManager.loadScene(scene)` on active scene switch.
- Registration order ensures `DayNightCycleSystem` runs before `DirectionalLightSystem` at equal priority.
- Scene object-set version tracking triggers automatic cache invalidation in `SystemManager`.

Current status: `resolved` (`REG-006`)

## 4) Edit vs Play Mode Boundaries

High-risk regressions:
- Editor tools running during runtime mode
- Runtime camera not selected during play
- Time scaling/pause controls inconsistent

Likely impacted areas:
- `engine/core/Engine.kt`
- `editor/ui/windows/viewport/ViewportToolbar.kt`
- `engine/render/CameraManager.kt`
- editor-only systems/gizmos

Implemented recovery changes:
- Runtime flag synchronization (`scene.isRunning`) is applied before workspace updates.
- Editor camera/input and gizmo mutation paths are gated off during play mode.
- Viewport editor context-menu and drag/drop editor actions are disabled while runtime is active.

Current status: `resolved` (`REG-005`)

## 5) Physics Integration

High-risk regressions:
- Fixed timestep drift or frame-rate dependence
- Physics state not synchronized into gameplay components
- Incorrect downstream consumer assumptions

Likely impacted areas:
- `engine/physics3d/BulletPhysics3D.kt`
- `engine/ecs/systems/PhysicsSystem.kt`
- gameplay systems reading `PhysicsComponent`

Implemented recovery changes:
- Physics stepping moved to `PhysicsSystem.update()` before gameplay sync.
- Scene-level duplicate Bullet stepping path removed.
- Bullet fixed-step accumulator now uses guarded/clamped deterministic stepping logic.

Current status: `resolved` (`REG-007`)

## 6) Persistence and Serialization

High-risk regressions:
- Component state lost on save/load
- Scene metadata not preserved
- UID/component-ID mismatch edge cases

Likely impacted areas:
- serialization systems under `engine/assets/serialization/`
- scene/project serialization flows

Implemented recovery changes:
- Scene-level ECS components are persisted/restored in scene save data.
- Serializer polymorphic registry includes day/night and directional light component types used in scene-level persistence.
- `Component.init()` preserves preloaded component IDs during load when an ID is already present.

Current status: `resolved` (`REG-008`)

## 7) UI Window Registry + Localization

High-risk regressions:
- Window not wired into registry/Koin
- Missing localization keys after UI changes
- Hardcoded strings reintroduced in new UI paths

Likely impacted areas:
- `editor/ui/WindowRegistry.kt`
- `app/KoinModule.kt`
- `src/main/resources/values/strings*.properties`

Implemented recovery changes:
- Recovery-scope hardcoded UI strings were moved to `StringManager` keys.
- Missing French window-title keys used by `WindowRegistry` were added.

Current status: `resolved` (`REG-009`)

## Validation Focus Order

1. Build/startup and DI wiring
2. Scene lifecycle and event-command path
3. Edit/play correctness and ECS ordering
4. Physics sync/stability
5. Serialization and UI/localization consistency

## Remaining Open Recovery Risk

- None in current recovery scope. Full test baseline is passing.
