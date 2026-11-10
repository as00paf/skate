# REN-28: Terrain and biomes (design)

Status: **proposal rev 2, awaiting approval.** Rev 2 records the human's answers to rev 1 §13 (2026-11-10, §14) and
adds **chunk streaming** for 2 km+ worlds (§15). Owner: skate-render. Reviewers: skate-physics (§8, §15.5),
skate-assets (§10, §15.6), skate-ecs (§3.1, §15.3), skate-gameui (§15.7 loading screen); skate-online later (§15.8).
Depends on ECS-14 (seeded RNG, `RandomService`, `engine/ecs/random/RandomService.kt` on `integration/30g`). Feeds
PHY-15 (heightfield collider) and REN-29 (vegetation, water), which reuses the scatter and noise parts.

Code references are to `agents/render` unless marked *(30g)* (only on `integration/30g` so far). **New** marks files
that don't exist yet. Paths without a root are relative to `src/main/kotlin/com/pafoid/skate/`.

## 1. Goals and non-goals

**Goals**
1. **Large outdoor terrain:** a chunked heightmap with distance LOD, frustum culling and shadows. A park-sized terrain
   should cost about as much as the current skatepark scenes.
2. **Big worlds (human, 2026-11-10):** terrains of 2 km and more, up to 16 km × 16 km. Chunks are **streamed** around
   the camera and the players (render data, splat, scatter, physics), within a memory budget and without frame hitches
   (§15).
3. **Splat materials:** up to 8 PBR layers per terrain (asphalt, grass, dirt, rock, sand, snow, …), blended per pixel
   by splat weights. Slopes get triplanar mapping so cliffs don't stretch. 8 layers is the human's decision (§14).
4. **Procedural generation:** a vendored noise library (FastNoiseLite, MIT) and a terrain recipe (layered noise,
   domain warp, ridges, island falloff, **flattened stamps**). It is deterministic from the project seed through
   ECS-14.
5. **Biomes:** a biome map, either procedural (temperature/moisture) or painted. Each biome drives the material
   layers and the scatter rules (props, rocks, later vegetation in REN-29), with soft borders.
6. **Hand editing:** editor sculpt, paint, scatter and stamp tools with brushes and undo. Edits on a procedural terrain
   are stored only for the edited chunks (§15.2).
7. **Physics:** a heightfield collider built from the same height samples (PHY-15), one shape per chunk and streamed,
   so what you see is what you collide with.

**Non-goals (for now)**
- Caves and overhangs (voxel terrain).
- Worlds beyond 16 km × 16 km, and floating-origin rebasing. Positions stay within ±8 km, where a `Float` keeps
  ≤ 1 mm precision.
- Streaming hand-placed scene entities: only terrain-derived content streams. Open question 1.
- Runtime deformation.
- Hydraulic erosion: **later** (human, §14). It stays listed as a future recipe step (§5.3).
- Water and vegetation rendering: those are REN-29. This note only defines the scatter hooks REN-29 uses.

## 2. Overview

```
 TerrainComponent (entity)                   Assets (§10): <name>.skterrain/
 ├─ size, spacing, heightScale, chunkQuads   ├─ terrain.json   (recipe, stamps, layers, biomes, rules)
 ├─ source: PROCEDURAL | DATA                ├─ overview.r16   (whole world, 8 m spacing; always resident)
 ├─ layers[≤8], biomes[], stamps[]           ├─ macro.png      (far colour, 32 m/texel)
 ├─ streaming (mode, radii)                  └─ regions/r_<rx>_<rz>.bin  (16×16 chunks each; only for data or
 └─ seedName ("terrain/<entity>")                                         edited chunks: heights, splat, biome, masks)
            │
            ▼  TerrainStreamer (§15) ◄── focuses: active camera, StreamingFocus components (player, net players)
 per chunk, on workers: read region record OR evaluate recipe+stamps ─► TerrainChunkData (heights + apron, splat, biome)
            │                                                     ├─► mesh stream, scatter instances (REN-29 reuses)
            │                                                     └─► heightfield shape (PHY-15)
            ▼  main thread, time-budgeted (StreamingUploadQueue)
 pooled GPU buffers + splat-atlas layers ─► TerrainPass: detail chunks (near) + far-field quadtree tiles from overview
                                         ─► physics: add/remove chunk bodies per fixed step (budgeted)
```

Everything derived (meshes, splat textures, scatter, colliders, overview of a procedural terrain) is rebuilt from the
sources: the recipe and stamps, plus the sculpted or painted data of edited chunks.

## 3. Data model

### 3.1 `TerrainComponent` (entity component, ECS domain; the fields are this design)

