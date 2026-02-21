# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.15: Rendering Pipeline Fixes

### Critical Issues

- [x] **A15.1: Fix Lambda Capture for Window Dimensions** - `RenderResourcesFactory.kt`:
  - **Problem**: Lambdas `{ width }` and `{ height }` captured initial values at factory creation time
  - **Solution**: Pass `FrameBuffer` and `PickingTexture` directly; passes read `width`/`height` properties
  - **Changes**:
    - Removed `getWindowWidth`/`getWindowHeight` lambdas from `PickingPass` and `GeometryPass`
    - Made `PickingTexture.width/height` public (was private)
    - Passes now read dimensions from `frameBuffer.width/height` and `pickingTexture.width/height`
  - **Impact**: Severe - Fixed picking and rendering broken after window resize
    - Location: `src/main/kotlin/com/pafoid/skate/engine/render/RenderResourcesFactory.kt:167-180`

- [x] **A15.2: Add Resize Propagation to RenderPasses** - `Renderer.kt`:
  - Updated `Renderer.resize()` to call `renderResources.renderPasses.picking.resize(width, height)`
  - Ensures PickingPass is notified of dimension changes
  - **Impact**: High - Passes now correctly updated after resize
    - Location: `src/main/kotlin/com/pafoid/skate/engine/render/renderer/Renderer.kt:157`

### Performance Optimizations

- [x] **A15.3: Consolidate Camera Viewport Updates** - `PickingPass.kt`, `GeometryPass.kt`:
  - **Problem**: Camera viewport was set twice per frame (once in each pass)
  - **Solution**: Move viewport update to `Renderer.render()` before executing any passes
  - **Changes**:
    - Added camera viewport update at start of `Renderer.render()`
    - Removed redundant `scene.camera.viewportWidth/Height` assignments from `PickingPass.execute()`
    - Removed redundant `camera.viewportWidth/Height` assignments from `GeometryPass.execute()`
  - **Impact**: Low - Minor performance improvement, cleaner code, single source of truth
    - Locations:
      - `src/main/kotlin/com/pafoid/skate/engine/render/renderer/Renderer.kt:67-70`
      - `src/main/kotlin/com/pafoid/skate/engine/render/renderer/passes/PickingPass.kt`
      - `src/main/kotlin/com/pafoid/skate/engine/render/renderer/passes/GeometryPass.kt`

### Documentation

- [x] **A15.4: Document Picking Skip Behavior** - `PickingPass.kt`:
  - **Changes**:
    - Added comprehensive KDoc with "## Picking Optimization" section
    - Documented when picking runs vs. when it's skipped
    - Explained benefits: GPU draw call savings, CPU iteration savings, prevents accidental selection
    - Documented how to re-enable picking (deselect with ESC or click empty space)
    - Added "## Technical Details" section explaining the encoding mechanism
    - Enhanced inline comment in `execute()` method
  - **Impact**: Low - Code clarity for future maintainers
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/renderer/passes/PickingPass.kt`

- [x] **A15.5: Add OpenGL Context Requirement KDoc** - `RenderResourcesFactory.kt`:
  - **Changes**:
    - Added "**Important**" warning that method requires active OpenGL context
    - Documented that it will crash if called before context creation
    - Added "## Initialization Order" section with detailed steps and notes
    - Added "## Usage" section explaining when/where to call
    - Added `@throws IllegalStateException` documentation
  - **Impact**: Low - Prevents initialization order bugs during engine modifications
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/RenderResourcesFactory.kt:39-63`

### Code Quality

- [x] **A15.6: Review Shader Buffer Thread Safety** - `Shader.kt`:
  - **Changes**:
    - Added "## Thread Safety" section to buffer KDoc
    - Clearly states buffers are NOT thread-safe
    - Explains corruption risk if multiple threads call upload methods concurrently
    - Documented current single-threaded usage pattern
    - Added "### Future Multi-threaded Rendering" section with migration options:
      - ThreadLocal<FloatBuffer> approach
      - Synchronization approach (with performance note)
      - Worker thread to render thread buffer passing
    - Added @see references to all upload methods
  - **Impact**: Low - Documentation for future maintainers considering multi-threaded rendering
  - Location: `src/main/kotlin/com/pafoid/skate/engine/assets/data/Shader.kt:35-65`

