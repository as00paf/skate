# Editor/Project Scene Separation — Focused Plan

**Document Type:** Architecture & Migration Plan
**Target:** SkateSim Engine v0.51.0.0
**Date:** 2026-04-14
**Author:** Tech Lead
**Status:** Draft — Review Required

---

## Goal

Achieve clean separation of the editor workspace from project scenes. **Separation first, cleanup after.**

---

## 1. Target Architecture

### 1.1 Design Philosophy

The core insight: **the editor is an application that hosts game scenes**. They are not the same thing.

- **EditorWorkspace** = the application shell (camera, gizmos, grid, selection, editor input)
- **Scene** = a single game level (GameObjects, physics, gameplay systems, serialization unit)
- **Boundary** = EditorWorkspace owns tools, Scene owns content. Nothing is "both."

### 1.2 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Engine                                   │
│  ┌──────────────────────┐     ┌───────────────────────────────┐ │
│  │   EditorWorkspace    │     │        SceneManager           │ │
│  │                      │     │                               │ │
│  │  camera: Camera      │     │  openScenes: List<Scene>      │ │
│  │  selection: GO?      │     │  ┌───────────────────────────┐ │ │
│  │                      │     │  │  Scene (Game Level #1)   │ │ │
│  │  Systems:            │     │  │  ┌───────────────────┐    │ │ │
│  │  - EditorCamera      │◄──┐ │  │  │ GameObjects       │    │ │ │
│  │  - MouseControls     │   │ │  │  │ Physics3D         │    │ │ │
│  │  - GizmoSystem       │   │ │  │  │                   │    │ │ │
│  │  - GridLines         │   │ │  │  │ Gameplay Systems: │    │ │ │
│  │                      │   │ │  │  │  InputSystem      │    │ │ │
│  └──────────────────────┘   │ │  │  │  AnimationSystem  │    │ │ │
│                             │ │  │  │  PhysicsSystem    │    │ │ │
│                             │ │  │  │  AudioSystem      │    │ │ │
│                             │ │  │  │  EnvironmentSys   │    │ │ │
│                             │ │  │  │  DayNightCycle    │    │ │ │
│  ┌──────────────────────┐   │ │  │  │  RagdollSystem    │    │ │ │
│  │   ImGuiLayer         │   │ │  │  └───────────────────┘    │ │ │
│  │  passes:             │   │ │  └───────────────────────────┘ │ │
│  │  - workspace to      │   │ │                               │ │
│  │    editor systems    │   │ │  (more scenes...)             │ │
│  │  - activeScene to    │   │ └───────────────────────────────┘ │
│  │    IWindowWithScene  │   │                                   │
│  └──────────────────────┘   │                                   │
│                             │                                   │
│  EditorInputHandler ────────┘                                   │
│  (uses workspace.selection + workspace.camera)                  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 System Ownership

| System | Before | After (Phase 1) |
|--------|--------|-----------------|
| EditorCamera | Scene.systemManager | **EditorWorkspace** |
| MouseControls | Scene.systemManager | **EditorWorkspace** |
| GizmoSystem | Scene.systemManager | **EditorWorkspace** |
| GridLines | Scene.systemManager | **EditorWorkspace** |
| InputSystem | Scene.systemManager | Scene (stays) |
| AnimationSystem | Scene.systemManager | Scene (stays) |
| PhysicsSystem | Scene.systemManager | Scene (stays) |
| AudioSystem | Scene.systemManager | Scene (stays) |
| RagdollSystem | Scene.systemManager | Scene (stays) |
| EnvironmentSystem | Scene.systemManager | Scene (stays) |
| DayNightCycleSystem | Scene.systemManager | Scene (stays) |
| DirectionalLightSystem | Scene.systemManager | Scene (stays) |

---

## 2. Root Cause Analysis — Why the Previous Attempt Failed

| Cause | What Happened | How We Prevent It |
|-------|---------------|-------------------|
| System registration inside `Scene.init()` | Splitting editor systems away broke the initializer | EditorWorkspace created AFTER `Scene.init()` completes |
| `Engine.updateRunningState()` calls `scene.editorUpdateScene(dt)` for ALL systems | Removing editor systems broke the update loop | Engine calls `workspace.editorUpdate(dt)` + `scene.editorUpdateScene(dt)` explicitly |
| `EditorCamera` takes `scene.camera` as constructor param | Camera baked into Scene, couldn't move | Keep `Scene.camera` as temporary bridge in Phase 1 |
| 6 windows do `scene.systemManager.getSystem<T>()` | Returned null after systems moved | Only `SystemsWindow` is affected (editor systems moved). Fix in Phase 2 |
| Moved things without changing readers | Every call site broke | Phase 1 changes BOTH ownership AND call sites atomically |

### What Must Be Preserved During Migration

1. **Scene still extends GameObject** — hybrid ECS pattern is working
2. **Scene.camera remains** — renderer depends on it; editor camera overlays via workspace
3. **GameObjectManager API** — all windows call `addGameObject`, `removeGameObject`, `getGameObject`
4. **SystemManager lifecycle** — `init()`, `start()`, `update()`, `destroy()` remain intact
5. **Koin singleton bindings** — Engine, Renderer, SceneManager are singletons

---

## 3. Phased Migration Plan

### Phase 1: Create EditorWorkspace, Extract Editor Systems (Core Separation)

**Goal:** Introduce `EditorWorkspace` class. Move editor-only systems from Scene's SystemManager to workspace. Scene keeps gameplay systems. Engine orchestrates both.

#### Files to change

| File | Change |
|------|--------|
| `engine/core/EditorWorkspace.kt` | **NEW** — owns `camera: Camera`, `selectedGameObject: GameObject?`, `systemManager` with editor systems only |
| `engine/core/Engine.kt` | Inject `EditorWorkspace`. Call `workspace.editorUpdate(dt)` before `scene.editorUpdateScene(dt)` |
| `editor/LevelEditorSceneInitializer.kt` | `EditorSystemFactory.addEditorSystems(scene)` → `EditorSystemFactory.createEditorSystems(workspace)`. Only gameplay + lighting systems added to Scene. |
| `editor/imgui/ImGuiLayer.kt` | Accept `EditorWorkspace` in constructor. Pass `workspace` to `EditorInputHandler`. Keep passing `activeScene` to `IWindowWithScene` (unchanged). |
| `editor/systems/EditorInputHandler.kt` | Change `update(currentScene: Scene?)` → `update(workspace: EditorWorkspace, scene: Scene?)`. Selection goes to `workspace.setSelected()`. |
| `editor/EditorCamera.kt` | Update to receive camera from workspace instead of Scene. |
| `editor/MouseControls.kt` | Update to receive workspace reference instead of Scene. |
| `editor/gizmos/GizmoSystem.kt` | Update to receive workspace reference instead of Scene. |
| `editor/gizmos/GridLines.kt` | Update to receive workspace reference instead of Scene. |
| `app/KoinModule.kt` | Register `single { EditorWorkspace(...) }`. Wire into Engine, ImGuiLayer, EditorInputHandler. Remove editor systems from scene registration. |

#### What stays on Scene (gameplay)
- InputSystem, AnimationSystem, AudioSystem, PhysicsSystem, RagdollSystem
- EnvironmentSystem, DayNightCycleSystem, DirectionalLightSystem
- All GameObjects, Physics3D, Camera (temporary bridge)

#### What moves to EditorWorkspace (editor tools)
- EditorCamera, MouseControls, GizmoSystem, GridLines
- EditorInputStateComponent (owned by workspace)

#### Boot Sequence (After Phase 1)
```
BootManager creates Scene → Scene.init() loads resources →
BootManager creates EditorWorkspace → workspace creates editor systems →
SceneManager.openScene(scene) → scene.startScene() starts gameplay systems →
Engine begins update loop:
  workspace.editorUpdate(dt)    ← editor systems (camera, gizmos, grid, mouse)
  scene.editorUpdateScene(dt)   ← gameplay systems (input, animation, physics, etc.)
```

#### Verification
- `.\gradlew.bat compileKotlin` succeeds
- App boots, splash screen shows, editor opens
- Editor camera moves with WASD/mouse
- Gizmos render and function (translate, rotate, scale)
- Grid renders
- Default scene loads with skater + skateboard
- SceneHierarchyWindow shows GameObjects
- Click object → PropertiesWindow updates
- Play button starts simulation, pause stops
- Project create/open works (prefabs spawn into Scene, not workspace)

#### Rollback
Delete `EditorWorkspace.kt`, revert all changed files to originals. Clean revert — EditorWorkspace is a new file, no existing files are deleted.

#### Risk: MEDIUM
This is the biggest structural change. Risk is in Engine update ordering and Koin wiring. Mitigated by:
- Keeping Scene.camera as a temporary bridge
- Not changing any IWindowWithScene interface yet
- Windows still receive Scene (gameplay systems stay there)

---

### Phase 2: Fix Immediate Fallout (System Lookups)

**Goal:** The only broken window after Phase 1 is `SystemsWindow` — it iterates `scene.systemManager.systems` and will now only see gameplay systems (editor systems moved to workspace).

#### Files to change

| File | Change |
|------|--------|
| `editor/windows/SystemsWindow.kt` | Accept both `Scene` and `EditorWorkspace`. Show systems from both. Two sections: "Editor Systems" and "Gameplay Systems". |

#### Why only SystemsWindow?

All other 5 IWindowWithScene windows query gameplay systems that stay on Scene:
- `EnvironmentWindow` → DayNightCycleSystem, EnvironmentSystem (stay on Scene)
- `PhysicsTunerWindow` → PhysicsSystem (stays on Scene)
- `InputTestingWindow` → Scene for GameObject queries (Scene unchanged)
- `AudioInspectorWindow` → Scene for GameObject queries (Scene unchanged)
- `SceneHierarchyWindow` → Scene for GameObject tree (Scene unchanged)

#### Verification
- SystemsWindow shows two sections: "Editor Systems" (from workspace) and "Gameplay Systems" (from Scene)
- All 6 IWindowWithScene windows render without NPE
- No other behavior changes

#### Rollback
Revert SystemsWindow. Temporarily add editor systems back to Scene's SystemManager.

#### Risk: LOW
Only one window needs fixing. Small, targeted change.

---

### Phase 3+: Deferred Cleanup (Post-Separation)

These are important but **NOT required for separation**. Addressed only after Phase 1-2 are solid.

| Task | Priority | Notes |
|------|----------|-------|
| Remove dead per-scene EventSystem | Low | Cosmetic — already unused |
| Consolidate to single global EventSystem | Low | Already working via global singleton |
| Wrap direct mutations in Commands | Medium | Undo support for EnvironmentWindow, PhysicsTunerWindow, etc. |
| Replace hardcoded name lookups ("Skater", "Skate") | Low | Works fine, just fragile |
| Remove `!!` operators | Medium | Null safety, not separation-related |
| Migrate selection to SelectionViewModel/Workspace | Medium | Works via events currently |
| Introduce EntityResolver for cross-references | Low | Future need |
| Introduce SceneContext interface | Low | Windows genuinely need Scene data for now |
| Rename Scene → GameScene | Low | Pure rename, optional |

---

## 4. Safeguards Against Crashes

### 4.1 Boot Sequence Protection

**Rule:** `EditorWorkspace` is created AFTER `Scene.init()` completes. Never during.

```
BootManager:
  1. scene = Scene("SplashScene", sceneInitializer)  // Scene.init() runs here
  2. scene.init()                                     // Resources loaded
  3. editorWorkspace = EditorWorkspace(...)           // Editor systems created here
  4. scene.startScene()                               // Gameplay systems started
  5. Engine enters update loop
```

### 4.2 Null-Safe System Lookups

Before Phase 1 lands, audit all `scene.systemManager.getSystem<T>()` calls:

| System Queried | Found On | Phase 1 Status |
|----------------|----------|----------------|
| DayNightCycleSystem | Scene | Unchanged |
| EnvironmentSystem | Scene | Unchanged |
| PhysicsSystem | Scene | Unchanged |
| AudioSystem | Scene | Unchanged |
| GizmoSystem | **Workspace** | SystemsWindow needs fix (Phase 2) |
| MouseControls | **Workspace** | SystemsWindow needs fix (Phase 2) |
| EditorCamera | **Workspace** | SystemsWindow needs fix (Phase 2) |
| GridLines | **Workspace** | SystemsWindow needs fix (Phase 2) |
| MeasureTool | GizmoSystem | Unchanged (owned by GizmoSystem) |

### 4.3 Camera Bridge (Temporary)

During Phase 1, `Scene.camera` remains as a read-only bridge. `EditorWorkspace` creates its own camera for editor use. ImGuiLayer renders `scene` but camera is workspace-owned.

**Safeguard:** Do NOT remove `Scene.camera` in Phase 1.

### 4.4 Serializer Target Protection

Add a documentation comment on `SceneSerializer`:

```kotlin
/**
 * Deserializes game content INTO a Scene.
 * Never pass EditorWorkspace to this class.
 * EditorWorkspace owns editor tools only — it has no serializable game content.
 */
```

### 4.5 Input System Boundary

`InputSystem` stays on Scene for now. It feeds both gameplay and editor input via `EditorInputStateComponent`. This is acceptable because:
- InputSystem writes to `EditorInputStateComponent` which workspace reads
- EditorCamera reads from that component
- No circular dependency

**Document:** InputSystem is a cross-boundary system for now. Full isolation is Phase 3+.

### 4.6 Feature Flag (Optional Safety Net)

Add `engine.editorWorkspaceEnabled: Boolean` in settings for Phase 1:

```kotlin
if (editorWorkspaceEnabled) {
    editorWorkspace.editorUpdate(dt)
    scene.editorUpdateScene(dt)  // gameplay systems only now
} else {
    scene.editorUpdateScene(dt)  // old behavior, everything
}
```

This allows instant rollback without git revert if Phase 1 has issues.

---

## 5. Fragile State Dependencies to Preserve

| Dependency | Must Preserve During |
|---|---|
| `Scene.camera` position/rotation | Entire migration (Phase 1+) |
| `Scene.gameObjectManager.gameObjects` list | Entire migration |
| `Scene.physics3d` reference | Entire migration |
| `Scene.isRunning` flag | Entire migration |
| `Scene.startScene()` lifecycle | Entire migration |
| `EventSystem` global singleton | Entire migration (never create per-scene) |
| `SelectionViewModel` event subscriptions | Entire migration |

---

## 6. Verification Strategy

### 6.1 Per-Phase Checklist

After each phase, verify:

| Test | How |
|------|-----|
| **Build** | `.\gradlew.bat compileKotlin` — must succeed |
| **Launch** | App starts, splash screen shows, editor opens |
| **Scene load** | Default scene loads with skater + skateboard |
| **Hierarchy** | SceneHierarchyWindow shows GameObjects |
| **Selection** | Click object → PropertiesWindow updates |
| **Gizmos** | Translate/Rotate/Scale gizmos render and work |
| **Viewport** | Camera moves, grid renders |
| **Play/Pause** | Play button starts simulation, pause stops |
| **Undo/Redo** | Create object → undo → object removed |
| **Physics tuner** | Opens, shows player/skateboard settings |
| **Environment** | Time of day slider works |
| **Audio inspector** | Opens on selected object with AudioComponent |
| **Systems window** | Shows both editor and gameplay systems (Phase 2+) |

### 6.2 Automated Tests (Where Applicable)

- **Phase 1**: Integration test for EditorWorkspace.update() calling all editor systems
- **Phase 2**: Verify SystemsWindow shows both system lists

---

## 7. Rollback Plan

### 7.1 Per-Phase Rollback

Each phase is a single git commit (or small set of commits). Rollback is:
```powershell
git revert <commit-hash>
.\gradlew.bat compileKotlin
.\gradlew.bat run
```

### 7.2 Emergency Rollback (Phase 1)

If Phase 1 causes unrecoverable crashes:
1. Revert the commit
2. Re-add editor systems to `EditorSystemFactory.addEditorSystems(scene)`
3. Revert Engine to old behavior (single `scene.editorUpdateScene(dt)`)
4. Verify editor works in old architecture
5. **Do not retry Phase 1** until the specific crash is identified and fixed in isolation

### 7.3 Rollback Decision Criteria

| Symptom | Action |
|---------|--------|
| App doesn't launch | Revert immediately |
| Editor opens but scene doesn't load | Revert immediately |
| Gizmos don't render but editor works | Debug in-place (don't revert) |
| Selection works in some windows but not others | Debug in-place (don't revert) |
| SystemsWindow missing editor systems | Debug in-place (don't revert — Phase 2 fixes this) |
| Undo doesn't work | Debug in-place (don't revert — unrelated to separation) |

---

## 8. Migration Order Summary

```
Phase 1: Create EditorWorkspace, extract editor systems   ← MEDIUM (core change)
Phase 2: Fix SystemsWindow dual-source lookup            ← LOW  (one window)
Phase 3+: Deferred cleanup (dead code, EventSystem, Commands, etc.) ← Varies
```

**Critical path:** Phase 1 → Phase 2 (sequential, Phase 2 depends on Phase 1)
**Deferred:** All cleanup tasks from Phase 3+ are parallelizable and independent

**Estimated effort:**
- Phase 1: 6-8 hours (highest risk, needs careful testing)
- Phase 2: 30 minutes - 1 hour
- Phase 3+: TBD per task

**Total for separation: ~7-9 hours of focused development work**

---

## 9. Post-Separation State

After Phase 1-2 complete:

| Concern | Before | After |
|---------|--------|-------|
| Editor systems registered on Scene | Yes | No (on EditorWorkspace) |
| Gameplay systems registered on Scene | Yes | Yes (unchanged) |
| Per-scene EventSystem | Yes (dead) | Yes (still dead — cleanup later) |
| Selection state location | GameObjectManager | GameObjectManager (unchanged — cleanup later) |
| Windows access Scene directly | Yes | Yes (unchanged — gameplay systems stay on Scene) |
| Hardcoded GameObject names | Yes | Yes (unchanged — cleanup later) |
| Direct state mutation | Yes | Yes (unchanged — cleanup later) |
| Scene can be serialized independently | Partially | **Yes** (no editor state mixed in) |
| Multiple scenes can be open with separate editor state | No | Possible (future) |

**Key point:** After separation, the architecture is clean but the code is still a bit messy. That's intentional. The cleanup phases (Phase 3+) can happen independently and safely because the structural boundary is solid.
