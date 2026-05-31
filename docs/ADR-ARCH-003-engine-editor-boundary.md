# ADR-ARCH-003: Engine/Editor Boundary — Violation Inventory, Boundary Map, and Migration Plan

- **Status:** accepted
- **Owner:** tech-lead
- **Related backlog:** A48.0.2
- **Date:** 2026-05-29
- **Supersedes:** Post-acceptance regressions against ADR-ARCH-002 (section 5 — layering rules)

---

## Context

Engine/editor boundary violations accumulate across `Main.kt`, `engine/core`, `engine/render`, and `engine/ecs`.
The engine runtime cannot start without direct wiring to editor constructs (`EditorScreen`, `ImGuiLayer`,
`LoggerService`, `SettingsManager`, `GizmoSystem`, `CameraManager` depending on `EditorCamera`).

This ADR records the evidence-based violation inventory, defines the hard boundary target, decides the startup
mode strategy, and provides the concrete migration sequence for P1 → P4.

---

## 1. Violation Inventory

### 1a. Engine → Editor Import Violations

| # | File | Import(s) | Severity | Why it's a violation |
|---|------|-----------|----------|----------------------|
| V-01 | `engine/core/BootManager.kt` lines 3–4 | `editor.systems.LoggerService`, `editor.systems.SettingsManager` | **Critical** | Boot-critical engine class takes concrete editor services as constructor parameters. `BootManager` must only depend on engine-owned contracts. Engine cannot boot independently. |
| V-02 | `engine/ecs/systems/GizmoSystem.kt` lines 3–9 | `editor.gizmos.{MeasureTool,RotationGizmo,ScaleGizmo,SelectionGizmo,TranslateGizmo}`, `editor.systems.SettingsManager`, `editor.systems.UndoRedoManager` | **Critical** | `GizmoSystem` is a pure editor tool (viewport gizmos, undo/redo integration). It belongs in `editor/systems/` but is registered in `engine/ecs/systems/`. Its entire constructor depends on editor types. |
| V-03 | `engine/render/CameraManager.kt` lines 3–4 | `editor.events.ViewportAction`, `editor.gizmos.EditorCamera` | **Critical** | Engine camera management hard-depends on an editor event type and `EditorCamera` (an editor construct). In runtime mode there is no editor camera; this coupling makes the renderer unable to run without an editor composition. |
| V-04 | `engine/ecs/systems/GridLines.kt` line 3 | `editor.systems.StringManager` | **High** | `StringManager` is a concrete editor service. The engine contract `IStringManager` already exists and is the correct dependency. Direct import bypasses the contract boundary. |
| V-05 | `engine/render/renderer/ThumbnailRenderer.kt` line 3 | `editor.systems.LoggerService` | **High** | `ThumbnailRenderer` is an editor-only tool (asset browser thumbnail generation). Its placement in `engine/render/renderer/` is itself a misclassification. It additionally imports a concrete editor service. |
| V-06 | `engine/render/renderer/PickingRenderer.kt` lines 3–4 | `editor.data.LogLevel`, `editor.systems.LoggerService` | **High** | Picking is an editor selection feature. Placed in `engine/render/renderer/` and imports two editor types. Should migrate to `editor/render/` or bind through `EngineLogger` contract. |
| V-07 | `engine/render/RenderResourcesFactory.kt` line 3 | `editor.systems.LoggerService` | **High** | Core render factory imports the concrete editor logger instead of the engine-owned `EngineLogger` contract. |

### 1b. Lifecycle/Composition Violations

