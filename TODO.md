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