| Field | Default | Notes |
|---|---|---|
| `sizeMetres` | 256 | Square side, up to 16384. The Transform positions the terrain; its rotation is ignored (axis-aligned, as heightfield colliders need). |
| `sampleSpacing` | 1 m | Metres between height samples (0.5, 1, 2 or 4). Replaces rev 1's `resolution`. Samples per side = `sizeMetres / sampleSpacing + 1`. |
| `heightScaleMetres` | 64 | A sample of 1.0 maps to this height. |
| `chunkQuads` | 64 | Quads per chunk side (fixed power of two). It is also the streaming unit (§15). |
| `source` | `PROCEDURAL` | `PROCEDURAL`: the recipe; edited chunks override it (sparse, §15.2). `DATA`: every chunk is stored (imported heightmaps, or a full bake). |
| `recipe` | island preset | §5.3. Ignored for `DATA`. |
| `stamps` | empty | `TerrainStamp` list (§5.4), human-approved. |
| `layers` | 4 presets | Up to 8 `TerrainLayer(material GUID, tiling metres, triplanar on/off)`. |
| `biomes` | 3 presets | `Biome` rules (§6). |
| `biomeSource` | `PROCEDURAL` | `PROCEDURAL` (climate noise) or `PAINTED` (biome ids in the chunk records). |
| `streaming` | `AUTO` | `OFF` (all chunks resident, as rev 1), `ON`, or `AUTO` (on when the terrain has more than 256 chunks, i.e. more than about 1 km at 1 m). Radii and budgets in §15.4. |
| `seedName` | `"terrain/<entity name>"` | The stream name for ECS-14 (§5.1). Renaming the entity keeps the old name, so the world doesn't change. |
| `castShadows` / `collider` | true / true | The collider is a separate `HeightfieldCollider3D` (PHY-15) that reads this component. |

### 3.2 Runtime objects (render domain, not serialized)

- **`TerrainChunkData`** (**new**, `engine/render/terrain/TerrainChunkData.kt`): one chunk's CPU data.
  - heights: `(chunkQuads + 3)²` floats, which includes a 1-sample apron so edge normals match the neighbours;
  - splat: 2 × RGBA8, `(chunkQuads + 1)²`;
  - biome ids: R8;
  - scatter masks;
  - a dirty flag.
- **`HeightField`**: the single height sampler, shared by rendering, scatter, gameplay queries and the collider.
  - It is now an interface over the resident chunks, with the always-resident overview as fallback.
  - `heightAt(x, z)` and `normalAt(x, z)`: bilinear sampling and central differences.
  - `isDetailed(x, z)` tells callers whether full-resolution data is loaded.
  - Without streaming, every chunk is resident and it behaves as rev 1.
- **`TerrainOverview`** (**new**): the whole terrain at 8 m spacing (2049² R16 for 16 km, 8 MB; 257² for 2 km). It is
  always resident on the CPU and the GPU, and feeds the far field (§15.4) and far-away gameplay queries.
- **`TerrainChunk`** (GPU side of a resident chunk):
  - an AABB;
  - a slot in the pooled mesh buffer (§4.1) and a layer in the splat atlas (§4.2);
  - its scatter instances.
- **Splat textures** are per chunk layers of a `SplatAtlas` texture array (§4.2), not whole-terrain textures.

## 4. Rendering

### 4.1 Chunks and LOD

- **Geometry:**
  - All chunks share one **XZ/UV grid vertex buffer**, because they have the same topology.
  - Each chunk adds a per-chunk stream of height plus packed normal (3 floats, 12 B per vertex, about 50 KB per
    chunk).
  - The stream lives in a **pooled** buffer slot (`ChunkBufferPool`, **new**). Loading a chunk is a `RhiBuffer.update`
    into a free slot, not a buffer creation (§15.5 hitch rules).
  - Each LOD is an index buffer stepping 2^lod samples. The 4 index buffers are shared by all chunks and built once,
    so changing LOD costs nothing.
- **Cracks between LODs** are hidden with **skirts**: a strip along each chunk edge, dropped by
  `max(1 m, 2 × LOD error)`. That's simpler than stitching index variants and works with any neighbour LOD, including
  the far-field tiles (§15.4). Considered and rejected: CDLOD morphing (needs vertex texture fetch plus morph
  uniforms), which we could revisit if popping shows.
- **LOD selection** uses screen-space error. Each LOD stores its max height error versus full resolution, computed
  at build time. The chosen LOD is the coarsest one whose error projects under `terrainPixelError` (default 2 px)
  at the chunk's nearest point.
  - This is pure math: `TerrainLod.select(chunkAabb, errors, camera, viewportHeight)`, unit-tested.
  - Hysteresis of 0.5 px stops LODs flickering at thresholds.
- **Culling:** frustum culling per chunk AABB. The shadow cascades use the same test with their own frusta and pick
  one LOD coarser.
- **Budget:**
  - At 256 m there are 16 chunks, so at most 16 terrain draws.
  - With streaming, detail chunks exist only within `detailRadius` (512 m default, about 200 chunks, about 100 after
    culling). Beyond that, the far-field quadtree adds about 100–300 tiles (§15.4).
  - Target: < 1.5 ms GPU on the dev machine for a 4 km world. Measured with the REN-22 per-pass timers; the terrain
    reports in `EngineStats` (draws, triangles per LOD, resident chunks, upload ms).

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
  - **Splat atlas:**
    - Splat and biome data come from a **`SplatAtlas`** (**new**): two `RGBA8` texture arrays at
      `(chunkQuads + 1)²`, one layer per resident detail chunk. A chunk passes its layer index as a uniform and
      samples with chunk-local UVs.
    - Uploads use `RhiTexture.upload(level, layer, data)` (exists).
    - Capacity is `min(RhiCaps.maxArrayLayers, budget)`. The GL 3.3 minimum is 256, which covers the default
      512 m detail radius; past that the atlas adds a second array.
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
  draws. A chunk regenerates identically on its own, which is exactly what streaming needs (§15): an unloaded
  procedural chunk is regenerated, not stored.