| # | File | Location | Severity | Why it's a violation |
|---|------|----------|----------|----------------------|
| V-08 | `Main.kt` lines 3, 20–26 | `import app.EditorScreen`, then unconditional `EditorScreen(window).init()` + `editorScreen.update(dt)` in the update loop | **Critical** | There is no mode selection. `EditorScreen` (which brings all editor constructs including `ImGuiLayer`) is always wired into the engine lifecycle regardless of intent. Runtime mode is impossible without modifying source. |
| V-09 | `app/KoinModule.kt` — `engineModule`, lines 247–248, 265, 267 | `EngineLogger` bound via `get<LoggerService>()`, `IStringManager` bound via `get<StringManager>()`, `InputMappingsProvider` bound via `get<SettingsManager>()` | **Critical** | `engineModule` resolves these contracts through concrete types defined only in `appModule`. `engineModule` cannot be loaded standalone without `appModule`. This is a hidden dependency between DI modules. |
| V-10 | `app/KoinModule.kt` — `engineModule`, lines 289–291 | `ThumbnailCache`, `PrefabsGenerator`, `EngineAssetCopier` registered in `engineModule` | **High** | These are editor asset-pipeline tools (thumbnail caching, prefab generation, asset copying). They belong in `appModule` / editor composition. Registering them in `engineModule` conflates runtime and editor concerns at the DI level. |
| V-11 | `app/KoinModule.kt` — `engineModule`, line 285 | `PickingRenderer` in `engineModule` | **High** | `PickingRenderer` supports editor object selection (mouse picking in viewport). It is not a runtime rendering concern; it belongs in the editor composition. |

### 1c. Misclassification Violations (wrong package, compound issue)

| # | File | Issue | Severity |
|---|------|-------|----------|
| V-12 | `engine/ecs/systems/GizmoSystem.kt` | Entire file is editor-only. Package placement in `engine/ecs/systems/` is structurally wrong. | **Critical** |
| V-13 | `engine/ecs/systems/GridLines.kt` | Editor-only visual aid (editor grid overlay with ImGui toggle). Package placement in `engine/ecs/systems/` is wrong. Directly imports `imgui.ImGui` in addition to editor service. | **High** |
| V-14 | `engine/render/renderer/ThumbnailRenderer.kt` | Editor-only asset pipeline feature in `engine/render/renderer/`. | **High** |
| V-15 | `engine/render/renderer/PickingRenderer.kt` | Editor-only selection tool in `engine/render/renderer/`. | **High** |

---

## 2. Boundary Target Map

### 2a. Hard ownership rules

```
engine/**
  ✅ May depend on:  LWJGL, JOML, Koin, Kotlin stdlib, engine/contracts/**
  ❌ Must NOT depend on: editor/**, app/** (KoinModule), ImGui (except via abstraction)
  ❌ Must NOT contain: editor-only tools (gizmos, thumbnail, picking, grid overlay)

editor/**
  ✅ May depend on:  engine/**, ImGui, Koin, editor/**
  ✅ Must implement: engine/contracts/** interfaces (EngineLogger, IStringManager, InputMappingsProvider)
  ❌ Must NOT be imported by: engine/**

app/** (KoinModule, EditorScreen, Main.kt)
  ✅ Is the composition root — it wires engine and editor together via DI
  ✅ Defines startup mode selection
  ❌ Must NOT be imported by: engine/**, editor/**
```

### 2b. Existing clean contracts (already valid boundary interfaces)

| Contract | Location | Role |
|----------|----------|------|
| `EngineLogger` | `engine/contracts/EngineLogger.kt` | Logging abstraction; editor's `LoggerService` must implement this |
| `IStringManager` | `engine/contracts/IStringManager.kt` | Localization abstraction; editor's `StringManager` must implement this (already does) |
| `InputMappingsProvider` | `engine/contracts/InputMappingsProvider.kt` | Input config abstraction; `SettingsManager` must implement this (already does) |

### 2c. New interfaces required (to be introduced in P3)

| Interface | Package | Replaces direct dependency on |
|-----------|---------|-------------------------------|
| `ISettingsProvider` | `engine/contracts/` | Concrete `SettingsManager` in `BootManager` |
| `ICameraProvider` | `engine/contracts/` | `EditorCamera` + `ViewportAction` event in `CameraManager`; allows engine renderer to request "active camera" without knowing editor camera exists |

### 2d. Adapter/wiring points at the boundary (app-layer responsibility)

