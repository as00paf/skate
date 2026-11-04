# REN-16 — SDF branch of `shader_2d_batch.glsl`: review findings and human test steps

## Findings

1. **Bug (fixed): SDF mode collided with entity ids.** The SDF flag travelled in the `entityId` vertex slot as `1.0`, and the shader tested `fEntityId > 0.5`. World sprites write `EntityIdEncoder.encode(id)` (>= 1) into that slot, so every textured world sprite would have been rendered through the SDF path (alpha thresholded at 0.5, RGB ignored). The marker is now `-2.0` (`UiDrawList.SDF_MODE`) and the shader tests `fEntityId < -0.5`.
2. **Bug (fixed): texture slot truncation.** `int(fTexId)` on an interpolated float can yield slot-1 (e.g. 0.9999 -> 0). `fTexId` and `fEntityId` are now `flat` varyings and the slot is rounded with `+ 0.5`.
3. **Batching: OK.** SDF quads share the 2D batch and vertex layout; the mode is per-vertex so glyphs and sprites batch in one draw call as long as they share the texture slots. `UiDrawList` already splits batches by texture and scissor.
4. **Edge quality: OK.** Antialiasing uses `fwidth(alpha) * 0.75` around the 0.5 edge, which stays about one screen pixel wide at any scale (crisp when minified or magnified).
5. **Documented, not changed:** (a) no outline/shadow path: it would need a second threshold plus an outline colour in the vertex format (suggest a future REN task if GUI needs it); (b) indexing `uTextures[]` with a non-constant value is technically undefined in GLSL 3.30 but works on all supported desktop drivers, and moving to a texture array or per-batch single texture would remove the risk.

## Human test steps (from a new project)

1. Run `.\gradlew.bat run` to start the editor.
2. File > New Project, name `SdfTest`, open it.
3. Add a 2D sprite object (GameObject > 2D > Sprite) with a texture and place it in view. Expected: it renders with its normal colours and texture (before the fix, entity id >= 1 would have made it appear as a flat silhouette).
4. Add several sprites sharing one texture, then one with another texture; all render correctly (checks slot selection and batching).
5. Click a sprite in the viewport; it is selected (picking still works).
6. Enter Play mode / run the game with in-game UI containing text (e.g. a Canvas with a Label). Text appears crisp.
7. Change the label's font size from small to large (e.g. 12, 24, 96). Edges stay smooth and sharp at every size, with no blur or jagged halo.
8. Put the label and a sprite on screen together; both render correctly in the same frame.
9. Run `.\gradlew.bat :test --tests "com.pafoid.skate.engine.render.ui.*"`; all pass.