- **Seed changes:** changing the project seed (Project Settings > Random) changes every procedural terrain; a
  per-terrain *seed offset* field lets a designer reroll one terrain alone. In Play the runtime seed override
  (`-Dskate.seed`, `game.settings.json`) applies too, which the human wants for "same world for everyone".
  - Edited chunks (§15.2) keep their stored data across a reroll. The editor warns that rerolling a terrain with edits
    leaves seams at their borders, and offers to discard the edits.

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

An ordered list of steps evaluated **per sample at world (x, z)**, so any chunk can be generated alone, on a worker
(`JobSystem.runAsync`).

1. `Noise(settings, amplitude, offset)`: adds a noise layer (fBm hills, ridged mountains, cellular plateaus).
2. `DomainWarp(settings, strength)`: warps the input coordinates of the following steps.
3. `Falloff(shape = ISLAND | NONE, inner, outer)`: lowers the edges, so a terrain becomes an island.
4. `Terrace(steps, sharpness)`, `Curve(points)`, `Clamp(min, max)`: shaping.
5. `Stamps`: applies the terrain's stamps (§5.4), always last, so ground under a park stays flat whatever the noise
   does.
6. **Later (human, §14):** `Erosion(iterations)`. Droplet erosion is not local: a droplet crosses chunks. With
   streaming it will be an **offline bake** that writes eroded chunks as data, not a per-chunk step.

Presets: *Flat*, *Rolling hills*, *Island*, *Mountains*, *Skatepark bowl* (a flat area with a bowl, for our game).
**Bake to data** evaluates the recipe for every chunk into region files and switches `source` to `DATA`. It is meant
for small terrains: on big worlds, sculpting bakes only the touched chunks (§15.2). The recipe is kept in the asset,
so it can be re-run with a new seed.

### 5.4 Flattened stamps (human: yes, §14)

- **`TerrainStamp`:** footprint, position, yaw, mode, falloff and priority.
  - footprint: `RECT(w, d)`, `CIRCLE(r)`, or `PREFAB(GUID)`, which uses the prefab's collider/mesh XZ bounds;
  - position: target height `y` = the stamp's Y;
  - mode: `FLATTEN` (set to `y`), `RAISE_TO` (only below), `LOWER_TO` (only above);
  - `falloff` metres: a smoothstep blend back to the recipe height outside the footprint;
  - `priority`: higher stamps win where stamps overlap.
- **Evaluation:** per sample, after the other steps. A chunk only evaluates the stamps whose bounds overlap it (a
  uniform grid index built when stamps change), so stamps cost nothing elsewhere.
- **Data chunks:** stamps also apply to `DATA` and edited chunks as a final pass at load, so moving a ramp re-flattens
  ground without re-sculpting. The stored heights stay unstamped.
- **Editor:** a "Flatten terrain under" checkbox on placed prefabs creates and moves a linked `PREFAB` stamp (§9).
- **Scatter:** scatter rules skip stamped areas by default (no trees on the half-pipe).

## 6. Biomes

- **Biome:** a name, a debug colour, layer rules and scatter rules.
  - **Layer rules:** `TerrainLayer` index → weight curve by height (m) and slope (deg); for example, rock above 35°,
    snow above 60 m.
  - **Scatter rules:** §7.
- **Biome map:**
  - **Procedural:** two low-frequency noise fields, *temperature* and *moisture* (seeded `"$seedName/climate"`),
    plus a height lapse (colder higher up). The biome is a 2D Whittaker-style lookup table with a configurable grid
    of biome ids.
  - **Painted:** R8 biome id per sample in the chunk records, edited with the biome brush.
- **Soft borders:** at each sample, take the 4 nearest biomes in climate space (or the painted ids within a blur
  radius), weighted by inverse distance, then sharpened (`borderSharpness`).
  - The blur radius reads the apron. Painted borders wider than the apron (1 sample) are blurred against the
    overview's biome ids.
  - Material layer weights = Σ biome weight × biome layer weights, normalized, then reduced to the splat maps.
  - Scatter uses the dominant biome with probability = its weight, which gives natural dithering at borders.
- **Macro colour map:** the far field (§15.4) uses `macro.png`, an average-layer colour per 32 m, baked from these
  weights.
- Everything here is pure math and unit-tested: lookup, border weights, layer weight normalization and the
  `TerrainWeights` reduction to 4 per pixel.

## 7. Scatter (props now, vegetation in REN-29)

- **Rule:**
  - what: a prefab GUID, or a mesh + material for instanced-only items;
  - where: density per 100 m², height range, slope range, minimum spacing;
  - jitter: scale range, yaw jitter, align to normal (0..1);
  - collider on/off;
  - a `streamRadius` (default: the detail radius; small props can use less).
- **Placement:**
  - Per chunk, Poisson-disk-like: a jittered grid at `minSpacing` with rejection by rule, seeded per chunk (§5.1).
  - Deterministic, and independent of other chunks and rules: each rule has its own sub-seed
    `"$seedName/scatter/$rule/$cx,$cz"`.
  - It runs on workers when a chunk streams in (§15.5).
