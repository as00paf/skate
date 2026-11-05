# REN-12 test steps: RHI migration

Design: `docs/design/REN-12.md`. Each step must leave the engine looking and running exactly as before.

## Steps 0-1: ratchet guard, RHI skeleton, OpenGlDevice

Steps 0 and 1 add `RhiGlIsolationGuardTest` and the `engine/render/rhi/` interfaces. They move the GL state, binding,
resource and timer helpers into `rhi/opengl/`, and the window now creates an `OpenGlDevice` instead of calling
`GL.createCapabilities()` itself.

1. `.\gradlew.bat run`, then create a NEW project → the editor opens, and the viewport shows MainScene as before
   (grid, skater, shadows, HUD).
2. Open `FeatureShowcase` and fly to `Materials` and `Fog` → transparent cube, emissive glow (bloom) and sky look as
   before. The cube map sky has no seams (seamless cube maps are still enabled).
3. Press Play, then Stop → the scene animates and resets as before.
4. Open the Render Graph window → every pass shows CPU and GPU times (the GPU timer queries still work).
5. Export the project (File > Export Project, then Export) and run the exported game → it opens fullscreen and renders the scene.
