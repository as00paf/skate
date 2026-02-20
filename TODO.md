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

- [ ] **A11.4: Implement Proper State Tracking** - New `GLStateTracker.kt`:
  - Track current OpenGL state (blend, depth mask, cull face, etc.)
  - Only call gl functions when state actually changes
  - **Impact**: High - Reduces redundant OpenGL calls

### Architecture Improvements

- [ ] **A11.5: Extract RenderPass Interface**:
  - Create `interface RenderPass { fun execute(scene: Scene, camera: Camera) }`
  - Extract PickingPass, GeometryPass, DebugPass implementations
  - Update Renderer to orchestrate passes instead of implementing them
  - **Impact**: Medium - Better separation of concerns, easier testing

- [ ] **A11.6: Implement Proper Z-Index for 2D Sprites** - `Renderer2D.kt`:
  - Remove hardcoded `zIndex == 0` check
  - Add `zIndex` property to SpriteRenderer
  - Group batches by z-index and render in order
  - **Impact**: Medium - Proper 2D layering

- [ ] **A11.7: Define Texture Slot Constants** - New `TextureSlots.kt`:
  - Extract magic numbers (0, 1, 2, 3, 4) to named constants
  - BASE_COLOR, NORMAL, METALLIC_ROUGHNESS, AO, EMISSIVE
  - **Impact**: Low - Code clarity

### Code Quality

- [ ] **A11.8: Fix Aspect Ratio Handling** - `Camera.kt`:
  - Remove hardcoded `1920f / 1080f` aspect ratio
  - Add `viewportWidth` and `viewportHeight` properties
  - Update dynamically on window resize
  - **Impact**: Medium - Correct rendering on all aspect ratios

- [ ] **A11.9: Implement Resource Cleanup** - `RenderBatch.kt`, `Renderer2D.kt`:
  - Implement `RenderBatch.destroy()` to delete VAO/VBO
  - Implement `Renderer2D.destroy()` to clean up all batches
  - **Impact**: Low - Memory leak prevention

- [ ] **A11.10: Centralize Entity ID Encoding** - New `EntityIdEncoder.kt`:
  - Extract +1/-1 encoding logic to single location
  - Document why 0 is reserved
  - **Impact**: Low - Code clarity