- **Rendering:** items without behaviour are drawn as **instance batches** (mesh × material per chunk; the RHI
  already supports instanced draws, as particles use them), culled per chunk with LOD by distance (drop rate
  beyond `fadeStart`). Items whose prefab has components (scripts, lights) are spawned as entities under the terrain,
  capped per rule; they don't show in the hierarchy unless the user unpacks them.
  - Streamed entity spawns and despawns are budgeted per frame (§15.5).
  - They are **stateless decoration**: their state is not kept when the chunk unloads (open question 2).
- **Colliders:** static colliders are added for rules with `collider = true`, from the prefab's collider. That covers
  rocks; grass never collides. They stream with the chunk's physics ring (§15.5).
- **Editor:** a scatter paint brush adds/erases a per-rule density mask (R8 per rule, in the chunk records), plus
  manual placement overrides (exclude areas, e.g. the skatepark; stamps exclude by default).

## 8. Physics: heightfield collider (PHY-15, needs skate-physics review)

- **Data:** the collider uses chunk height samples directly, at full resolution and never LOD, so the character and
  the board touch exactly the rendered LOD-0 surface (the visible LOD error stays under 2 px).
- **New shape:** a proposed `ColliderShape.Heightfield(samplesPerSide, spacing, heights: FloatArray, minY, maxY)`
  (`engine/physics3d/ColliderShape.kt`).
  - **One shape and one static body per chunk, always,** created with `IPhysics3D.createRigidBody(shape, mass = 0f)`
    and removed with `removeRigidBody` (both exist). No GameObject per chunk. Edits and streaming then touch only the
    affected chunks.
  - Bullet backend: libbulletjme `HeightfieldCollisionShape` (float heights, Y-up).
  - Jolt backend (PHY-17, now present): `HeightFieldShape`. Its sample-count and block-size constraints are for
    skate-physics to confirm against 65 samples per chunk.
  - `SimplePhysics3D` backend: bilinear raycast and sphere/box contact against the height grid (it has no general
    mesh shapes today).
  - Neighbouring chunk shapes share their edge samples, so bodies cross seams without bumps. A unit test checks this
    with a sphere rolling across a seam.
- **Streaming:** shapes are built on workers and added or removed on the physics step within a budget. Bodies over a
  chunk without a collider are frozen until it arrives (§15.5).
- **Edits:** sculpting marks chunks dirty, and their collider is rebuilt at brush release, not every stroke frame.
- **Queries:** `PhysicsQueries` raycasts hit terrain like any collider (layer `Terrain` for filtering). Gameplay that
  only needs height can call `TerrainQueries.heightAt(x, z)` without physics; it falls back to the overview far away.
- **Surfaces:** the dominant layer at a contact point is exposed as a surface id (`TerrainQueries.layerAt`), for
  footstep/wheel sounds (AUD) and board friction (GPL).

## 9. Editor tools (editor domain, EDT tasks)

- **Terrain tool panel**, active when a terrain is selected, with modes:
  - **Sculpt:** raise/lower, smooth, flatten (to the height under the click), set height, noise, terrace.
  - **Paint layer:** pick 1 of 8.
  - **Paint biome** (painted source only).
  - **Scatter:** paint or erase density per rule.
  - **Stamps:** add, move, resize and delete stamps with a gizmo. Prefabs get a "Flatten terrain under" checkbox
    (§5.4).
- **Brush:** radius (m), strength, falloff curve, spacing. Shown as a projected circle on the terrain through the
  debug ground lines (REN-25). Raycast against `HeightField` (§4.2).
  - Brushes only work where detail data is resident. The editor's detail radius around the editor camera covers that;
    a brush over a chunk that is still loading waits for it.
- **Undo:** each stroke is one command storing the changed rectangle before and after (heights, splat or mask),
  compressed in memory and coalesced per stroke. That fits `UndoRedoManager` without per-sample commands.
  - Undoing on an unloaded chunk loads it first.
  - Chunks referenced by unsaved edits are **pinned** (never evicted) until saved (§15.7).
- **Performance:** while a stroke runs, only touched chunks re-upload. Height, normal and splat updates are partial
  buffer and texture updates (RHI `BufferUpdate.DYNAMIC`, texture upload by level and layer).
- **Procedural terrains:** recipe parameters are editable live; rebuilds happen on worker threads, and chunks swap in
  when ready, nearest first. *Bake to data* is a command (small terrains).
- **Streaming view:** a debug overlay colours chunks by state (§15.3), and the panel shows resident chunks, memory
  used against budget, queue length and upload ms (`EngineStats`).

## 10. Assets and persistence (needs skate-assets review)

- **The `.skterrain` asset is a folder:**
  - `terrain.json`: recipe, stamps, layers, biomes and scatter rules, versioned, saved with the current scene schema
    rules; no migrations (rule 8).
  - `overview.r16`: the whole world at 8 m spacing, uint16 LE.
    - For `DATA` terrains it is the source of truth for the far field.
    - For procedural terrains it is a **cache**: regenerated when the recipe, seed or stamps change, then rewritten
      for edited areas.
  - `macro.png`: far colour, RGB8 at 32 m per texel.
  - `regions/r_<rx>_<rz>.bin`: one file per 16 × 16 chunks (1024 m at 1 m spacing), format in §15.6. A procedural
    terrain only has regions holding **edited** chunks; a `DATA` terrain has all of them.
- **References:** the folder has a GUID `.meta` like other assets (AST GUIDs), and the component references it by
  GUID.
