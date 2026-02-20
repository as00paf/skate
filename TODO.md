# 🛹 SkateSim MVP - Master TODO (Part 2)

## 🟢 Phase A: Render Upgrades Code Quality & Refactoring

### A10. Render Code Duplication Refactoring

- [x] **A10.1: ModelRenderer Consolidation** - Merge `render()` and `renderSimple()` methods:
  - Extract common mesh rendering logic into a private helper method
  - Create unified render method with parameters for shader type and uniform uploads
  - Extract texture binding pattern into a reusable helper function
- [x] **A10.2: Extract Lighting Uniforms Loader** - Create dedicated class for shader uniform uploads:
  - Move all lighting uniform uploads from `Renderer.render()` to `LightingUniformsLoader`
  - Include sun, moon, ambient, fog, and camera position uniforms
- [x] **A10.3: Remove Helper Methods** - Inline or relocate simple wrappers:
  - Remove `loadProjectionMatrix()` and `loadViewMatrix()` from `Renderer.kt`
  - Consider creating Camera extension functions for matrix creation
- [x] **A10.4: Create OpenGL State Helpers** - New file
  `src/main/kotlin/com/pafoid/skate/engine/render/utils/GLState.kt`:
  - Extension function `bindVAO(vaoId, attributes)` for consistent VAO binding
  - Extension function `unbindVAO(vaoId, attributes)` for cleanup
  - Extension function `bindTexture(slot, texture, fallback)` for texture binding pattern
  - Extension function `withDepthFunc(func, block)` for depth function scoping
- [x] **A10.5: Renderer2D Reorganization** - Improve separation of concerns:
  - Move `RenderBatch` constants to companion object
  - Evaluate if `Renderer2D` and `RenderBatch` should be merged (kept separate - clear responsibilities)
  - Extract vertex property loading into a dedicated method with better naming

---

## 🔴 Phase A11: Rendering Performance Fixes (Pipeline Review Findings)

### Critical Performance Issues

- [x] **A11.1: Cache Shader Uniform Locations** - `Shader.kt`:
  - Add `private val uniformCache = mutableMapOf<String, Int>()` to Shader class
  - Create `getLocation(varName)` helper that caches `glGetUniformLocation` results
  - Update all `upload*()` methods to use cached locations
  - **Impact**: Severe - glGetUniformLocation is extremely expensive in hot paths

- [x] **A11.2: Reuse Buffer Objects** - `Shader.kt`:
  - Create reusable buffers as class properties (matrixBuffer, vec3Buffer, etc.)
  - Call `buffer.clear()` before each use instead of allocating new buffers
  - **Impact**: High - Reduces GC pressure and frame stutter
  - **Note**: Completed as part of A11.1; other BufferUtils calls are in asset loading (not hot paths)

- [x] **A11.3: Query Hardware Texture Limits** - `RenderBatch.kt`:
  - Replace hardcoded `maxTextureSlots = 8` with OpenGL query
  - Use `GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS)`
  - **Impact**: Medium - Hardware compatibility

- [x] **A11.4: Implement Proper State Tracking** - New `GLStateTracker.kt`:
  - Track current OpenGL state (blend, depth mask, cull face, depth func)
  - Only call gl functions when state actually changes
  - Update GLState helpers to use GLStateTracker
  - Add `GLStateTracker.initialize()` call in Renderer.initFrameBuffer()
  - **Impact**: High - Reduces redundant OpenGL calls

### Architecture Improvements

- [x] **A11.5: Extract RenderPass Interface**:
  - Create
    `interface RenderPass { fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) }`
  - Extract PickingPass, GeometryPass, DebugPass implementations
  - Update Renderer to orchestrate passes instead of implementing them
  - **Impact**: Medium - Better separation of concerns, easier testing

- [x] **A11.6: Implement Proper Z-Index for 2D Sprites** - `Renderer2D.kt`:
  - Add `zIndex` property to SpriteRenderer component
  - Remove hardcoded `zIndex == 0` check
  - Group batches by z-index using `Map<Int, List<RenderBatch>>`
  - Render batches in z-index order (lowest to highest)
  - **Impact**: Medium - Proper 2D layering

