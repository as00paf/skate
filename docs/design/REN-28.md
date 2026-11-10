# REN-28: Terrain and biomes (design)

Status: **proposal, awaiting approval.** Owner: skate-render. Reviewers: skate-physics (§8), skate-assets (§10).
Depends on ECS-14 (seeded RNG, `RandomService`, on `agents/scripting` as `a7e29f2a`). Feeds PHY-15 (heightfield
collider) and REN-29 (vegetation, water), which reuses the scatter and noise parts.

## 1. Goals and non-goals

**Goals**
1. **Large outdoor terrain:** a chunked heightmap with distance LOD, frustum culling and shadows. It should cost about
   as much as the current skatepark scenes.
2. **Splat materials:** up to 8 PBR layers per terrain (asphalt, grass, dirt, rock, sand, snow, …), blended per pixel
   by splat weights. Slopes get triplanar mapping so cliffs don't stretch.
3. **Procedural generation:** a vendored noise library (FastNoiseLite, MIT) and a terrain recipe (layered noise,
   domain warp, ridges, island falloff). It is deterministic from the project seed through ECS-14.
4. **Biomes:** a biome map, either procedural (temperature/moisture) or painted. Each biome drives the material
   layers and the scatter rules (props, rocks, later vegetation in REN-29), with soft borders.
5. **Hand editing:** editor sculpt, paint and scatter tools with brushes and undo. A procedural terrain can be
   *baked* to editable data.
6. **Physics:** a heightfield collider built from the same height samples (PHY-15), so what you see is what you
   collide with.

**Non-goals (for now)**
- Caves and overhangs (voxel terrain).
- Streaming open worlds larger than memory: everything is loaded with the scene. The chunk layout keeps streaming
  possible later.
- Runtime deformation.
- Hydraulic erosion: it is listed as an optional later recipe step (§5.3).
- Water and vegetation rendering: those are REN-29. This note only defines the scatter hooks REN-29 uses.

## 2. Overview

```
 TerrainComponent (entity)                    Assets (§10)
 ├─ size, resolution, heightScale             ├─ <name>.skterrain/heights.r16   (baked or sculpted)
 ├─ source: PROCEDURAL(recipe) | DATA(asset)  ├─ <name>.skterrain/splat0.png, splat1.png
 ├─ layers[≤8] (material refs)                ├─ <name>.skterrain/biomes.png    (painted biome ids)
 ├─ biomes[] (rules) / biomeSource            └─ <name>.skterrain/terrain.json  (recipe, layers, biomes)
 └─ seedName ("terrain/<entity>")
            │
            ▼  TerrainBuilder (CPU, JobSystem, deterministic)
 HeightField (float grid) ──► Splat weights ──► Scatter instances (per chunk)
      │                          │                     │
      ├─► TerrainChunk meshes    ├─► splat textures    └─► InstanceBatches (REN-29 reuses)
      │   (4 LODs + skirts)      │   (RGBA8 ×2)
      ├─► heightfield collider (PHY-15, same samples)
      └─► TerrainPass draws: chunks culled + LOD picked; shadows; terrain.glsl (shared lighting include)
```

Everything derived (meshes, splat textures, scatter) is rebuilt from the height field and the rules. Only the
sources are saved: the recipe, or the sculpted/painted data.

## 3. Data model

### 3.1 `TerrainComponent` (entity component, ECS domain; the fields are this design)

| Field | Default | Notes |
|---|---|---|
| `sizeMetres` | 256 | Square side. The Transform positions the terrain; its rotation is ignored (axis-aligned, as heightfield colliders need). |
| `resolution` | 257 | Height samples per side (2^n + 1), so 1 m spacing by default. Max 4097. |
| `heightScaleMetres` | 64 | A sample of 1.0 maps to this height. |
| `chunkQuads` | 64 | Quads per chunk side, so 4 × 4 chunks by default. |
| `source` | `PROCEDURAL` | `PROCEDURAL` (recipe, nothing big saved) or `DATA` (`terrainAsset` GUID: baked or sculpted). |
| `recipe` | island preset | §5.3. Ignored for `DATA`. |
| `layers` | 4 presets | Up to 8 `TerrainLayer(material GUID, tiling metres, triplanar on/off)`. |
| `biomes` | 3 presets | `Biome` rules (§6). |
| `biomeSource` | `PROCEDURAL` | `PROCEDURAL` (climate noise) or `PAINTED` (`biomes.png` in the asset). |
| `seedName` | `"terrain/<entity name>"` | The stream name for ECS-14 (§5.1). Renaming the entity keeps the old name, so the world doesn't change. |
| `castShadows` / `collider` | true / true | The collider is a separate `HeightfieldCollider3D` (PHY-15) that reads this component. |