- **Size:** compressed per chunk record.
  - About 0.3 MB for a 256 m data terrain.
  - About 30–60 MB for a fully sculpted 4 km terrain.
  - A procedural 16 km world with a few edited areas is a few MB plus the 8 MB overview.
  - None of it goes in the scene JSON.
- **Procedural terrains without edits save no chunk data,** only the recipe. Region files appear on the first sculpt
  or paint.
- **Hot reload:** changing `terrain.json` rebuilds the resident chunks and the overview. A changed region file reloads
  its resident chunks (AST hot reload hook).
- **Export and packing:** the pack includes the folder. The `ReferenceCollector` (*(30g)*
  `engine/assets/pack/ReferenceCollector.kt`) follows the layer material GUIDs, the scatter prefab GUIDs and
  `PREFAB` stamp GUIDs. Streaming from the exported pack needs random access (§15.6).
- **Texture arrays:** the 8 layer materials' textures are resampled into the arrays at load, so materials stay normal
  `.skmat` assets.

## 11. Tests and showcase

- **Unit tests (no GL):**
  - Noise determinism per seed and stream name; changing another stream changes nothing.
  - Recipe steps.
  - Stamps: flatten, raise, lower, falloff, priority, chunk overlap index.
  - `HeightField` sampling and normals, and the overview fallback.
  - Chunk border continuity: adjacent chunks share edge heights, and apron normals match the neighbour.
  - LOD error and selection, including hysteresis.
  - Skirt geometry.
  - Splat normalization and top-4 reduction.
  - Biome lookup and border weights.
  - Scatter determinism, spacing and rule filters.
  - Undo rectangle diff across chunk borders.
  - Asset round-trip, including the region file index and the compressed record round-trip.
  - Heightfield shape build and the seam roll test (physics, with the Bullet test harness).
  - Streaming (§15.9).
- **GPU tests:** terrain renders with no cracks between LODs or between detail chunks and far tiles (skirt check from
  grazing views), the showcase terrain view in GL/VK parity, and a timing budget logged.
- **Feature Showcase:** a new `Terrain` area. It holds a 96 m procedural island with 3 biomes (beach sand, grass with
  rocks, rocky peak) next to the existing areas, behind the back row, with one stamp under a ramp. It also has a
  "Reroll seed" button that uses the ECS-14 runtime reseed, so the human can watch determinism (same seed, same
  island). The default skatepark MainScene stays flat.
- **Large-world sample** (TLG): a separate sample scene, *Big Island*, with a 4 km procedural island, a flattened
  skatepark plateau, a free-fly camera and a streaming stats overlay. It is what the hitch and budget checks run on
  (§15.9).

## 12. Implementation tasks (after approval)

| # | Task | Domain | Deps |
|---|---|---|---|
| 1 | Extract `lighting.glsl` from `shader_3d_default.glsl` (sun, lights, shadows, IBL, fog); pixel parity GL/VK | REN | — |
| 2 | `engine/procedural`: vendored FastNoiseLite + `NoiseField`/`NoiseSettings`, ECS-14 seeding helpers | REN | ECS-14 |
| 3 | `TerrainComponent` + `TerrainChunkData` + `HeightField` (chunked, apron, overview fallback) + `TerrainRecipe` + per-chunk builder on workers | ECS (component) + REN | 2 |
| 4 | Chunk meshes: shared grid + pooled height streams (`ChunkBufferPool`), LOD selection, skirts, culling, `TerrainPass`, shadows, picking | REN | 1, 3 |
| 5 | Splat materials: layer texture arrays, `SplatAtlas`, `terrain.glsl`, height blend, triplanar, far tiling | REN | 4 |
| 6 | Biomes: climate noise, lookup, border weights → splat; macro colour bake; biome debug view | REN | 5 |
| 7 | Scatter: rules, per-chunk placement, instance batches, prefab entities, colliders (per chunk, streamable) | REN (+ PHY colliders) | 6 |
| 8 | `.skterrain` asset: `terrain.json`, `overview.r16`, `macro.png`, region files (§15.6), GUID meta, hot reload, packing | AST | 3 |
| 9 | Heightfield collider (PHY-15): one shape per chunk on Bullet, Jolt and `SimplePhysics3D`, seam test | PHY | 3 |
| 10 | Editor terrain tools: sculpt, paint, biome, scatter and **stamp** brushes and gizmos, undo, bake; "Flatten terrain under" on prefabs | EDT | 4–8, 13 |
| 11 | Showcase `Terrain` area + reroll button, docs/samples | REN + TLG | 6, 7, 9 |
| 12 | Vulkan parity for terrain, including the far field (VK) | VK | 5, 14 |
| 13 | Stamps: `TerrainStamp`, recipe step, overlap index, apply to data chunks | REN | 3 |
| 14 | Far field: `TerrainOverview`, quadtree tiles from the overview, macro colour shading, detail/far crack hiding | REN | 4, 6 |
| 15 | Streaming core: `ChunkStreamingPlanner` (pure), `TerrainStreamer` state machine, priorities and prediction, memory budgets and LRU, `StreamingUploadQueue` (time-budgeted), `EngineStats` counters, debug overlay | REN | 4, 5, 8 |
| 16 | Streaming focus: `StreamingFocus` component, `TerrainStreamingSystem`, editor-camera and active-camera focus, `prefetch` API | ECS (component, system review) + REN | 15 |
| 17 | Streamed physics: worker shape build, budgeted add/remove per fixed step, freezing bodies over unloaded chunks, prefetch of the physics ring | PHY | 9, 16 |
| 18 | Streamed scatter: per-chunk batches and budgeted entity spawn/despawn on load/unload | REN | 7, 15 |
| 19 | Random-access pack reading: stream entries from `game.bin` through a `FileChannel` instead of `readBytes()` | AST | 8 |
| 20 | Sparse edits: per-chunk bake on first edit of a procedural terrain, dirty-chunk pinning, region save, overview/macro refresh, reroll warning | REN + EDT | 10, 15 |
| 21 | Exported game and Play: spawn prefetch behind the loading screen, quality-scaled radii from the graphics settings | GUI + REN | 16, 17, 19 |
| 22 | *Big Island* large-world sample + streaming stats overlay, hitch and budget measurements | TLG + REN | 14–18, 21 |
| — | Later (human: erosion later): offline erosion bake into data chunks | REN | 20 |