---

## 🟡 Phase A16: Dependency Injection Consistency (Rendering Pipeline)

### Problem Statement

The rendering pipeline components currently use a mix of Constructor Injection and Field Injection (`by inject()`). To
improve testability and strictly adhere to the Dependency Inversion Principle, we should standardize on explicit
Constructor Injection for rendering components.

- [x] **A16.1: Standardize Constructor Injection in Renderers** - Remove `by inject()` from renderers:
  - **Changes**:
    - `ModelRenderer`: Removed `: KoinComponent`, added `debugRenderer` to constructor
    - `DebugRenderer`: Removed `: KoinComponent`, added `resourceManager`, `logger`, `sceneManager` to constructor
    - `PickingRenderer`: Removed `: KoinComponent`, added `resourceManager`, `logger`, `sceneManager` to constructor
    - `Camera`: Removed `: KoinComponent`, added `inputProvider`, `keyListener`, `mouseListener`, `sceneManager` to
      constructor
    - Removed unused `org.koin.core.component` imports
  - **Impact**: Medium - Improves testability and explicitly declares class dependencies

- [x] **A16.2: Update RenderResourcesFactory** - Inject explicit dependencies:
  - **Changes**:
    - `ModelRenderer` now receives `debugRenderer` parameter
    - `PickingRenderer` now receives `resourceManager`, `logger`, `sceneManager` parameters
  - **Impact**: Medium - Factory takes full responsibility for wiring dependencies

- [x] **A16.3: Update Koin Module** - `KoinModule.kt`:
  - **Changes**:
    - Updated `DebugRenderer` definition: `single { DebugRenderer(get(), get(), get()) }`
    - Updated `PickingRenderer` definition: `single { PickingRenderer(get(), get(), get()) }`
    - Updated `ThumbnailCache` definition: `single { ThumbnailCache(get(), get(), get(), get(), get()) }`
    - Updated `BootManager` definition: added `inputProvider`, `keyListener`, `mouseListener` parameters
    - Reordered modules: `inputModule` now defined before `engineModule` for proper dependency order
    - Removed duplicate `BootManager` definition from `appModule`
  - **Impact**: Low - Alignment with new constructor signatures

### Related Changes

- **Scene.kt**: Updated constructor to accept and forward input dependencies to `Camera`
- **BootManager.kt**: Updated to accept and forward input dependencies to `Scene`
- **ThumbnailCache.kt**: Removed `KoinComponent`, now uses constructor injection for `Camera` dependencies

---

## Notes

- All tasks from phases A10-A14 are complete - see CHANGELOG.md for details
- **v0.15 Complete**: All 6 rendering pipeline fixes implemented:
  - A15.1: Fixed lambda capture bug (severe - picking broken after resize)
  - A15.2: Added resize propagation to PickingPass
  - A15.3: Consolidated camera viewport updates (single source of truth)
  - A15.4: Documented picking skip behavior optimization
  - A15.5: Added OpenGL context requirement documentation
  - A15.6: Documented shader buffer thread safety assumptions
- **v0.16 Complete**: Constructor injection standardized across rendering pipeline
- Use the template above for new phase tasks

---

## 🔴 v0.17: Camera Architecture Refactoring

### Problem Statement

`Camera.kt` contains gameplay-specific third-person camera logic (gamepad input, physics clipping) mixed with core
camera functionality. This violates separation of concerns between engine, game, and editor components.

### Current Architecture Issues

**Camera.kt** (Should be Engine Component) contains:

- Gamepad input handling (RS rotation for third-person)
- Mouse rotation when cursor disabled (gameplay mode)
- Physics clipping (raycast against scene)
- Dependencies on `IInputProvider` and `SceneManager`

**What we want:**

- **Camera**: Pure engine component with NO input dependencies
- **GameCamera**: Gameplay third-person controller with gamepad support
- **EditorCamera**: Editor free-fly navigation (already correct ✅)