### 3.2 Runtime objects (render domain, not serialized)

- **`HeightField`**: `FloatArray(resolution²)` in metres. Bilinear `heightAt(x, z)` and `normalAt(x, z)` (central
  differences). This single sampler is shared by rendering, scatter, gameplay queries and the collider, so they all
  agree.
- **`TerrainChunk`**:
  - an AABB;
  - 4 LOD index sets over one shared vertex grid: step 1, 2, 4, 8 (65², 33², 17², 9² vertices);
  - skirts;
  - the chunk's scatter instances.
- **`SplatMap`**: 2 × RGBA8 textures, `resolution²` texels; 8 weights that sum to 1. Biome weights come from the
  biome map (§6).

## 4. Rendering

### 4.1 Chunks and LOD

- **Geometry:** one static vertex buffer per chunk holding positions, normals and UVs at full resolution. Each LOD
  is an index buffer stepping 2^lod samples. All 4 index buffers are built once, so changing LOD costs nothing.
- **Cracks between LODs** are hidden with **skirts**: a strip along each chunk edge, dropped by
  `max(1 m, 2 × LOD error)`. That's simpler than stitching index variants and works with any neighbour LOD.
  Considered and rejected: CDLOD morphing (needs vertex texture fetch plus morph uniforms), which we could revisit if
  popping shows.
- **LOD selection** uses screen-space error. Each LOD stores its max height error versus full resolution, computed
  at build time. The chosen LOD is the coarsest one whose error projects under `terrainPixelError` (default 2 px)
  at the chunk's nearest point.
  - This is pure math: `TerrainLod.select(chunkAabb, errors, camera, viewportHeight)`, unit-tested.
  - Hysteresis of 0.5 px stops LODs flickering at thresholds.
- **Culling:** frustum culling per chunk AABB. The shadow cascades use the same test with their own frusta and pick
  one LOD coarser.
- **Budget:** at 256 m and 64-quad chunks there are 16 chunks, so at most 16 terrain draws. A 2 km terrain at 1 m
  is 32 × 32 chunks, about 150 visible after culling, each ≤ 8k triangles at LOD 0 and mostly coarser.
  - Target: < 1 ms GPU on the dev machine. Measured with the REN-22 per-pass timers; the terrain reports in
    `EngineStats` (draws, triangles per LOD).

### 4.2 `TerrainPass` and shader

- **Pass:** a new `TerrainPass` runs inside the geometry stage, before meshes, into the same targets (SceneColor and
  SceneDepth). It declares that I/O for the EDT-54 render graph window. Shadows: the shadow pass gets a terrain
  caster callback (LOD + 1, no splat work).
- **Shared lighting include:** `terrain.glsl` shares lighting with `shader_3d_default.glsl`. **Prerequisite step:**
  move the sun, local lights, cascaded/local shadows, IBL (REN-27) and fog out of `shader_3d_default.glsl` into a
  `lighting.glsl` include (the `#include` preprocessor exists: `fog.glsl`). This is a pure refactor with GL/VK pixel
  parity, and it is task 1 in §12.