REN-29 (vegetation and water) builds on tasks 2 and 7 (instance batches, scatter rules), and its vegetation cells
stream with task 18. Without streaming (small terrains, `streaming = OFF`), tasks 1–13 stand alone; tasks 14–22 add
big worlds.

## 13. Open questions for the human

Rev 1's four questions are answered (§14). Remaining questions from the streaming design:

1. **Hand-placed entities in big worlds:** should scene entities placed by hand (buildings, ramps, NPCs) also
   load and unload by area (level streaming), or stay loaded with the scene?
   *Recommended:* keep them loaded for now (a few thousand entities are fine in memory), and design entity-cell
   streaming as a separate ECS task when a real level needs it.
2. **Streamed scripted props:** should scatter-spawned entities with behaviour (e.g. a breakable crate) remember their
   state when their chunk unloads?
   *Recommended:* no. They are stateless decoration that respawns fresh; anything with persistent state is
   hand-placed.
3. **Default radii and budgets:** physics 128 m, detail 512 m, far field to 4 km; 256 MB GPU and 256 MB CPU for
   terrain plus scatter at High quality, halved at Low. Are these right for the target machines?
   *Recommended:* yes, tuned with the *Big Island* measurements in task 22.
4. **Unloaded ground under a body:** when a dynamic body reaches a chunk whose collider hasn't loaded (e.g. after a
   fast teleport), should it freeze until the collider arrives, or fall through?
   *Recommended:* freeze (sleep) dynamic bodies; the player is prefetched so it never happens to them.
5. **World size cap:** is 16 km × 16 km enough? Bigger needs floating-origin rebasing across rendering, physics and
   netcode.
   *Recommended:* cap at 16 km for now.
6. **Multiplayer:** the server streams physics around every player's focus, and each client streams rendering around
   its own camera. Agree as the direction for NET when it gets there?
   *Recommended:* yes; skate-online reviews when NET reaches terrain.

## 14. Human decisions (2026-11-10, answers to rev 1 §13)

1. **Default size and resolution / bigger worlds:** bigger worlds (2 km+) are wanted soon, so **chunk streaming is
   designed now**: load and unload chunks around the camera (render meshes and LODs, splat data, scatter batches, the
   physics heightfield per chunk), with a memory budget, hitch-free loading, and how the editor and the exported game
   drive it. Physics parts with skate-physics, the on-disk chunk layout with skate-assets. → §15, tasks 14–22.
2. **Flattened stamps:** yes. → §5.4, task 13, editor in task 10.
3. **Layer count:** 8 layers. → §1 goal 3, §4.2.
4. **Erosion:** later. → §5.3 step 6, the "Later" row in §12.

## 15. Chunk streaming

### 15.1 Rings and what streams

Each terrain keeps three rings around each **focus** (§15.3). Distances are horizontal (XZ) from the focus to the
chunk AABB.

| Ring | Default radius | Resident per chunk |
|---|---|---|
| Physics | 128 m | Heightfield body, scatter colliders, scatter entities with behaviour |
| Detail | 512 m | `TerrainChunkData` (CPU), mesh height stream, splat-atlas layer, scatter instance batches (and REN-29 vegetation cells) |
| Far | `farDistance` 4 km (capped at the terrain size) | Nothing per chunk: the far field draws quadtree tiles from the always-resident `TerrainOverview` and `macro.png` (§15.4) |

- **Ring order:** the physics ring is always inside the detail ring.
- **Unload hysteresis:** a chunk unloads at 1.25 × its ring radius, so a camera wobbling at a border doesn't thrash.
- **Detail-only chunks:** chunks between the physics and detail radii render but have no colliders.
- **Small terrains:** with `streaming = OFF`, every chunk is in every ring, as rev 1.

### 15.2 Chunk sources

A chunk is loaded from the first source that has it:
1. **Region record** in `regions/` (`DATA` terrains, or edited chunks of a procedural terrain);
2. **The recipe** (procedural): generated on a worker, deterministic from the per-chunk seed (§5.1), so nothing is
   stored.

Stamps are applied after either source (§5.4).

**Sparse edits:** the first sculpt or paint on a procedural chunk writes that chunk's generated data as a region record
(a per-chunk bake). From then on it loads as data. A 16 km procedural world with a hand-made town stores only the
town's chunks.

