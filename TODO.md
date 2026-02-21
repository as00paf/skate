# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.19: ECS Systems Code Quality & Architecture

### Problem Statement

The ECS systems implementation has several code quality issues and architectural inconsistencies that should be
addressed for better maintainability and performance.

### Completed Items

- [x] **A19.6: Add System Execution Order** - `SystemManager.kt`, `System.kt`:
  - **Changes**:
    - Added `priority` parameter to `System` constructor (default 0)
    - Lower priority values execute first (negative = early, positive = late)
    - `SystemManager` sorts systems by priority before each update cycle
    - Uses lazy sorting (only sorts when systems are added/removed)
    - Changed `systems` list to private with public read-only view
    - Changed `getSystem()` from inline reified to regular function with Class parameter
  - **Impact**: Low - Deterministic execution order for systems with dependencies
  - **Status**: Fixed and compiles successfully

- [x] **A19.7: Remove Koin Field Injection from GizmoSystem** - `GizmoSystem.kt` and related:
  - **Changes**:
    - Converted `GizmoSystem`, `MouseControls`, `EditorCamera`, `GridLines` to constructor injection
    - Removed `: KoinComponent` from all System subclasses
    - Removed `by inject()` property delegates
    - Updated `LevelEditorSceneInitializer` to inject dependencies and pass to system constructors
    - Updated Koin module to register systems with constructor parameters
    - Assigned appropriate priorities:
      - MouseControls: -100 (early - input processing)
      - EditorCamera: -50 (early - input processing)
      - AnimationSystem: 0 (default - physics/animation)
      - GridLines: 50 (mid - rendering)
      - GizmoSystem: 100 (late - UI/tools)
  - **Impact**: Low - Consistent with v0.16 constructor injection pattern, clearer dependencies
  - **Status**: Fixed and compiles successfully

---

## Notes

- See CHANGELOG.md for completed v0.15, v0.16, v0.17, v0.18, and v0.19 items
- Use the template above for new phase tasks