### Tasks

- [x] **A17.1: Extract Free-Fly Movement to EditorCamera** - `Camera.kt`, `EditorCamera.kt`:
  - **Changes**:
    - Moved `move()` method from Camera to EditorCamera as `handleFreeFlyMovement()`
    - Moved mouse rotation logic from Camera.move() to EditorCamera.handleFreeFlyMovement()
    - Removed `update()` conditional branch (`else { move() }`) from Camera
    - Simplified Camera.update() to only handle third-person camera when `target != null`
    - Added RMB (right mouse button) rotation for free-fly mode in EditorCamera
    - Added WASD + Space/Shift movement in EditorCamera
    - Removed unused GLFW key imports from Camera.kt
  - **Impact**: High - Cleaner separation between engine (Camera) and editor (EditorCamera)
  - Locations:
    - `src/main/kotlin/com/pafoid/skate/engine/render/Camera.kt:100-106`
    - `src/main/kotlin/com/pafoid/skate/editor/EditorCamera.kt:44-95`

- [x] **A17.2: Create GameCamera Class** - New `GameCamera.kt`:
  - **Changes**:
    - Created `com.pafoid.skate.game.camera.GameCamera` class
    - Moved `updateThirdPerson()` from Camera to GameCamera
    - Moved `handleClipping()` (physics raycast) from Camera to GameCamera
    - Moved gamepad input handling (RS rotation) from Camera to GameCamera
    - GameCamera wraps/composes a base Camera instance
    - GameCamera takes `IInputProvider`, `SceneManager`, and `Camera` as dependencies
  - **Impact**: High - Gameplay logic separated from engine component
  - Location: `src/main/kotlin/com/pafoid/skate/game/camera/GameCamera.kt`

- [x] **A17.3: Remove Input Dependencies from Camera** - `Camera.kt`:
  - **Changes**:
    - Removed `IInputProvider`, `KeyListener`, `MouseListener`, `SceneManager` constructor parameters
    - Removed `speed` property (editor/game specific)
    - Removed `target`, `desiredDistance`, `targetOffset` (gameplay third-person)
    - Removed `updateThirdPerson()` and `handleClipping()` methods
    - Simplified `update()` to only handle preset interpolation
    - Removed all input-related imports
    - Added KDoc explaining Camera is a pure engine component
    - Updated `CameraPreset` to use `zoom` instead of `distance`
  - **Impact**: High - Camera usable in headless/server/thumbnail contexts
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/Camera.kt`

- [x] **A17.4: Update Usage Sites** - All files creating Camera instances:
  - **Changes**:
    - `Scene.kt`: Simplified to `Camera()` with no parameters
    - `ThumbnailCache.kt`: Removed input dependencies, uses `Camera(position = ...)`
    - `BootManager.kt`: Removed input dependencies from constructor and Scene creation
    - `KoinModule.kt`: Updated `ThumbnailCache` and `BootManager` definitions with fewer parameters
  - **Impact**: Medium - Proper dependency injection for camera types
  - Locations:
    - `src/main/kotlin/com/pafoid/skate/engine/ecs/Scene.kt`
    - `src/main/kotlin/com/pafoid/skate/editor/systems/ThumbnailCache.kt`
    - `src/main/kotlin/com/pafoid/skate/engine/core/BootManager.kt`
    - `src/main/kotlin/com/pafoid/skate/app/KoinModule.kt`

---

## Notes

- All tasks from phases A10-A14 are complete - see CHANGELOG.md for details
- **v0.15 Complete**: All 6 rendering pipeline fixes implemented
- **v0.16 Complete**: Constructor injection standardized across rendering pipeline
- **v0.17 Complete**: Camera architecture refactored into three-tier design:
  - Camera: Pure engine component (no input dependencies)
  - GameCamera: Gameplay third-person controller (gamepad support, physics clipping)
  - EditorCamera: Editor free-fly navigation (WASD, MMB orbit, zoom)
- Use the template above for new phase tasks
