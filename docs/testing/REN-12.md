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

## Step 3: textures and samplers

Image textures, cube maps, the UI font atlas and model thumbnails are now created through the RHI device.

1. `.\gradlew.bat run`, then create a NEW project → the skater and board show their textures, and the HUD text is
   sharp with no stray pixels at glyph edges (font atlas).
2. Open the Asset Browser's Prefabs tab → every prefab shows a model thumbnail. Close and reopen the project → the
   thumbnails come back (they are released and rendered again).
3. Open `FeatureShowcase` and fly to `Materials` → the asphalt cube is textured and stays sharp at a grazing angle
   (mipmaps).
4. Replace a texture file used by the scene (same name) while the editor runs → the viewport updates (hot reload
   swaps the texture in place).

## Step 4: render targets, passes, readback and blit

The scene framebuffer, post-processing scratch targets, shadow maps and the picking target are now RHI render
targets. Every pass starts with `beginPass` (target, clear, viewport) and ends with `endPass`. Picking, thumbnails and
screenshots read pixels through the device, and the exported game presents with an RHI blit.

1. `.\gradlew.bat run`, then create a NEW project → MainScene looks as before: sky color, sharp sun shadows, bloom and
   gamma (post-processing), grid and HUD on top.
2. Click the skater, then the ground → each gets selected (picking readback). Resize the viewport panel and click
   again → selection still matches the cursor.
3. Open `FeatureShowcase` and fly to `Lights` → the spot and point lights cast shadows (layered shadow map). Fly to
   `Particles` → the particles blend over the scene.
4. Click the viewport's screenshot button → a PNG appears under `screenshots/` and matches the viewport, right side up.
5. Open the Asset Browser's Prefabs tab → model thumbnails show on a grey background.
6. Export the project and run the exported game → it fills the window, stretched to the window size.

## Step 5: shaders and parameters

Shaders are compiled by the RHI device, and the `Shader` asset sets named parameters that the backend uploads only
when they change.

1. `.\gradlew.bat run`, then create a NEW project → MainScene renders as before (lit skater, shadows, sky, grid).
2. Open `FeatureShowcase` and fly to `Materials` → each material keeps its own color, roughness and emissive glow (no
   values leak between objects). Select the `[Materials] Asset` sphere and drag its base color → only that sphere
   changes.
3. Fly to `Fog` and change the fog density in the Environment component → the fog updates at once.
4. Edit `src/main/resources/shaders/post_gamma.glsl` (e.g. return `vec4(1.0, 0.0, 0.0, 1.0)`) while the editor runs →
   the viewport turns red (hot reload). Introduce a syntax error → the console logs "Fragment compilation failed"
   with the driver's message and the previous shader keeps rendering. Revert the file.

## Step 6: pipelines and draws

Every draw now goes through an RHI pipeline (shader plus depth, blend, cull, offset and line state) and the command
list; the old GL state scopes are gone.

1. `.\gradlew.bat run`, then create a NEW project → MainScene renders as before: shadows without acne, the grid
   (thick lines) and gizmos on top, the HUD with its clipped panels.
2. Open `FeatureShowcase` and fly to `Materials` → the glass cube is transparent from both sides (double-sided blend)
   and opaque cubes hide their back faces. Fly to `Particles` → fire glows (additive) and smoke blends.
3. Press Play, then fly near the spot and point lights → their shadows render; the sky stays behind everything.
4. Click objects, including a sprite → each is selected (picking pipelines). Open the Prefabs tab → thumbnails render.
5. Edit `src/main/resources/shaders/shader_3d_default.glsl` (e.g. output red) while the editor runs → models turn
   red (pipelines rebuilt after the hot reload). Revert it.

## Step 7: timers and cleanup

The Render Graph times passes with RHI timers, and no OpenGL code is left outside the GL backend except the editor's
ImGui renderer.

1. `.\gradlew.bat run`, then create a NEW project → the editor and viewport look as before.
2. Open the Render Graph window → every enabled pass shows a CPU time and, after a few frames, a GPU time; disable
   the post-processing pass → its GPU time shows as unknown, and re-enabling it brings the time back.
3. Press Play in `FeatureShowcase`, then Stop → rendering, picking and thumbnails behave as in step 6.
4. Export the project and run the exported game → it renders the scene full screen.