```
app/KoinModule.kt (or split: runtimeModule + editorModule)
  - Binds EngineLogger     → LoggerService
  - Binds IStringManager   → StringManager
  - Binds InputMappingsProvider → SettingsManager
  - Binds ISettingsProvider → SettingsManager        [new, P3]
  - Binds ICameraProvider   → EditorCamera adapter   [new, P3]

editor/systems/ (after P4 migration)
  - GizmoSystem (moved from engine/ecs/systems/)
  - GridLines   (moved from engine/ecs/systems/)

editor/render/ (after P3/P4 migration)
  - ThumbnailRenderer (moved from engine/render/renderer/)
  - PickingRenderer   (moved from engine/render/renderer/)
```

---

## 3. Startup Mode Decision

### Decision: **Single binary with `--editor` mode flag**

Runtime-only is the **default**. Passing `--editor` at launch enables the full editor composition.

### Rationale

1. **Minimal P1 footprint.** The only structural change is a flag check in `Main.kt` and conditional Koin module
   loading. No new build targets, no second `main()`, no abstraction layers added before the boundary is clean.

2. **Correct default.** Shipped games run without an editor. Runtime-only should be the zero-overhead path.

3. **Single binary model matches industry precedent** (Godot, Unity Player vs Editor). Both modes coexist in
   one artifact; the deployment context controls the mode.

4. **DI composition is the natural selection point.** `main()` inspects args, loads `engineModule` (always) and
   `appModule` (editor mode only). This makes the module boundary the authority, not a runtime flag scattered
   across systems.

5. **Against "separate composition roots":** Would require two build targets and duplicate entry points.
   The current single-binary lifecycle is correct; only the composition needs to be guarded.

6. **Against "abstract bootstrap / IStartupProfile":** Over-engineered for the current violation scope. The
   `IStartupProfile` pattern is valuable when there are 3+ startup profiles; right now there are two. Add it
   in a future ADR if a third profile (headless server, test harness) emerges.

### Concrete implementation contract for P1

```kotlin
// Main.kt — after P1a
fun main(args: Array<String>) {
    val editorMode = "--editor" in args

    val modules = if (editorMode) listOf(engineModule, appModule) else listOf(engineModule)
    val app = startKoin { modules(modules) }

    val engine = app.koin.get<Engine>()
    val window = Window(title = "PAFSK8", windowIcon = Assets.Textures.APP_ICON)

    engine.start()

    if (editorMode) {
        val editorScreen = EditorScreen(window)
        editorScreen.init()
        window.show { dt ->
            engine.update(dt)
            editorScreen.update(dt)
        }
        editorScreen.destroy()
    } else {
        window.show { dt -> engine.update(dt) }
    }

    engine.destroy()
}
```

> **Note:** The `appModule` and `engineModule` split in `KoinModule.kt` (P2) must ensure `engineModule`
> can resolve all its own bindings (`EngineLogger`, `IStringManager`, `InputMappingsProvider`) without
> requiring `appModule` to be present. This means the engine must either: (a) provide no-op/default
> runtime implementations of these contracts in `engineModule`, or (b) bind them via a dedicated
> `runtimeAdapterModule` included in both modes. Decision for P2 execution.

---

## 4. Migration Order (P1 → P4)

### P1 — Bootstrap / runtime split at entry point

**Goal:** Engine runtime starts without `EditorScreen` or `ImGuiLayer` in the call path.

Files to change:
1. **`Main.kt`** — Add `--editor` flag check; guard `EditorScreen` creation and `appModule` loading.
   See Section 5 for exact changes.
2. **`app/KoinModule.kt`** — Extract binding of `EngineLogger`, `IStringManager`, `InputMappingsProvider`
   into a small `runtimeAdapterModule` so `engineModule` can always load cleanly. (Preliminary — full split
   is P2.)

**Acceptance:** `main(emptyArray())` starts the engine without any editor construct instantiated.

---

### P2 — DI decomposition

