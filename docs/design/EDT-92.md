# EDT-92 — Terrain inspector and tools (REN-28 task 10): design note

Status: **approved** (human, 2026-11-17; answers in §7). Owner: skate-vulkan under the 2026-11-17 rule (domain agents write the
editor UI for their back end), reviewed by skate-editor.

Builds on REN-36 (`TerrainComponent`, `TerrainRuntime.markEdited`, `TerrainChunkData.setHeight`, stamps, biomes),
REN-42 (`seedName`, `spacingCapped`) and AST-36/38 (`TerrainAssetStore.create`, `TerrainAssets.save`).

## 1. Where the UI lives

- **Inspector** (`editor/imgui/components/Terrain.kt`, dispatched from `Component.kt` like `ParticleEmitter`), with
  collapsing sections, each a stable-ID header:
  1. *Terrain*: size, spacing (with a note when `spacingCapped()`), height scale, chunk quads, source, streaming,
     cast shadows, collider; **Preset** combo + Apply (`applyPreset`), **Reroll** (`seedOffset++`), seed name.
  2. *Asset*: the asset reference, or **Create terrain asset** (`TerrainAssetStore.create(<project>/Assets/Data,
     <entity name>, terrain)`).
  3. *Layers* (≤ 8: material, tiling, triplanar), *Biomes* (per biome: name, colour, layer rules, scatter rules),
     *Climate*, *Stamps* (list with add / remove / select).
  4. *Tools*: Sculpt / Paint biome / Stamp tool buttons, brush settings, **Show biomes** toggle (`TerrainDebug.showBiomes`).
- **Viewport tool**: a new `TerrainBrushTool` gizmo (`editor/gizmos/`, same base as `MeasureTool`), active while a
  terrain tool is chosen in the inspector *and* the terrain's entity is selected. Choosing Select/Move/Rotate/Scale
  or deselecting ends it. No new toolbar button and no new shortcut (proposal: keep the toolbar unchanged for now).

## 2. Brushes

- **Hit test**: mouse ray marched against `runtime.heightField.heightAt` (step = spacing, then bisection), so no
  physics dependency and it works in Edit mode. Brush circle drawn with `DebugRenderer` lines following the ground.
- **Sculpt modes**: Raise, Lower, Smooth (average of the 3×3 neighbourhood), Flatten (to the height under the cursor
  at stroke start). Settings: radius (m), strength (m/s for raise/lower, 0–1 for smooth/flatten), smooth falloff
  (`(1 − d²/r²)²`). Shift inverts Raise/Lower. `[` / `]` change the radius while the tool is active.
- **Paint biome**: writes `paintedBiomeIds` for samples under the brush (hard edge; the splat blend already
  softens borders), switches `biomeSource` to `PAINTED` on the first stroke (part of that stroke's command).
- **Per frame while dragging**: edit samples of the chunks under the brush (the skirt/border samples of neighbouring
  chunks too, so seams stay closed), set `chunk.dirty`, then `runtime.markEdited()` (recomputes splat, biomes, scatter).
  Cost bounded by brush area; collider refresh follows `markEdited` (PHY-15 path).

## 3. Undo

- **One command per stroke** (mouse down → up): `TerrainStrokeCommand(terrain, before, after)` where `before` /
  `after` are the touched chunks' heights (and painted ids) captured on first touch in the stroke. Undo/redo copy
  them back, mark dirty, `markEdited()`, and save the asset. Memory: only touched chunks (a 64-quad chunk is 4.5k
  floats ≈ 18 KB). The stroke is applied live while dragging; the command is pushed already-executed (an
  `ExecutionTrackedCommand`-style "record" so `execute` on push does not reapply).
- Inspector fields, presets, stamps, layers, biomes, climate: the existing property-edit command path
  (`InspectorEditTracker`, one command per committed edit).

## 4. Stamps and "Flatten terrain under"

- **Stamp tool**: click on the ground adds a stamp (default RECT 10 × 10 m, FLATTEN, at the hit height); the selected
  stamp shows a handle set: drag the centre to move (on the ground), drag edge handles to resize, a ring to rotate
  (yaw). Each drag is one `EditTerrainStampCommand` (old/new `TerrainStamp`, it is an immutable data class).
- **"Flatten terrain under"**: a Hierarchy/viewport context-menu item on any object with a mesh, enabled when a
  terrain exists in the scene. Adds a PREFAB-footprint stamp (or RECT from the world AABB when the object is not a
  prefab instance) at the object's bounds, FLATTEN to its lowest point, falloff 4 m. One undoable command.

## 5. Persistence

- Sculpt and paint need an asset, otherwise edits are lost on the next rebuild or reload. **Proposal**: the first
  stroke on a terrain without an asset creates one automatically (named after the entity, a toast says so, undo of
  that stroke does not delete the asset). Alternative: tools disabled until the user presses *Create terrain asset*.
- `TerrainAssets.save(terrain)` after each stroke (on mouse up, and after undo/redo) and on scene save.
- When the editor creates a `TerrainComponent` (Add Component or this inspector), `seedName = "terrain/<entity name>"`
  (REN-42).

## 6. Tests

Command tests (stroke apply/undo/redo restores heights and painted ids bit-exactly, stamp edits, flatten-under);
pure brush math (falloff, smooth, flatten, seam samples, ray march hit) without a device; headless ImGui render tests
of the inspector sections and the tool panel; string/tooltip guards (EN + FR in `components.properties`).

## 7. Decisions (approved, human, 2026-11-17)

1. **Where tools are chosen**: the inspector *Tools* section only. No toolbar buttons or shortcuts.
2. **Asset on first stroke**: auto-create the terrain asset on the first stroke (toast; undo of the stroke keeps it).
3. **Brush set**: Raise, Lower, Smooth, Flatten, Paint biome now. Noise and Ramp later (EDT-97).
4. **Stamp editing**: viewport handles, plus inspector fields.
5. **"Flatten terrain under"**: in the object context menu.
6. **Scope (changed)**: the biome, layer-rule, scatter-rule and climate inspectors move to a follow-up (EDT-96).
   EDT-92 is sculpt, paint, stamps, asset creation, `seedName` on create and the note when the sample spacing is
   capped. (Assumption: the "pinned-chunk note" in the approval means the REN-42 `spacingCapped()` note; streaming
   eviction and chunk pinning are not implemented yet.) §1 item 3 therefore keeps only *Stamps* in EDT-92.
