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

## Step 2: buffers and vertex layouts

Mesh, sprite, UI, debug, particle, light and skinning buffers now come from the RHI device. Nothing should look or
behave differently.

1. `.\gradlew.bat run`, then create a NEW project → the skater animates in Play (skinning buffer), the HUD text and
   button render (UI buffers), and the grid and camera/light gizmos draw (debug buffers).
2. Right-click the viewport > Create Empty, add `SPRITE_RENDERER`, and set its position to (0, 1, 0) → a white square
   appears. Click it → it is selected (2D batch and picking buffers).
3. Open `FeatureShowcase`, press Play, and fly to `Particles` → fire, smoke, sparks and fountain render (instance
   buffer). Fly to `Lights` → red, blue and green lights shade the sphere (light buffer).
4. Import a model (drag a `.gltf` into the Project Browser, then into the viewport) → it renders with its textures.
   Delete it, then press Ctrl+Z → it comes back.