**Goal:** `engineModule` graph resolves standalone; editor module owns editor types.

Files to change:
1. **`app/KoinModule.kt`** — Full split:
   - Create `runtimeAdapterModule`: binds `EngineLogger → LoggerService` (or a no-op runtime logger),
     `IStringManager → StringManager` (or a properties-file-only runtime variant),
     `InputMappingsProvider → SettingsManager` (or runtime settings loader).
   - Move `ThumbnailCache`, `PrefabsGenerator`, `EngineAssetCopier` from `engineModule` to `appModule`.
   - Move `PickingRenderer` registration from `engineModule` to `appModule`.
   - `engineModule` must not reference any type from `editor/**` or `app/**`.

**Acceptance:** `startKoin { modules(engineModule, runtimeAdapterModule) }` compiles and starts without
`appModule`; no `ClassNotFoundException` or unresolved Koin definitions.

---

### P3 — Engine package decontamination

**Goal:** Zero `editor/**` imports remain in `engine/**` packages.

Files to change:
1. **`engine/core/BootManager.kt`** — Replace `LoggerService` with `EngineLogger` (V-01); replace
   `SettingsManager` with new `ISettingsProvider` engine contract (V-01). Add `ISettingsProvider` to
   `engine/contracts/`.
2. **`engine/render/CameraManager.kt`** — Introduce `ICameraProvider` engine contract (V-03). Remove
   `EditorCamera` parameter; accept `ICameraProvider` instead. Remove `ViewportAction` import; subscribe
   to an engine-owned event type or move play-mode flag propagation to `Engine.kt`. `EditorCamera` in
   `editor/gizmos/` must implement `ICameraProvider`.
3. **`engine/ecs/systems/GridLines.kt`** — Replace `StringManager` with `IStringManager` contract (V-04).
4. **`engine/render/RenderResourcesFactory.kt`** — Replace `LoggerService` with `EngineLogger` (V-07).
5. **`engine/render/renderer/ThumbnailRenderer.kt`** — Replace `LoggerService` with `EngineLogger` (V-05).
   (Full relocation to `editor/` is P4; P3 only removes the import violation.)
6. **`engine/render/renderer/PickingRenderer.kt`** — Replace `LoggerService` / `LogLevel` with
   `EngineLogger` (V-06). (Full relocation is P4.)

**Acceptance:** `grep -r "import com.pafoid.skate.editor" engine/` returns zero results.

---

### P4 — ECS/editor concern extraction + mutation pipeline closure

**Goal:** Editor-only systems live in `editor/`, not `engine/`. Remaining direct state mutations removed.

Files to change / move:
1. **`engine/ecs/systems/GizmoSystem.kt`** → move to **`editor/systems/GizmoSystem.kt`** (V-02, V-12).
   Update `app/KoinModule.kt` (`appModule`) and `app/EditorScreen.kt` registration accordingly.
2. **`engine/ecs/systems/GridLines.kt`** → move to **`editor/systems/GridLines.kt`** (V-13).
   Update `appModule` registration and `EditorScreen.kt`.
3. **`engine/render/renderer/ThumbnailRenderer.kt`** → move to **`editor/render/ThumbnailRenderer.kt`** (V-14).
   Update `appModule` registration.
4. **`engine/render/renderer/PickingRenderer.kt`** → move to **`editor/render/PickingRenderer.kt`** (V-15).
   Update `appModule` registration. Update `RenderResourcesFactory` to accept `PickingRenderer` as an
   optional/editor-only render pass.
5. **Mutation pipeline closures** — remaining windows/handlers with direct state mutation paths (tracked
   separately under A48.0.2 task 7; out of scope for this ADR's violation inventory, which is
   import/lifecycle focused).

**Acceptance:** `engine/**` package tree contains no gizmo, thumbnail, picking, or grid overlay files.
`editor/**` package tree contains all of the above. `EngineLayeringGuardTest` passes with new fixtures.

---