### 15.3 State machine, focus and who drives it

**Chunk states** (`TerrainStreamer`, **new**: `engine/render/terrain/streaming/TerrainStreamer.kt`):

`UNLOADED → QUEUED → LOADING (IO/generate, worker) → BUILT (CPU data, mesh stream, scatter instances, shape ready) →
UPLOADING (main thread, budgeted) → RESIDENT → (EVICTING) → UNLOADED`

- **Cancellation:** a chunk that leaves its ring while `QUEUED` or `LOADING` is cancelled through its coroutine `Job`.
- **Physics:** the physics add is a separate sub-state, so a chunk can be rendered before its collider is in.
- **Cache:** `BUILT` data of chunks just evicted stays in a CPU **LRU cache** (budget §15.4), so turning around
  doesn't reload from disk.

**Planner** (`ChunkStreamingPlanner`, **new**, pure: `engine/render/terrain/streaming/ChunkStreamingPlanner.kt`):
given the focuses, rings, resident set, pinned set and budgets, it returns load requests (with priorities) and
evictions. It runs every frame without allocation (reused arrays).
- **Priority:**
  - ring (physics first);
  - then distance to the *predicted* focus position, `position + velocity × lookahead` (1.5 s);
  - then a bonus inside the camera frustum.
  - A skater at 15 m/s requests chunks about 22 m ahead of the plain distance order.
- **Eviction:** the farthest outside the unload radius first. Chunks are never evicted when pinned (unsaved edits,
  §15.7), or when inside any focus's ring.

**Focus sources:**
- **Implicit:** the active render camera (`CameraManager`), for the detail and far rings only.
- **`StreamingFocus`** (**new**, ECS component `engine/ecs/components/StreamingFocus.kt`):
  - fields `physics: Boolean` (default true), `radiusScale` (1.0) and `priority` (0);
  - put on the player prefab (GPL), on spawned network players (NET), and on any object that must have ground under
    it (a vehicle driving away).

**Driver:** `TerrainStreamingSystem` (**new**, `engine/ecs/systems/TerrainStreamingSystem.kt`, ECS reviews).
- `update`: gathers the focuses, runs the planner for each terrain, and launches loads on `JobSystem`
  (`engine/core/JobSystem.kt`). Disk reads go through `runIO`, generation, decode, mesh and scatter building through
  `runAsync`.
- `fixedUpdate`: applies the budgeted physics adds and removes (§15.5).

**Who drives it, by mode:**

| Mode | Render rings follow | Physics ring follows |
|---|---|---|
| Editor, edit mode | Editor camera | Editor camera, only when physics debug draw or a scatter-collider preview is on; brushes use CPU heights, not colliders |
| Editor, Play | The game camera and the editor scene camera, if its view is visible | `StreamingFocus` components |
| Exported game | Active camera | `StreamingFocus` components |
| Headless server (future NET) | None | `StreamingFocus` on every player |

**`prefetch(position, rings)`:** returns a `Deferred` that completes when those rings are resident around a point.
Callers:
- scene load in the exported game and Play (task 21, behind the loading screen, at the spawn point);
- teleports and respawns (GPL);
- the editor's "Focus on selected" camera jump, which shows the far field until detail arrives.

### 15.4 Memory budget

Per-chunk costs at 64 quads and 1 m spacing:

| Item | Size per chunk |
|---|---|
| Mesh height+normal stream (GPU) | ~50 KB (12 B × 65² + skirts) |
| Splat atlas layers (GPU) | ~34 KB (2 × RGBA8 × 65²) |
| `TerrainChunkData` (CPU) | ~70 KB (heights with apron, splat, biome, masks) |
| Heightfield shape (physics, native) | ~20 KB |
| Scatter instances (CPU + GPU) | rule-dependent: 64 B per instance (REN-29 grass dominates) |

- **Default detail ring (512 m):** about 200 chunks, so about 17 MB GPU and 14 MB CPU for the terrain itself. Scatter
  and vegetation are the real consumers.
- **Always resident:**
  - **2 km world:** the overview is 257² (0.1 MB) and the macro map 65² RGB, so negligible.
  - **16 km world:** the overview is 2049² × R16 (8 MB CPU and 8 MB GPU) and the macro map 513² RGB8 (0.8 MB).
- **Budgets** (Project Settings, scaled by the overall quality preset from REN-30 `GraphicsSettings`): `gpuBudgetMB`
  (256 High / 128 Low) and `cpuBudgetMB` (256 / 128), covering terrain, the LRU cache and scatter.
  - When over budget, the planner first shrinks the scatter `streamRadius`, then the detail radius (down to 256 m),
    and logs a warning once. The physics ring never shrinks.
- **Pools:**
  - `ChunkBufferPool` and the `SplatAtlas` are pre-sized from the detail radius at startup. Their capacity is the
    hard cap on resident detail chunks.
  - **Steady-state streaming creates no GPU objects**, only updates into free slots.

### 15.5 Hitch-free loading

The rule is that the main thread only does bounded copies.
- **Workers:**
  - file reads;
  - decompression;
  - procedural generation and stamps;
  - normals;
  - mesh streams;
  - splat weights;
  - scatter placement;
  - heightfield shape construction, if the backend allows it off-thread (skate-physics confirms per backend;
    otherwise it is built in `fixedUpdate` under the same budget).
