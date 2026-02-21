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

`Camera.kt` contains editor-specific free-fly movement code mixed with core camera functionality (projection/view
matrices, third-person gameplay camera). This violates separation of concerns between engine and editor components.

### Current Issues

**Camera.kt** (Engine Component) contains:

- `move()` method with WASD controls - Editor-only free-fly movement
- Mouse rotation when cursor disabled - Editor-only input handling
- `update()` conditional logic that branches between editor and gameplay modes
- Input dependencies (`IInputProvider`, `KeyListener`, `MouseListener`) primarily for editor usage

**EditorCamera.kt** (Editor System) should contain:

- All editor viewport navigation controls
- Free-fly WASD movement
- MMB rotation, scroll zoom, Home reset (already has these ✅)

### Tasks

- [ ] **A17.1: Extract Free-Fly Movement to EditorCamera** - `Camera.kt`, `EditorCamera.kt`:
  - Move `move()` method from Camera to EditorCamera
  - Move mouse rotation logic from Camera.move() to EditorCamera
  - Remove `update()` conditional branch (`else { move() }`)
  - Simplify Camera.update() to only handle third-person camera when `target != null`
  - **Impact**: High - Cleaner separation between engine and editor

- [ ] **A17.2: Remove Input Dependencies from Camera** - `Camera.kt`:
  - Remove `IInputProvider`, `KeyListener`, `MouseListener` constructor parameters
  - Remove `SceneManager` dependency (only used for clipping in third-person)
  - Camera becomes a pure data/orientation component
  - **Impact**: High - Camera can be used in headless/server contexts

- [ ] **A17.3: Update EditorCamera to Own Camera State** - `EditorCamera.kt`:
  - EditorCamera directly manipulates camera position/orientation
  - Add speed controls for editor free-fly movement
  - Handle all input in editorUpdate() instead of delegating to Camera.update()
  - **Impact**: Medium - EditorCamera becomes single source of truth for editor controls

- [ ] **A17.4: Update Camera Constructor Usage** - All files creating Camera instances:
  - Update `Scene.kt` constructor (no longer passes input dependencies)
  - Update `ThumbnailCache.kt` constructor (no longer passes input dependencies)
  - Update `BootManager.kt` (no longer passes input dependencies to Scene)
  - **Impact**: Medium - Simplified Camera construction across codebase