- **Material sampling:**
  - Layers are **texture arrays** (RHI `TEXTURE_2D_ARRAY`, already used for shadows): one array each for albedo,
    normal and ORM (occlusion/roughness/metallic), 8 layers, 1024² by default.
  - The 8 splat weights are reduced to the **4 strongest per pixel**, so a pixel samples at most 4 layers ×
    3 maps = 12 fetches, or 36 with triplanar.
  - Blending is **height-based** (each layer's albedo alpha holds height), so stones poke through sand instead of a
    flat cross-fade.
  - **Triplanar** only where `slope > layer.triplanarSlope` and the layer enables it; flat ground uses one planar
    UV.
  - **Distance:** far tiling blend (sample at 1× and 1/8× tiling and blend by distance) to hide repetition.
  - Splat and biome textures are sampled with the terrain UV (0..1 over the terrain).
- **Picking and selection:** the picking pass draws terrain chunks at LOD 2 with the entity id, so clicking terrain
  selects it. Sculpt brushes hit-test with CPU raycasts against the `HeightField`, not the picking buffer.
- **Vulkan:** everything goes through the RHI (vertex buffers, texture arrays, the shader through `GlslToVulkan`).
  Parity is a VK task with a terrain showcase view (§12).

## 5. Procedural generation

### 5.1 Seeding (ECS-14)

- **Seed:** a terrain's noise seed is `RandomService.streamSeed(projectSeed, seedName)`, folded to the 32-bit seed
  FastNoiseLite takes (`(s xor (s ushr 32)).toInt()`).
- **No sequential stream:** generation never draws from a sequential `RandomStream`. Noise is sampled at world
  positions, and scatter uses per-chunk seeds: `streamSeed(projectSeed, "$seedName/scatter/$cx,$cz")`.
- **Why:** the result does not depend on generation order, on how many chunks or threads run, or on other systems'
  draws. A chunk regenerates identically on its own (editor rebuilds, future streaming).
- **Seed changes:** changing the project seed (Project Settings > Random) changes every procedural terrain; a
  per-terrain *seed offset* field lets a designer reroll one terrain alone. In Play the runtime seed override
  (`-Dskate.seed`, `game.settings.json`) applies too, which the human wants for "same world for everyone".

### 5.2 Noise library

- **FastNoiseLite** (Jordan Peck, MIT): one Java file, vendored as `engine/procedural/noise/FastNoiseLite.java` with
  its licence header, plus a new `THIRD_PARTY_NOTICES.md` at the repo root (none exists yet; task 2 adds it).
- **Why it:** OpenSimplex2(S), Perlin, Value, Cellular, fBm/ridged/ping-pong fractals and domain warp in one file
  with no dependencies, and it's deterministic.
- **Placement:** it lives in a neutral `engine/procedural` package, not in render, because REN-29 vegetation,
  gameplay and scripts will use it too.
- **Wrapper:** `NoiseField` (Kotlin, no allocation per sample) exposes it as `sample(x, z): Float` from a serializable
  `NoiseSettings`. Scripts get `ctx.noise(settings)` later (SCR follow-up).

### 5.3 Terrain recipe (`TerrainRecipe`, serialized in the component)

An ordered list of steps over the height field, evaluated per sample at world (x, z) and parallel by rows on
`JobSystem`.

1. `Noise(settings, amplitude, offset)`: adds a noise layer (fBm hills, ridged mountains, cellular plateaus).
2. `DomainWarp(settings, strength)`: warps the input coordinates of the following steps.
3. `Falloff(shape = ISLAND | NONE, inner, outer)`: lowers the edges, so a terrain becomes an island.
4. `Terrace(steps, sharpness)`, `Curve(points)`, `Clamp(min, max)`: shaping.
5. Later: `Erosion(iterations)` (droplet hydraulic erosion, CPU), seeded from the terrain's stream. Not in the first
   version.

Presets: *Flat*, *Rolling hills*, *Island*, *Mountains*, *Skatepark bowl* (a flat area with a bowl, for our game).
**Bake to data** evaluates the recipe into `heights.r16` and switches `source` to `DATA` so sculpting can start. The
recipe is kept in the asset, so it can be re-run with a new seed.

## 6. Biomes

- **Biome:** a name, a debug colour, layer rules and scatter rules.
  - **Layer rules:** `TerrainLayer` index → weight curve by height (m) and slope (deg); for example, rock above 35°,
    snow above 60 m.
  - **Scatter rules:** §7.
- **Biome map:**
  - **Procedural:** two low-frequency noise fields, *temperature* and *moisture* (seeded `"$seedName/climate"`),
    plus a height lapse (colder higher up). The biome is a 2D Whittaker-style lookup table with a configurable grid
    of biome ids.
  - **Painted:** `biomes.png` (R8 biome id per sample), edited with the biome brush.
- **Soft borders:** at each sample, take the 4 nearest biomes in climate space (or the painted ids within a blur
  radius), weighted by inverse distance, then sharpened (`borderSharpness`).
  - Material layer weights = Σ biome weight × biome layer weights, normalized, then reduced to the splat maps.
  - Scatter uses the dominant biome with probability = its weight, which gives natural dithering at borders.
- Everything here is pure math and unit-tested: lookup, border weights, layer weight normalization and the
  `TerrainWeights` reduction to 4 per pixel.

## 7. Scatter (props now, vegetation in REN-29)

- **Rule:**
  - what: a prefab GUID, or a mesh + material for instanced-only items;
  - where: density per 100 m², height range, slope range, minimum spacing;
  - jitter: scale range, yaw jitter, align to normal (0..1);
  - collider on/off.
- **Placement:**
  - Per chunk, Poisson-disk-like: a jittered grid at `minSpacing` with rejection by rule, seeded per chunk (§5.1).
  - Deterministic, and independent of other chunks and rules: each rule has its own sub-seed
    `"$seedName/scatter/$rule/$cx,$cz"`.
- **Rendering:** items without behaviour are drawn as **instance batches** (mesh × material per chunk; the RHI
  already supports instanced draws, as particles use them), culled per chunk with LOD by distance (drop rate
  beyond `fadeStart`). Items whose prefab has components (scripts, lights) are spawned as entities under the terrain,
  capped per rule; they don't show in the hierarchy unless the user unpacks them.
- **Colliders:** static colliders are added for rules with `collider = true`, from the prefab's collider. That covers
  rocks; grass never collides.
- **Editor:** a scatter paint brush adds/erases a per-rule density mask (R8 per rule, in the asset), plus manual
  placement overrides (exclude areas, e.g. the skatepark).

## 8. Physics: heightfield collider (PHY-15, needs skate-physics review)

- **Data:** the collider uses `HeightField` samples directly, at full resolution and never LOD, so the character and
  the board touch exactly the rendered LOD-0 surface (the visible LOD error stays under 2 px).
- **New shape:** a proposed `ColliderShape.Heightfield(samplesPerSide, spacing, heights: FloatArray, minY, maxY)`.
  - Bullet backend: libbulletjme `HeightfieldCollisionShape` (float heights, Y-up). A large terrain may be split into
    one shape per chunk, so edits rebuild only changed chunks.
  - `SimplePhysics3D` backend: bilinear raycast and sphere/box contact against the height grid (it has no general
    mesh shapes today).
- **Edits:** sculpting marks chunks dirty, and their collider is rebuilt at brush release, not every stroke frame.
- **Queries:** `PhysicsQueries` raycasts hit terrain like any collider (layer `Terrain` for filtering). Gameplay that
  only needs height can call `TerrainQueries.heightAt(x, z)` without physics.
- **Surfaces:** the dominant layer at a contact point is exposed as a surface id (`TerrainQueries.layerAt`), for
  footstep/wheel sounds (AUD) and board friction (GPL).

## 9. Editor tools (editor domain, EDT tasks)

- **Terrain tool panel**, active when a terrain is selected, with modes:
  - **Sculpt:** raise/lower, smooth, flatten (to the height under the click), set height, noise, terrace.
  - **Paint layer:** pick 1 of 8.
  - **Paint biome** (painted source only).
  - **Scatter:** paint or erase density per rule.
- **Brush:** radius (m), strength, falloff curve, spacing. Shown as a projected circle on the terrain through the
  debug ground lines (REN-25). Raycast against `HeightField` (§4.2).
- **Undo:** each stroke is one command storing the changed rectangle before and after (heights, splat or mask),
  compressed in memory and coalesced per stroke. That fits `UndoRedoManager` without per-sample commands.
- **Performance:** while a stroke runs, only touched chunks re-upload. Height, normal and splat updates are partial
  buffer and texture updates (RHI `BufferUpdate.DYNAMIC`, texture upload by level).
- **Procedural terrains:** recipe parameters are editable live; rebuilds happen on worker threads, and chunks swap in
  when ready. *Bake to data* is a command.

## 10. Assets and persistence (needs skate-assets review)

- **The `.skterrain` asset is a folder:**
  - `terrain.json`: recipe, layers, biomes and scatter rules, versioned, saved with the current scene schema rules;
    no migrations (rule 8).
  - `heights.r16`: raw little-endian uint16, `resolution²`.
  - `splat0.png` and `splat1.png`: RGBA8 weights.
  - `biomes.png`: R8 ids.
  - `scatter_<rule>.png`: R8 density masks.
- **References:** the folder has a GUID `.meta` like other assets (AST GUIDs), and the component references it by
  GUID.
- **Size:** about 0.5 MB for 257², 32 MB for 4097², so it isn't stored in the scene JSON.
- **Procedural terrains save no data,** only the recipe in the component. The data asset exists after bake or first
  sculpt.
- **Hot reload:** changing `terrain.json` or any map rebuilds the terrain (AST hot reload hook).
- **Export and packing:** the pack includes the folder. The `ReferenceCollector` follows the layer material GUIDs and
  the scatter prefab GUIDs.
- **Texture arrays:** the 8 layer materials' textures are resampled into the arrays at load, so materials stay normal
  `.skmat` assets.

## 11. Tests and showcase

- **Unit tests (no GL):**
  - Noise determinism per seed and stream name; changing another stream changes nothing.
  - Recipe steps.
  - `HeightField` sampling and normals.
  - Chunk border continuity: adjacent chunks share edge heights.
  - LOD error and selection, including hysteresis.
  - Skirt geometry.
  - Splat normalization and top-4 reduction.
  - Biome lookup and border weights.
  - Scatter determinism, spacing and rule filters.
  - Undo rectangle diff.
  - Asset round-trip.
  - Heightfield shape build (physics, with the Bullet test harness).
- **GPU tests:** terrain renders with no cracks between LODs (skirt check from grazing views), the showcase terrain
  view in GL/VK parity, and a timing budget logged.
- **Feature Showcase:** a new `Terrain` area. It holds a 96 m procedural island with 3 biomes (beach sand, grass with
  rocks, rocky peak) next to the existing areas, behind the back row. It also has a "Reroll seed" button that uses
  the ECS-14 runtime reseed, so the human can watch determinism (same seed, same island). The default skatepark
  MainScene stays flat.

## 12. Implementation tasks (after approval)

| # | Task | Domain | Deps |
|---|---|---|---|
| 1 | Extract `lighting.glsl` from `shader_3d_default.glsl` (sun, lights, shadows, IBL, fog); pixel parity GL/VK | REN | — |
| 2 | `engine/procedural`: vendored FastNoiseLite + `NoiseField`/`NoiseSettings`, ECS-14 seeding helpers | REN | ECS-14 |
| 3 | `TerrainComponent` + `HeightField` + `TerrainRecipe` + builder (JobSystem) | ECS (component) + REN | 2 |
| 4 | Chunk meshes, LOD selection, skirts, culling, `TerrainPass`, shadows, picking | REN | 1, 3 |
| 5 | Splat materials: texture arrays, `terrain.glsl`, height blend, triplanar, far tiling | REN | 4 |
| 6 | Biomes: climate noise, lookup, border weights → splat; biome debug view | REN | 5 |
| 7 | Scatter: rules, per-chunk placement, instance batches, prefab entities, colliders | REN (+ PHY colliders) | 6 |
| 8 | `.skterrain` asset: save/load, GUID meta, hot reload, packing | AST | 3 |
| 9 | Heightfield collider (PHY-15) | PHY | 3 |
| 10 | Editor terrain tools: sculpt, paint, biome, scatter brushes, undo, bake | EDT | 4–8 |
| 11 | Showcase `Terrain` area + reroll button, docs/samples | REN + TLG | 6, 7, 9 |
| 12 | Vulkan parity for terrain (VK) | VK | 5 |

REN-29 (vegetation and water) builds on tasks 2 and 7 (instance batches, scatter rules).

## 13. Open questions for the human

1. **Default size and resolution:** 256 m at 1 m spacing. Do we need bigger worlds soon (2 km+), which would push
   streaming up the list?
2. **Skatepark integration:** should terrain support **flattened stamps** (prefab footprints that flatten the ground
   under ramps and ledges)? Proposed as a recipe step `Stamp(prefab footprints)` in task 3.
3. **Layer count:** is 8 enough? The texture arrays could go to 16 at more memory cost.
4. **Erosion:** worth a task now for nicer mountains, or later?

## 14. Decisions (recorded from the human, 2026-11-10)

1. **World size:** bigger worlds (2 km+) are wanted soon: **design chunk streaming now** (load/unload around the camera, physics heightfield and scatter per chunk, asset layout). Revise §2–§12 and the task list accordingly.
2. **Skatepark integration:** yes, flattened stamps under prefab footprints (recipe step `Stamp`).
3. **Layer count:** 8 layers.
4. **Erosion:** later, as an optional recipe step.

Status after these answers: **revision needed for streaming**, then approval.