- **`StreamingUploadQueue`** (**new**, `engine/render/terrain/streaming/StreamingUploadQueue.kt`):
  - Drained in `TerrainPass.prepare` with a **time budget** (`uploadBudgetMs`, 1.5 ms) and a byte budget (4 MB per
    frame), measured with `System.nanoTime()`.
  - Each item is one chunk's buffer update and two atlas layer uploads.
  - `JobSystem`'s main-thread queue is capped by task count (256 per `update`), not time, so streaming doesn't use it
    for uploads.
- **Physics adds:** at most `physicsAddsPerStep` (2) adds and removes per fixed step, in `TerrainStreamingSystem.fixedUpdate`.
  - Physics-ring chunks are first in priority and the ring is small (about 16–25 chunks), so the physics ring around
    a 15 m/s skater stays ahead.
  - **Bodies over an unloaded collider:** dynamic bodies whose AABB lies over a physics-ring chunk without a collider
    are put to sleep and woken when it arrives (open question 4). `StreamingFocus` bodies are prefetched.
- **Scatter entities:** spawns and despawns are capped per frame (16) and spread across frames. Instance batches go
  through the upload queue like meshes.
- **No allocation:**
  - decode buffers come from a pool (`MemoryUtil` direct buffers, as `ParticlePass` uses);
  - the planner reuses arrays;
  - chunk records are read into pooled byte buffers.
- **No missing ground:**
  - the far field always draws, so a chunk that hasn't arrived shows the coarser overview surface, never a hole;
  - a detail chunk fades in by swapping the far tile in place, with the skirts hiding the seam.
- **Measured:** `EngineStats` gets `terrainResidentChunks`, `terrainQueued`, `terrainUploadNs`, `terrainGpuBytes` and
  `terrainCpuBytes`. Task 22 asserts the worst frame while flying at 60 m/s over *Big Island* stays under the frame
  budget plus 1 ms (logged, measured manually by the human).

### 15.6 On-disk chunk layout (with skate-assets)

- **Region file** `regions/r_<rx>_<rz>.bin`, 16 × 16 chunks:
  - **Header:** magic `SKTR`, version, `chunkQuads`, `sampleSpacing`, then a 256-entry index of
    `(offset u32, storedSize u32, rawSize u32, flags u16, crc32 u32)`, zero for absent chunks.
  - **Records:** each chunk record is independently deflated, so one chunk loads with one seek and one read.
- **Chunk record:**
  - heights: uint16 `(chunkQuads + 3)²` including the apron;
  - splat: 2 × RGBA8;
  - biome: R8;
  - one R8 mask per scatter rule (only present ones, flagged).
- **Saving (editor):** only dirty chunks. Their region is rewritten to a temp file and atomically renamed, so a crash
  never corrupts a region.
- **Exported games:**
  - Today `GameBin` reads all of `game.bin` into memory (`game/GameBin.kt`:49, `file.readBytes()`), and
    `PackBuilder.extract` takes the whole `ByteArray` *(30g)*. That defeats streaming for big worlds.
  - Task 19 gives `Pack` a `FileChannel`-backed `read(entry, position, length)`.
  - Region entries are stored **uncompressed at the pack level**, because their records are already deflated. A chunk
    then reads straight from the pack at `entry.position + record.offset`.

### 15.7 Editor specifics

- **Pinning:** chunks with unsaved edits are pinned. The planner never evicts them, and their memory counts against
  the budget; past the budget, the panel suggests saving.
- **Save:** saving the scene saves dirty regions, then refreshes `overview.r16` and `macro.png` for the edited areas.
  The refresh is incremental: only overview texels covering dirty chunks.
- **Recipe edits:** changing recipe or stamp parameters on a big procedural world rebuilds resident chunks
  nearest-first and regenerates the overview on workers (about 1 s for 16 km on the dev machine). The far field keeps
  the old overview until the new one is ready.
- **Overview:** the Terrain panel shows a minimap from the overview, with a click to move the editor camera there
  (prefetch).

### 15.8 Precision and networking

- **Precision:** positions within ±8 km keep ≤ 1 mm float precision. Chunk vertices are chunk-local, and the chunk
  origin is a uniform, so no camera-relative rendering is needed at this size (non-goal beyond 16 km).
- **Networking (future, NET review):** the server runs physics rings around every player's `StreamingFocus`. Clients
  run render rings around their camera, plus a physics ring around their own player for prediction. Determinism
  holds because procedural chunks are pure functions of seed and position, and stored chunks ship in the pack.

### 15.9 Streaming tests

- **Unit tests (no GL):**
  - Planner: ring membership, hysteresis, priority order with prediction, pinned chunks never evicted, budget
    shrinking order, no allocation in steady state (allocation counter).
  - State machine: cancellation when leaving the ring, LRU reuse, prefetch completes only when rings are resident.
  - Upload queue: respects the time and byte budgets with a fake clock.
  - Region file: index and record round-trip, crc mismatch, atomic save, absent chunks.
  - Sparse edits: the first edit writes exactly one record; reload after save reproduces it.
  - Generated chunk equals the stored copy of the same chunk.
  - Overview incremental refresh.
  - Physics: budgeted add/remove counts, bodies frozen and woken.
  - Pack random-access read equals `extract`.
- **Manual measurement:** task 22 on *Big Island* (worst frame time, memory against budget).
