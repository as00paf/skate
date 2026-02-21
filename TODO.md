# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.18: Fix Test Compilation After Camera Refactoring

### Problem Statement

The camera architecture refactoring (v0.17) broke several test files that need to be updated to match the new API.

### Test Compilation Errors

- [ ] **A18.1: Fix CameraTest** - `CameraTest.kt`:
  - **Errors**:
    - Unresolved reference `desiredDistance` (removed from Camera)
    - No parameter `distance` in CameraPreset (renamed to `zoom`)
  - **Fix**:
    - Update tests to use `camera.zoom` instead of `camera.desiredDistance`
    - Update CameraPreset usage to use `zoom` parameter
  - Location: `src/test/kotlin/com/pafoid/skate/engine/render/CameraTest.kt:50-70`

- [ ] **A18.2: Fix BootManagerTest** - `BootManagerTest.kt`:
  - **Errors**:
    - Unresolved reference `initFrameBuffer` (removed during A12 refactoring)
    - Unresolved reference `loadShaders` (removed during A12 refactoring)
    - Cannot infer type for lambda parameters
  - **Fix**:
    - Update test to use new Renderer API (`renderer.initialize()`)
    - Remove references to removed methods
    - Fix lambda type inference issues
  - Location: `src/test/kotlin/com/pafoid/skate/engine/BootManagerTest.kt:86-94`

- [ ] **A18.3: Fix BoardRigTest** - `BoardRigTest.kt`:
  - **Errors**:
    - Cannot access `stateManager` (private in PlayerController)
    - Unsafe calls on nullable `PlayerStateManager?`
  - **Fix**:
    - Update test to not access private `stateManager` property
    - Use safe calls or Elvis operator for nullable types
  - Location: `src/test/kotlin/com/pafoid/skate/engine/scenes/components/BoardRigTest.kt:144`

- [ ] **A18.4: Fix PlayerControllerTest** - `PlayerControllerTest.kt`:
  - **Errors**:
    - Cannot access `stateManager` (private in PlayerController)
    - Unsafe calls on nullable `PlayerStateManager?`
  - **Fix**:
    - Update test to not access private `stateManager` property
    - Use safe calls or Elvis operator for nullable types
  - Location: `src/test/kotlin/com/pafoid/skate/engine/scenes/components/PlayerControllerTest.kt:104-111`

- [ ] **A18.5: Fix TrickDetectionTest** - `TrickDetectionTest.kt`:
  - **Errors**:
    - Cannot access `stateManager` (private in PlayerController)
    - Unsafe calls on nullable `PlayerStateManager?`
  - **Fix**:
    - Update test to not access private `stateManager` property
    - Use safe calls or Elvis operator for nullable types
  - Location: `src/test/kotlin/com/pafoid/skate/engine/scenes/components/TrickDetectionTest.kt:152`

---

## Notes

- See CHANGELOG.md for completed v0.15, v0.16, and v0.17 items
- Use the template above for new phase tasks
