# Codebase Assessment Report

## Scope

This report consolidates a full-project assessment performed with:

- `tech-lead` (architecture conformance audit)
- `reviewer` (code uniformity, duplication, maintainability audit)

## Executive Summary

- **Overall architecture health:** ~**6/10**
- The codebase remains functional, but post-refactor **pattern drift** is significant in key paths.
- Highest risks are concentrated in:
  1. UI mutation flow inconsistency
  2. Edit/Play boundary leaks
  3. Undo/async command contract mismatch
  4. ECS cache invalidation gaps
  5. DI and layering inconsistency

## Consolidated Findings

| Priority | Area | Problem | Evidence (examples) | Impact |
|---|---|---|---|---|
| P0 | Event/Command architecture | UI still mutates state directly or executes commands directly instead of `UI -> Event -> Handler -> Command -> UndoRedoManager` | `editor/ui/windows/PropertiesWindow.kt`, `editor/ui/windows/SceneHierarchyWindow.kt`, `editor/ui/menus/EditMenuBuilder.kt`, `editor/search/providers/ActionSearchProvider.kt`, `editor/ui/windows/viewport/ViewportToolbar.kt` | Behavior drift, weak auditability, inconsistent undo behavior |
| P0 | Edit vs Play boundary | Editor flows can mutate scene/runtime while in play | `SceneHierarchyWindow.kt`, `PropertiesWindow.kt`, `ViewportToolbar.kt`, `EditMenuBuilder.kt` | Runtime nondeterminism and corruption risk |
| P0 | Undo semantics | No-op or non-undoable commands are treated as normal undoable entries | `CreatePrimitiveCommand.kt`, `CreateLightCommand.kt`, `DuplicateGameObjectCommand.kt`, `UndoRedoManager.kt` | Misleading history and unreliable undo UX |
| P0 | Async command lifecycle | Async scene operations can be pushed to undo history before success/failure is known | `CreateSceneCommand.kt`, `OpenSceneCommand.kt`, `UndoRedoManager.kt` | Race conditions and invalid history entries |
| P1 | ECS cache invalidation | Cache refresh is object-set centric; component add/remove does not uniformly invalidate | `SystemManager.kt`, `GameObject.kt`, `AnimationSystem.kt` | Stale system views, subtle runtime bugs |
| P1 | DI consistency | Mixed constructor DI, field injection/service-locator style, and manual core construction | `Scene.kt`, `BulletPhysics3D.kt`, `ViewportActionHandler.kt` | Lifecycle ambiguity and reduced testability |
| P1 | Duplication/uniformity debt | Same behavior implemented differently in multiple modules | duplicate-object flows in 4 places; scene traversal duplicated in serializer/project manager | Higher bug rate and maintenance overhead |
| P2 | Layering | Engine event types coupled to editor types | `engine/events/ViewportAction.kt` importing editor `PrefabType` | Architectural coupling and reduced modularity |
| P2 | Localization consistency | Remaining hardcoded user-facing strings in runtime/editor UI paths | `GameViewWindow.kt`, `ProjectWindow.kt`, `AudioSystem.kt`, `InputSystem.kt`, action labels | Incomplete i18n compliance and UX inconsistency |
| P2 | Input consistency | Scroll/capture handling behavior differs across axes | `MouseListener.kt` (`getScrollX` vs `getScrollY`) | Confusing interaction behavior |

## Uniformity Debt Hotspots

1. **Mutation entry points are mixed**
   - Event-driven + handler + command in some surfaces
   - direct command execution in others
   - direct mutable-state writes in others

2. **Duplicate object behavior diverges by entry path**
   - Different naming, offsets, component-copy behavior, selection effects

3. **Scene graph traversal logic is duplicated and inconsistent**
   - Serializer and project manager use separate traversal/resolution paths

4. **Undo semantics are not canonical**
   - Undoable, execute-only, and async operations are not clearly separated

5. **Localization discipline is uneven**
   - Some modules are key-driven, others still use literals

## Recommended Action Plan

### Phase 1 — Quick Wins (stabilization)

1. Enforce no direct state mutation in `editor/ui/**` entry points.
2. Add play-mode mutation gate in handlers/commands.
3. Fix known consistency defects:
   - `SystemsWindow` split correctness
   - mouse scroll capture uniformity
4. Localize remaining UI-visible strings.

### Phase 2 — Structural Convergence

1. Introduce a **single mutation gateway**:
   - UI publishes events only
   - handlers execute commands
2. Refactor undo model:
   - explicit command categories: undoable / execute-only / async-completing
3. Add component-mutation versioning/invalidation path for ECS caches.
4. Decouple engine events from editor-only types.
5. Consolidate duplicate scene/object operations into shared services.

### Phase 3 — Hardening and Prevention

1. Reduce/remove `KoinComponent` usage from engine core in favor of constructor DI.
2. Move blocking file operations to async execution paths where needed.
3. Add architecture guard tests and standard fixtures to prevent pattern drift.

## Immediate Work Clusters (Backlog-Ready)

- **Cluster A:** UI/Event/Command conformance cleanup
- **Cluster B:** Undo + async command lifecycle refactor
- **Cluster C:** ECS component-mutation invalidation
- **Cluster D:** Duplication consolidation (duplicate-object + scene walker)
- **Cluster E:** DI/layering cleanup
- **Cluster F:** Localization completion + architectural guard tests

## Suggested Guardrails

1. Source-level architecture tests to fail on:
   - direct mutation in `editor/ui/**`
   - `engine/**` importing editor packages
2. Tests/checks for:
   - command-stack correctness (undoable vs execute-only vs async)
   - component mutation invalidation behavior
3. Shared test utilities for async/job behavior to eliminate per-test drift.

