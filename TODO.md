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

- [ ] **A15.3: Consolidate Camera Viewport Updates** - `PickingPass.kt`, `GeometryPass.kt`:
    - Camera viewport is set twice per frame (once in each pass)
    - Move viewport update to `Renderer.render()` before executing any passes:
      ```kotlin
      // In Renderer.render(), before pass execution:
      scene.camera.viewportWidth = width
      scene.camera.viewportHeight = height
      ```
    - Remove redundant assignments from individual passes
    - **Impact**: Low - Minor performance improvement, cleaner code
    - Locations:
        - `src/main/kotlin/com/pafoid/skate/engine/render/renderer/passes/PickingPass.kt:83`
        - `src/main/kotlin/com/pafoid/skate/engine/render/renderer/passes/GeometryPass.kt:97`

### Documentation

- [ ] **A15.4: Document Picking Skip Behavior** - `PickingPass.kt`:
    - Add KDoc explaining that picking is skipped when object is selected
    - Document this is intentional for performance optimization
    - Note that hover detection is disabled while object is selected
    - **Impact**: Low - Code clarity
    - Location: `src/main/kotlin/com/pafoid/skate/engine/render/renderer/passes/PickingPass.kt:74`

- [ ] **A15.5: Add OpenGL Context Requirement KDoc** - `RenderResourcesFactory.kt`:
    - Document that `GLStateTracker.initialize()` requires active OpenGL context
    - Add precondition that `create()` must be called after context creation
    - **Impact**: Low - Prevents initialization order bugs
    - Location: `src/main/kotlin/com/pafoid/skate/engine/render/RenderResourcesFactory.kt:54`

### Code Quality

- [ ] **A15.6: Review Shader Buffer Thread Safety** - `Shader.kt`:
    - Reusable buffers (`matrixBuffer`, `vec2Buffer`, etc.) are not thread-safe
    - Add KDoc noting single-threaded rendering assumption
    - **Future**: Consider `ThreadLocal` buffers if multi-threaded rendering is needed
    - **Impact**: Low - Documentation for future maintainers
    - Location: `src/main/kotlin/com/pafoid/skate/engine/assets/data/Shader.kt:37-41`

---

## 🟡 Phase A16: Dependency Injection Consistency (Rendering Pipeline)

### Problem Statement

The rendering pipeline components currently use a mix of Constructor Injection and Field Injection (`by inject()`). To
improve testability and strictly adhere to the Dependency Inversion Principle, we should standardize on explicit
Constructor Injection for rendering components.

- [ ] **A16.1: Standardize Constructor Injection in Renderers** - Remove `by inject()` from renderers:
  - Update `ModelRenderer.kt` to accept `DebugRenderer` in its constructor.
  - Update `PickingRenderer.kt` to accept `ResourceManager`, `LoggerService`, and `SceneManager` in its constructor.
  - Update `DebugRenderer.kt` to accept `ResourceManager`, `LoggerService`, and `SceneManager` in its constructor.
  - Update `Camera.kt` to accept `IInputProvider`, `KeyListener`, `MouseListener`, and `SceneManager` in its
    constructor.
  - Remove `: KoinComponent` from these classes where applicable.
  - **Impact**: Medium - Improves testability and explicitly declares class dependencies.

- [ ] **A16.2: Update RenderResourcesFactory** - Inject explicit dependencies:
  - Pass the required dependencies to `PickingRenderer` when it is instantiated.
  - Pass the required dependencies to `ModelRenderer` when it is instantiated.
  - **Impact**: Medium - Factory takes full responsibility for wiring dependencies.

- [ ] **A16.3: Update Koin Module** - `KoinModule.kt`:
  - Update the definitions for `Camera` and `DebugRenderer` (if they are declared in Koin) to pass constructor
    parameters using `get()`.
  - **Impact**: Low - Alignment with new constructor signatures.

---

## Notes

- All tasks from phases A10-A14 are complete - see CHANGELOG.md for details
- Use the template above for new phase tasks
- v0.15 focuses on fixing issues found during rendering pipeline review
