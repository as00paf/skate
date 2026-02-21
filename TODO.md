# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.19: ECS Systems Code Quality & Architecture

### Problem Statement

The ECS systems implementation has several code quality issues and architectural inconsistencies that should be
addressed for better maintainability and performance.

### Remaining Issues

- [ ] **A19.6: Add System Execution Order** - `SystemManager.kt`, `System.kt`:
  - **Problem**: Systems run in arbitrary list order, no way to specify dependencies
  - **Fix**: Add `priority` field to System, sort before execution
  - **Impact**: Low - Deterministic execution order for systems with dependencies
  - **Locations**:
    - `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/System.kt`
    - `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/SystemManager.kt`

- [ ] **A19.7: Remove Koin Field Injection from GizmoSystem** - `GizmoSystem.kt`:
  - **Problem**: GizmoSystem uses `by inject()` for all dependencies (KeyListener, MouseListener, etc.)
  - **Fix**:
    - Pass dependencies via constructor parameters
    - Remove `: KoinComponent` from GizmoSystem
    - Update Koin module to pass dependencies with `get()`
  - **Impact**: Low - Consistent with v0.16 constructor injection pattern, clearer dependencies
  - **Location**: `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/GizmoSystem.kt`

---

## Notes

- See CHANGELOG.md for completed v0.15, v0.16, v0.17, v0.18, and v0.19 items
- Use the template above for new phase tasks