## 5. P1 Kickoff Tranche — Exact Changes per File

### `Main.kt`

**Current state:** `EditorScreen` unconditionally instantiated on line 20; `appModule` always loaded.

**Required changes:**
- Parse `args` for `"--editor"` flag before `startKoin`.
- Load `listOf(engineModule, runtimeAdapterModule)` in runtime mode;
  `listOf(engineModule, runtimeAdapterModule, appModule)` in editor mode.
- Wrap lines 20–28 (`EditorScreen` creation, `editorScreen.update`, `editorScreen.destroy`) in
  `if (editorMode) { ... }`.
- Extract two local functions (`runEditorLoop`, `runRuntimeLoop`) for clarity — not required but
  strongly recommended.
- Remove `import com.pafoid.skate.app.EditorScreen` from the unconditional import block; move it inside
  the guarded call site or keep as conditional import.

### `Engine.kt`

**Current state:** No `editor/**` imports. Already clean.

**Required changes (P1a scope):**
- Add KDoc comment stating `Engine` is runtime-only; it must not be given editor constructs directly.
- No import changes needed.
- Verify `renderer.useFbo = true` in `start()` is a valid runtime default (it is — FBO is used for
  the game viewport even at runtime). No change.

### `Window.kt`

**Current state:** No `editor/**` imports. Already clean.

**Required changes (P1a scope):** None. `Window` is already a pure engine construct.
No changes needed in P1a.

### `ImGuiLayer.kt`

**Current state:** Lives in `editor/imgui/` — correctly placed. Accepts `WindowController` (engine
type) in `init()`, which is the right boundary pattern. No structural violations against the editor
package.

**Required changes (P1a scope):** None. `ImGuiLayer` is already in the correct package and is only
instantiated via `appModule` → `EditorScreen`. The only guarantee needed is that `appModule` is only
loaded in editor mode — which `Main.kt` P1a change covers.

### `EditorScreen.kt`  *(the equivalent of `EditorWorkspace.kt` — that file does not exist; `EditorScreen` is the actual editor compositor)*

**Current state:** Lives in `app/EditorScreen.kt`. Imports only `editor/**` and `engine/**`. Is only
instantiated from `Main.kt`. Already structurally correct — it is the app-layer bridge.

**Required changes (P1a scope):**
- No structural changes to the class itself.
- Its instantiation in `Main.kt` must be guarded by `editorMode` flag (handled by `Main.kt` change above).
- Confirm that Koin injection of `ImGuiLayer`, `EditorInputHandler`, `EditorCamera`, `GizmoSystem`,
  `GridLines` inside `EditorScreen` is satisfied only when `appModule` is loaded (i.e., these bindings
  do not exist in `engineModule` alone). After P1a and P2, `appModule` absence means `EditorScreen` is
  never instantiated, so Koin won't try to resolve these bindings.

---

## 6. Out of Scope for This ADR

- Mutation pipeline violations inside `editor/ui/windows/**` and `editor/ui/handlers/**` — tracked
  separately under A48.0.2 task 7 (P4).
- `InputSystem` using ImGui directly (`imgui.ImGui` import, line 19) — medium priority; tracked but
  not a boundary violation (ImGui is a runtime dependency, not editor-package-specific).
- Guard test expansion (P5) and documentation reconciliation (P6) — own phases with own owners.

---

## Verification

- P1 gate: `main(emptyArray())` runs without `EditorScreen`, `ImGuiLayer`, or any `appModule` type
  being instantiated.
- P2 gate: `startKoin { modules(engineModule, runtimeAdapterModule) }` starts cleanly with no missing
  definitions.
- P3 gate: `grep -r "import com.pafoid.skate.editor" src/main/kotlin/com/pafoid/skate/engine/`
  returns zero results.
- P4 gate: `engine/ecs/systems/` contains no gizmo, grid, or picking files;
  `engine/render/renderer/` contains no thumbnail or picking files.
- `EngineLayeringGuardTest` passes with fixtures covering V-01 through V-07.
