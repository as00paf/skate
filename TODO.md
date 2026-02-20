# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.15: Rendering Pipeline Fixes

### Critical Issues

- [ ] **A15.1: Fix Lambda Capture for Window Dimensions** - `RenderResourcesFactory.kt`:
    - Current lambdas `{ width }` and `{ height }` capture initial values at factory creation time
    - After window resize, PickingPass and GeometryPass use stale dimensions
    - **Solution Options**:
        1. Pass references to `Window` object and read dimensions directly in passes
        2. Create shared mutable state object that gets updated on resize
        3. Pass `Renderer` reference and read from `frameBuffer.width/height`
    - **Impact**: Severe - Picking and rendering broken after any window resize
    - Location: `src/main/kotlin/com/pafoid/skate/engine/render/RenderResourcesFactory.kt:167-180`

- [ ] **A15.2: Add Resize Propagation to RenderPasses** - `Renderer.kt`:
    - `PickingPass.resize()` exists but is never called
    - `GeometryPass` may need resize handling for proper viewport management
    - Update `Renderer.resize()` to propagate to all passes:
      ```kotlin
      fun resize(width: Int, height: Int) {
          renderResources.frameBuffer.resize(width, height)
          renderResources.pickingTexture.resize(width, height)
          renderResources.renderPasses.picking.resize(width, height)
      }
      ```
    - **Impact**: High - Passes don't know about new dimensions after resize
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

## Notes

- All tasks from phases A10-A14 are complete - see CHANGELOG.md for details
- Use the template above for new phase tasks
- v0.15 focuses on fixing issues found during rendering pipeline review
