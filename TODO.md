# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.19: ECS Systems Code Quality & Architecture

### Problem Statement

The ECS systems implementation has several code quality issues and architectural inconsistencies that should be
addressed for better maintainability and performance.

### High Priority Issues

- [x] **A19.1: Fix AnimationSystem Double Update Bug** - `AnimationSystem.kt`:
  - **Problem**: `animation.update()` was called twice when `blendTime <= 0`
  - **Fix**: Removed duplicate call on line 62 (after the if/else block)
  - **Impact**: High - Eliminated wasted computation and potential animation state corruption
  - **Status**: Fixed and compiles successfully

- [x] **A19.2: Fix Animation Blending Skeleton Collapse** - `AnimationSystem.kt`:
  - **Problem**: During animation blending, skeleton transforms were corrupted causing mesh to collapse
  - **Root Cause**: `prev.update()` modified skeleton bones, then `updateBlended()` read corrupted state
  - **Fix**:
    - Apply previous animation and snapshot its pose
    - Apply new animation and snapshot its pose
    - Blend between the two snapshots using proper transform interpolation
    - Added `blendTransforms()` helper for clean position/rotation/scale interpolation
  - **Impact**: Critical - Animation blending now works correctly without skeleton corruption
  - **Status**: Fixed and compiles successfully

- [x] **A19.3: Refactor GizmoSystem Architecture** - `GizmoSystem.kt`:
  - **Problem**: GizmoSystem registered gizmo systems with Scene but also managed them internally
    - All gizmo systems ran every frame even when not active (wasted computation)
    - Used `setInUse()` / `setNotInUse()` instead of proper enable/disable pattern
    - Unclear ownership and lifecycle
  - **Fix**:
    - GizmoSystem now owns gizmos directly (not registered as separate systems)
    - Only the active gizmo is updated each frame (based on `usingGizmo` state)
    - Removed `scene.addSystem()` calls for individual gizmos
    - Added KDoc explaining the ownership model
  - **Impact**: High - Cleaner architecture, better performance (only 1 gizmo updated per frame), clearer ownership
  - **Status**: Fixed and compiles successfully

### Medium Priority Issues

- [x] **A19.4: Remove AnimationSystem Redundant Methods** - `AnimationSystem.kt`:
  - **Problem**: `update()` and `editorUpdate()` have identical logic

- [ ] A19.5: Optimize System Query Pattern - `AnimationSystem.kt`:
  - **Problem**: Every frame, iterates ALL GameObjects and filters by component type (O(n) per frame)
  - **Fix**: Cache list of eligible GameObjects, update when components are added/removed
  - **Impact**: Medium - Performance improvement for scenes with many GameObjects
  - **Location**: `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/AnimationSystem.kt:16`

### Low Priority Issues

- [ ] **A19.6: Add System Execution Order** - `SystemManager.kt`, `System.kt`:
  - **Problem**: Systems run in arbitrary list order, no way to specify dependencies
  - **Fix**: Add `priority` field to System, sort before execution
  - **Impact**: Low - Deterministic execution order for systems with dependencies
  - **Locations**:
    - `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/System.kt`
    - `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/SystemManager.kt`

---

## Notes

- See CHANGELOG.md for completed v0.15, v0.16, v0.17, and v0.18 items
- Use the template above for new phase tasks