- [x] **A11.7: Define Texture Slot Constants** - New `TextureSlots.kt`:
  - Extract magic numbers (0, 1, 2, 3, 4) to named constants
  - BASE_COLOR, NORMAL, METALLIC_ROUGHNESS, AO, EMISSIVE
  - Update ModelRenderer to use constants instead of magic numbers
  - **Impact**: Low - Code clarity

### Code Quality

- [x] **A11.8: Fix Aspect Ratio Handling** - `Camera.kt`:
  - Add `viewportWidth` and `viewportHeight` properties to Camera
  - Remove hardcoded `1920f / 1080f` aspect ratio
  - Calculate aspect ratio dynamically from viewport dimensions
  - Update GeometryPass and PickingPass to set camera viewport dimensions
  - **Impact**: Medium - Correct rendering on all aspect ratios

- [x] **A11.9: Implement Resource Cleanup** - `RenderBatch.kt`, `Renderer2D.kt`:
  - Implement `RenderBatch.destroy()` to delete VAO/VBO
  - Implement `Renderer2D.destroy()` to clean up all batches
  - Add `SkyboxRenderer.destroy()` and `SkyDomeRenderer.destroy()`
  - Add `FrameBuffer.destroy()` and `PickingTexture.destroy()`
  - Update `Renderer.destroy()` to call all cleanup methods
  - **Impact**: Low - Memory leak prevention

- [x] **A11.10: Centralize Entity ID Encoding** - New `EntityIdEncoder.kt`:
  - Extract +1/-1 encoding logic to single location
  - Document why 0 is reserved
  - Use Float throughout to match GPU uniform types (no casting/rounding)
  - **Impact**: Low - Code clarity and consistency

---

## 🔴 Phase A12: Renderer Architecture Refactoring (SRP & lateinit elimination)

### Problem Statement

The `Renderer` class has too many responsibilities (shader loading, framebuffer creation, renderer instantiation, render
pass orchestration) and uses 10+ `lateinit` vars, violating the Single Responsibility Principle and creating unclear
initialization order.

### A12.1: Create RenderResources Data Classes

- [x] **A12.1.1: Create `RenderResources.kt`** - New file with data classes:
  ```kotlin
  data class Shaders(
      val default: Shader, val debug: Shader, val batch: Shader,
      val picking: Shader, val picking3D: Shader,
      val skybox: Shader, val skyDome: Shader
  )
  
  data class Renderers(
      val skybox: SkyboxRenderer,
      val skyDome: SkyDomeRenderer,
      val model: ModelRenderer
  )
  
  data class RenderPasses(
      val picking: PickingPass,
      val geometry: GeometryPass,
      val debug: DebugPass
  )
  
  data class RenderResources(
      val shaders: Shaders,
      val frameBuffer: FrameBuffer,
      val pickingTexture: PickingTexture,
      val renderers: Renderers,
      val renderPasses: RenderPasses
  )
  ```

### A12.2: Create RenderResourcesFactory

- [x] **A12.2.1: Create `RenderResourcesFactory.kt`** - Factory for all render resources:
  - `suspend fun create(width: Int, height: Int): RenderResources`
  - Handles shader loading via ResourceManager
  - Creates FrameBuffer and PickingTexture
  - Instantiates all renderer classes
  - Creates all render passes with proper dependencies
  - **Impact**: High - Centralized initialization logic

### A12.3: Refactor Renderer to Orchestrator Only

- [x] **A12.3.1-A12.3.6: Complete Renderer Refactoring**:
  - Remove all `lateinit` vars → use `renderResources`
  - Remove initialization methods (`initFrameBuffer`, `loadShaders`, `initializeRenderPasses`)
  - Simplify constructor to accept only `RenderResourcesFactory`
  - Add `suspend fun initialize()` for lazy initialization
  - Simplify `render()` method to only orchestrate passes
  - Add `resize()` method for window dimension handling
  - Update `destroy()` to use `renderResources`
  - Add `frameBuffer` property for editor integration

### A12.4: Update Koin DI Module

- [x] **A12.4.1: Update `KoinModule.kt`**:
  - Add `RenderResourcesFactory` as a singleton
  - Update `Renderer` constructor to use factory
  - Remove direct `DebugRenderer` and `PickingRenderer` singletons (created by factory)

