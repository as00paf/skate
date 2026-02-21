# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.18: Fix Test Compilation After Camera Refactoring

### Problem Statement

The camera architecture refactoring (v0.17) broke several test files that need to be updated to match the new API.

### Test Compilation Errors

- [x] **A18.1: Fix CameraTest** - `CameraTest.kt`:
  - **Changes**:
    - Removed unused Koin/MockK imports and setup (no longer needed after Camera refactoring)
    - Changed `camera.desiredDistance` to `camera.zoom`
    - Updated CameraPreset constructor calls to use `zoom` parameter instead of `distance`
    - Updated test expectations to use zoom values (1.0 to 1.5) instead of distance values (5 to 10)
  - **Status**: Fixed and compiles successfully

- [x] **A18.2: Fix BootManagerTest** - `BootManagerTest.kt`:
  - **Changes**:
    - Updated `renderer.initFrameBuffer()` to `renderer.initialize()` (new API after A12 refactoring)
    - Removed `renderer.loadShaders()` verification (method removed during A12 refactoring)
    - Removed unused mocks: `mouseListener`, `debugRenderer`
    - Removed unused imports: `MouseListener`, `DebugRenderer`
    - Updated Koin module setup to remove unused dependencies
  - **Status**: Fixed and compiles successfully

- [x] **A18.3: Fix BoardRigTest** - `BoardRigTest.kt`:
  - **Changes**:
    - Added `PlayerStateManager` component to test board GameObject
    - Changed `controller.stateManager.transitionToState()` to `stateManager.transitionToState()`
    - Added import for `PlayerStateManager`
    - Test now properly adds required component instead of accessing private property
  - **Status**: Fixed and compiles successfully

- [x] **A18.4: Fix PlayerControllerTest** - `PlayerControllerTest.kt`:
  - **Changes**:
    - Updated controller retrieval from `skateboard.getComponent()` to `skater.getComponent()` (PlayerController moved
      to Skater)
    - Changed `controller.stateManager` to `skater.getComponent<PlayerStateManager>()` (component on Skater, not private
      property)
    - Added comment explaining PlayerController is now on the Skater, not the skateboard
  - **Status**: Fixed and compiles successfully

- [x] **A18.5: Fix TrickDetectionTest** - `TrickDetectionTest.kt`:
  - **Changes**:
    - Added `PlayerStateManager` import
    - Added `PlayerStateManager` component to test skateboard GameObject
    - Changed `controller.stateManager.transitionToState()` to `stateManager.transitionToState()`
    - Test now properly adds required component instead of accessing private property
  - **Status**: Fixed and compiles successfully

---

## Notes

- **v0.18 Complete**: All test compilation errors fixed:
  - A18.1: CameraTest - Updated to use zoom instead of distance
  - A18.2: BootManagerTest - Updated to use new Renderer API
  - A18.3: BoardRigTest - Added PlayerStateManager component
  - A18.4: PlayerControllerTest - Updated for PlayerController on Skater
  - A18.5: TrickDetectionTest - Added PlayerStateManager component
- See CHANGELOG.md for v0.15, v0.16, and v0.17 details
- Use the template above for new phase tasks