### A12.5: Update Engine Initialization

- [x] **A12.5.1: Update `BootManager.kt`**:
  - Call `renderer.initialize()` during initialization
  - Handle initialization errors gracefully

### A12.6: Update Window Management

- [x] **A12.6.1: Update `Window.kt`**:
  - Track window dimensions locally (`windowWidth`, `windowHeight`)
  - Call `renderer.resize()` on window resize
  - Remove direct access to renderer dimensions

---

## 📝 Technical Debt & Follow-up Tasks

### Rendering

- [x] **Implement `FrameBuffer.resize()` and `PickingTexture.resize()`** - `Renderer.kt`:
  - Implemented `FrameBuffer.resize()` to recreate framebuffer textures when window resizes
  - `PickingTexture.resize()` was already implemented
  - Updated `Renderer.resize()` to call both methods
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/FrameBuffer.kt`

- [x] **Pass `VAOLoader` to `RenderResourcesFactory`** - `RenderResourcesFactory.kt`:
  - Currently factory creates `VAOLoader` internally via `createVaoLoader()`
  - Should be injected via constructor for better DI, it is already declared as a singleton in KoinModule.kt
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/RenderResourcesFactory.kt:139`

---

## 🔴 Phase A13: Render Pipeline Review Follow-up

### Documentation

- [x] **A13.1: Add KDoc to VAOLoader** - `VAOLoader.kt`:
  - Document all public methods (`loadToVAO`, etc.)
  - Explain vertex attribute layout and enabled attributes bitmask
  - Document memory management (VAO/VBO/IBO lifecycle)
  - **Impact**: Medium - Code clarity for asset loading pipeline

### Performance Optimizations

- [x] **A13.2: Cache Camera Matrices** - `Camera.kt`:
  - Reuse `Matrix4f` instances instead of allocating new ones each frame
  - Add `private val projectionMatrix = Matrix4f()` and `private val viewMatrix = Matrix4f()`
  - Update `createProjectionMatrix()` and `createViewMatrix()` to reuse instances
  - **Impact**: High - Reduces GC pressure (matrices created every frame for each shader)
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/Camera.kt`

- [x] **A13.3: Object Pool for DebugRenderer** - `DebugRenderer.kt`:
  - Implement object pool for `Line3D` and `Triangle3D` objects
  - Reuse objects instead of allocating new ones each frame
  - **Impact**: Medium - Reduces GC pressure from debug visualization
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/renderer/DebugRenderer.kt`
  - **Bug Fix**: Fixed physics debug rendering - `DebugRenderer` singleton is now properly
    shared between `BulletPhysics3D` (adds debug lines) and `DebugPass` (renders them).
    The A12 refactoring accidentally created separate instances, breaking debug visualization.

### Code Quality

- [x] **A13.4: Document Coordinate Systems** - `PickingTexture.kt`, `Renderer.kt`:
  - Add KDoc explaining screen space vs texture space coordinate systems
  - Document Y-coordinate inversion logic for picking
  - Consider adding `isInverted` parameter to `PickingTexture.readPixel()`
  - **Impact**: Low - Code clarity for coordinate transformations
  - **Done**: Added comprehensive KDoc to `PickingTexture` class and `readPixel()` methods
    in both `PickingTexture` and `Renderer`. Documentation includes coordinate system
    diagrams, usage examples, and clear explanation of Y-axis inversion requirement.

- [ ] **A13.5: Improve Renderer2D Batch Search** - `Renderer2D.kt`:
  - Consider using `PriorityQueue` or maintaining sorted batch list
  - Current O(n) batch search could be optimized
  - **Impact**: Low - Performance for large sprite counts
  - Location: `src/main/kotlin/com/pafoid/skate/engine/render/renderer/Renderer2D.kt:26`

### Testing

- [ ] **A13.6: Add Unit Tests for Render Pipeline**:
  - Test `EntityIdEncoder.encode/decode` round-trip
  - Test `Camera.createProjectionMatrix()` with various aspect ratios
  - Test `GLStateTracker` state change detection
  - **Impact**: Medium - Prevent regressions in core rendering logic