# REN-29: Vegetation and water (design)

Status: **proposal rev 3, awaiting approval.** Rev 1 was rejected by the lead (not grounded in the code). Rev 3
records the human's answers to rev 2 §13 (2026-11-10, §14) and adds the **infinite ocean** (§3.5, §6.7, §8.4),
**foliage on arbitrary meshes** (§4.6), the **swimming direction** (§8.3) and the phase order shared with REN-28 (§12).
Owner: skate-render. Reviewers: skate-physics (§4.6 edit-mode queries, §8, PHY-16), skate-assets (§10), skate-ecs (§3,
§5.3), skate-vulkan (§12), skate-gameplay and skate-animation (§8.3 swim direction).
Depends on REN-27 (IBL: `EnvironmentProbe`) and **builds on REN-28** (`docs/design/REN-28.md`): task 1 (`lighting.glsl`),
task 2 (`engine/procedural`, ECS-14 seeding), task 7 (scatter rules, per-chunk placement, instance batches) and §6
biomes. Feeds PHY-16 (water volumes and buoyancy).

Code references are to `agents/render` at `accee790` unless marked *(30g)*: those files exist only on
`integration/30g` so far (EDT-54 render-graph API, ECS-14 `RandomService`). Files marked **new** don't exist yet. Paths
without a root are relative to `src/main/kotlin/com/pafoid/skate/`.

## 0. What rev 1 recorded, and what changed

Rev 1 recorded **no human decisions**: it was never reviewed. Its requirements are kept, and so is the backlog's
`Do` line (instanced trees/grass with LOD and wind, a water shader with reflection/refraction and foam, water volumes
for buoyancy via PHY-16, a Feature Showcase demo area):

- GPU instancing for dense vegetation, and distance LOD for trees (§4).
- A global wind direction and speed that drives vegetation in the vertex shader. A future weather system can drive it (§5).
- Vegetation density, species and scale come from REN-28 biomes (§4.5).
- The water surface reflects the sky and the surrounding geometry using REN-27 IBL, with refraction (§6).
- A dedicated `WaterPass` after opaque rendering and before post-processing (§6.2).
- Shoreline and intersection foam, texture-driven (§6.4).
- Buoyancy proportional to the submerged volume, plus drag, inside water volumes (§8).
- Swappable vegetation types and water materials (§3, §10).
- A showcase area (§11).

Corrections from the review: wind is **render**-owned vertex animation, not bones (no skate-animation work). The
water volume is a `WaterComponent` read by a physics system, not a new collider type (§8.1). The dependencies now
include REN-28.

## 1. Goals and non-goals

**Goals**
1. **Grass:** large fields of grass and small plants, up to ~200k visible instances, drawn instanced with distance
   density falloff. Target < 2 ms GPU on the dev machine.
2. **Trees and shrubs:** instanced meshes with 2–3 mesh LODs plus an optional impostor. They cast shadows and can have
   colliders (rocks/trunks via REN-28 §7 rules).
3. **Wind:** one wind field (direction, speed, gusts) for grass, trees, water waves (optional) and later particles.
   It is deterministic and freezes on pause.
4. **Placement:** on REN-28 terrain through biome scatter rules, and without terrain (the skatepark, the showcase)
   through a `FoliageComponent` area.
5. **Water:** bounded water bodies (pools, lakes, rivers as boxes, a sea around a REN-28 island) with Gerstner waves,
   refraction, depth absorption, reflections (screen-space plus IBL fallback), foam, sun specular, shadows and fog.
6. **Buoyancy (PHY-16):** dynamic bodies float, bob and settle in water using the same wave heights the renderer
   draws, on Bullet, Jolt and `SimplePhysics3D`.
7. **Infinite ocean (human, §14):** a camera-following sea that reaches the horizon, alongside bounded water. It has
   far-field LOD, a seamless horizon, a shoreline against REN-28 terrain that works with streaming, and buoyancy and
   volume queries for PHY-16 (§6.7, §8.4). It is built last, after terrain and streaming.
8. **Foliage on arbitrary meshes (human, §14):** `FoliageComponent` places plants on any static mesh by raycasting
   down, using physics queries in edit mode (§4.6).

**Non-goals (for now)**
- GPU-driven culling or compute: the renderer is GL 3.3 core (`#version 330 core` in `shader_3d_default.glsl`), with
  no compute or SSBO. Culling stays on the CPU.
- FFT ocean, caustics (human, §14), wakes or ripples from moving objects, waterfalls, spline rivers with banks.
- Planar reflections: later, and only if pools look wrong with SSR + IBL (human, §14).
- Grass reacting to the skater: a later follow-up (human, §14).
- Skeletal or bone-driven trees: wind is vertex-shader only.
- Swimming: a later GPL/ANI direction (human, §14). Nothing in this design may prevent it; the hooks are in §8.3.
- Earth curvature on the ocean horizon: worlds are capped at 16 km (REN-28 §16).

## 2. Overview

```
 EnvironmentComponent (+ wind fields)        FoliageType asset (.skfoliage)          WaterComponent (entity)
 EnvironmentClockSystem (new) ─ time         ├─ lods[] (model GUID, screen size)     ├─ size, depth, waves[≤4]
        │                                    ├─ impostor (optional)                  ├─ material (.skmat, "water")
        ▼                                    └─ wind profile, shadows, collider      └─ density, drag, flow
 WindField (pure, engine/environment)              │                                      │
        │                         ┌────────────────┴───────────┐                          │
        │            REN-28 ScatterRule.foliageType     FoliageComponent (area)            │
        │                         └──────► ScatterPlacer (REN-28 task 7, per cell, seeded) │
        │                                        │                                         │
        │                                 FoliageCell[] (per type: packed instances)        │
        ▼                                        ▼                                         ▼
 wind.glsl ─────────────────────────► VegetationPass ── InstanceBatch ──┐   WaterWaves (pure) ── water_waves.glsl
                                                                        │          │                 │
 render order: Shadow → Picking → Geometry(opaque, sky) → [Terrain] → Vegetation → Water → Transparent → Particles → Post…
                                                                                    │
                                                         BuoyancySystem (PHY-16) ◄──┘ same heights, same clock
```

## 3. Data model

### 3.1 Wind: fields on `EnvironmentComponent` (ECS component, fields are this design)

`engine/ecs/components/EnvironmentComponent.kt` already holds sky, fog, ambient and IBL settings (REN-26/27). Add:

| Field | Default | Notes |
|---|---|---|
| `windDirection` | 45° | Yaw in degrees, the direction the wind blows *towards*. |
| `windSpeed` | 3 m/s | 0 disables wind. Presets in the inspector: calm 0.5, breeze 3, strong 8, storm 16. |
| `windGustStrength` | 0.4 | Gust amplitude relative to speed. |
| `windGustScale` | 20 m | Gust front wavelength. Gusts travel along the wind at `windSpeed`. |
| `windTurbulence` | 0.3 | High-frequency flutter weight (leaves, grass tips). |
| `animateInEditor` | true | Wind and waves move in edit mode (preview). Off freezes them at time 0. |

Plus a non-serialized `runtime.animationTime` (§5.3).

### 3.2 `FoliageType` (asset, `.skfoliage`, **new**: `engine/render/vegetation/FoliageType.kt`)

| Field | Default | Notes |
|---|---|---|
| `kind` | `GRASS` | `GRASS` (built-in clump mesh, no LOD, no shadows) or `MESH` (trees, shrubs, rocks). |
| `grass` | blades 7, height 0.5 m, width 0.06 m, 2 colours | `GRASS` only: parameters of the generated clump mesh (`GrassMeshBuilder`, **new**). Texture optional (alpha-tested blade). |
| `lods` | — | `MESH` only. Up to 3 `FoliageLod(model GUID, minScreenSize)`. LOD *i* is used while the projected bounding-sphere height ≥ `minScreenSize` (fraction of the viewport). |
| `impostor` | none | Optional far LOD (§4.3). Beyond the last LOD and without an impostor, the instance is culled. |
| `cullScreenSize` | 0.01 | Below this, nothing is drawn. |
| `wind` | `GRASS` profile | `NONE`, `GRASS` or `TREE` plus `stiffness` (0..1) and `flutter` (0..1). §5.2. |
| `castShadows` | GRASS false / MESH true | Per type. LODs ≥ 2 never cast. |
| `collider` | none | `MESH` only: `CAPSULE(radius, height)` trunk or `FROM_MODEL` (REN-28 §7 static colliders). Grass never collides. |
| `material` | from the model | Optional `.skmat` override (e.g. a tinted leaf material). Uses the existing PBR schema with `alphaMode = MASK` and `doubleSided`. |
| `tintVariation` | 0.15 | Per-instance hue/brightness jitter (seeded). |

Different vegetation can be swapped by pointing a rule or a layer at another `.skfoliage` (rev 1 acceptance 4).

### 3.3 `FoliageComponent` (entity component, ECS registers; **new**: `engine/ecs/components/FoliageComponent.kt`)

Vegetation without terrain, for the skatepark and the showcase:

| Field | Default | Notes |
|---|---|---|
| `areaSize` | 10 × 10 m | Rectangle on the entity's local XZ. The Transform places it; only yaw is used. |
| `layers` | 1 grass layer | Up to 8 `FoliageLayer(foliageType GUID, densityPer100m2, minSpacing, scaleMin/Max, yawJitter, alignToNormal)`. These are REN-28 §7 scatter rule fields minus height/slope. |
| `placement` | `FLAT` | `FLAT`: at the entity's Y. `TERRAIN`: snaps to the `HeightField` of the terrain below (REN-28 §3.2) and reads its normal. `MESH` (human, §14): raycasts down onto static colliders (§4.6). |
| `areaHeight` | 10 m | `MESH` only: the area is a box reaching this far below the entity; rays run from its top to its bottom. |
| `surfaceLayers` | static layers | `MESH` only: collision layer mask that rays hit. `Character` and dynamic bodies are never hit. |
| `surfaces` | `TOP` | `MESH` only: `TOP` (first hit per ray) or `ALL` (every upward-facing hit, for multi-level structures). |
| `maxSlope` | 45° | `MESH` (and `TERRAIN`): rejects steeper surface normals. |
| `densityMask` | none | Optional painted R8 mask per layer, stored as a sidecar asset (§10). |
| `seedName` | `"foliage/<entity name>"` | ECS-14 stream name; same rules as REN-28 §5.1. |

On terrain, vegetation normally comes from biome scatter rules instead (§4.5). `FoliageComponent` is for local
patches.

### 3.4 `WaterComponent` (entity component, ECS registers; **new**: `engine/ecs/components/WaterComponent.kt`)

| Field | Default | Notes |
|---|---|---|
| `sizeX`, `sizeZ` | 20, 20 m | Surface rectangle centred on the Transform; only yaw is used (pitch/roll ignored, like terrain). |
| `depth` | 2 m | The volume runs from the surface down by `depth`. Used for buoyancy bounds and as the absorption fallback when no geometry is below. |
| `waves` | "Lake" preset | Up to 4 `GerstnerWave(directionDeg, wavelength m, amplitude m, steepness 0..1)`. Phase speed follows deep-water dispersion `c = sqrt(g / k)`, so only shape is authored. |
| `windDrivenWaves` | false | Scales amplitudes by `windSpeed / 3 m/s` (clamped 0..3) and aligns wave 0 with the wind. |
| `flowVelocity` | (0, 0) m/s | River flow: scrolls normal maps and foam, and drags floating bodies (§8). |
| `material` | bundled `water_default.skmat` | Schema `"water"` (§6.5), registered through `MaterialSchemas.register` (`engine/render/material/MaterialSchema.kt`). |
| `quadSize` | 0.5 m | Surface grid resolution near the camera (§6.1). |
| `density` | 1000 kg/m³ | Buoyancy (§8). |
| `linearDrag` / `angularDrag` | 1.0 / 0.5 | Per second, scaled by submerged fraction (§8). |
| `receiveShadows` | true | Water never casts. |

| `cutsOcean` | true | When the rectangle lies over an ocean (§6.7), the ocean is not drawn and not queried inside it, so a harbour pool or a lagoon at another level stays separate. |
| `buoyancy` on bodies | — | See §8.3: a body can opt out of `BuoyancySystem` (`RigidBody3D.buoyancy = AUTO / OFF / CONTROLLER`). |

Presets in the inspector: *Calm pool*, *Lake*, *Ocean swell*, *River* (flow 1 m/s).

### 3.5 `OceanComponent` (entity component, ECS registers; **new**: `engine/ecs/components/OceanComponent.kt`)

The infinite ocean (human, §14). At most one per scene; scene validation warns about a second one and only the first
is used.

| Field | Default | Notes |
|---|---|---|
| `seaLevel` | 0 m | World Y of the still surface. Only the Transform's Y is ignored; the ocean has no position or rotation. |
| `waves` | "Ocean swell" preset | Up to 4 `GerstnerWave`s, same type and normalization as `WaterComponent` (§6.1). |
| `windDrivenWaves` | true | As `WaterComponent`. |
| `material` | `water_ocean.skmat` | Schema `"water"`, the same as bounded water. |
| `quadSize` | 0.5 m | Inner clipmap quad size (§6.7). |
| `shoreMask` | `AUTO` | `AUTO`: derived from the REN-28 terrain in the scene, if any (§6.7). `NONE`: the sea is everywhere. |
| `shoalingDepth` | 8 m | Water shallower than this scales wave amplitude down and crest foam up. |
| `current` | (0, 0) m/s | Constant drift for floating bodies and foam scroll. |
| `density`, `linearDrag`, `angularDrag` | 1025 kg/m³, 1.0, 0.5 | Buoyancy (§8.4). |
| `receiveShadows` | true | As bounded water. |

### 3.6 Runtime objects (render domain, not serialized)

- **`FoliageCell`** (**new**, `engine/render/vegetation/FoliageCell.kt`): a square cell (16 m by default, or REN-28
  terrain chunk size when on terrain) with an AABB and, per `FoliageType`, a packed instance array. Each instance is
  16 floats (64 B): a 3×4 world matrix (12), then `tint`, `random` (0..1, seeded), `windPhase` and `lodFade`.
- **`InstanceBatch`** (**new**, `engine/render/instancing/InstanceBatch.kt`): one mesh part × material × instance
  buffer, with a vertex input that combines the mesh's static buffers and the instance buffer. It is the "instance
  batch" REN-28 §7 refers to. It is created by this note's task 2, which the phase order (§12) runs at the start
  of the terrain phase, before REN-28 task 7 (scatter) needs it.
- **`WaterSurfaceMesh`** (**new**, `engine/render/water/WaterSurfaceMesh.kt`): grid chunks plus LOD index sets. It is
  built by the pure `WaterMeshBuilder` (**new**).

## 4. Vegetation rendering

### 4.1 Instancing: what exists and what's missing

- `ParticlePass` (`engine/render/renderer/passes/ParticlePass.kt`) already draws instanced. It uses a
  `BufferUsage.INSTANCE` buffer, a `VertexBinding(perInstance = true)` (`engine/render/rhi/RhiTypes.kt`:194,
  `VertexLayouts.PARTICLES`) and `cmd.draw(..., instances = count)`. So the RHI and both backends support it.
- Meshes do not. `ModelRenderer.draw` (`engine/render/renderer/ModelRenderer.kt`) calls `drawIndexed(part.vertexCount)`
  with the part's own `RhiVertexInput`. `shader_3d_default.glsl` reads a per-object `transformationMatrix` uniform.
- **Additions** (all render domain):
  - `VAOLoader.instancedInput(part, instanceBuffer)` (**new method**): `VAOLoader` already keeps each mesh's buffers
    (`GpuMeshBuffers`). This builds a second `RhiVertexInput` over the same buffers plus the instance buffer. Cached
    per (part, buffer) and released with the mesh.
  - `VertexLayouts.INSTANCE_TRANSFORM` (**new**): locations 8–11 (three `vec4` matrix rows plus a params `vec4`),
    after the mesh attributes 0–7. `VertexLayoutShaderTest` checks it like the other layouts.
  - `ShaderFeature.INSTANCED` and `ShaderFeature.WIND` (**new enum values** in
    `engine/render/shader/ShaderPreprocessor.kt`). They are compiled through the existing
    `AssetsManager.getShaderVariant(path, features)` (it already serves `shadow.glsl` with `ALPHA_TEST`). Under
    `INSTANCED`, `shader_3d_default.glsl`, `shadow.glsl` and `shader_3d_picking.glsl` build the model matrix from the
    instance rows instead of the uniform. Nothing changes for non-instanced draws.
  - `ModelRenderer.drawInstanced(part, material, batch, count)` (**new**): binds the material with the existing
    `MaterialBinder` and uses the same four `PipelineCache`s.

### 4.2 `VegetationPass` (**new**, `engine/render/renderer/passes/VegetationPass.kt`)

- **Placement in the graph:** after `GeometryPass`'s opaque meshes and before transparent ones. Today
  `GeometryPass.execute` draws opaque and BLEND meshes from one `DrawQueue` (`sortKeyOf`, transparent last), then the
  sky dome. So task 1 splits it (§6.2). Order (`RenderPasses.inExecutionOrder`, `engine/render/RenderResources.kt`:90):
  `shadow, picking, geometry, [terrain], vegetation, water, transparent, particles, postProcess, debug, ui`.
- **I/O for the EDT-54 graph window *(30g)*:** inputs `ShadowMap`, `LocalShadowMap`; outputs `SceneColor`,
  `SceneDepth` (`RenderResourceIds`, `engine/render/graph/RenderGraphInfo.kt`). It gets `lbl.render_pass.vegetation*`
  keys.
- **Lighting:** the same uniforms as `GeometryPass`: `LightingUniformsLoader`, `LightBuffer`, the shared
  `ShadowFrame`, and `EnvironmentProbe.bind`. Today `GeometryPass` owns the `EnvironmentProbe` and the `LightBuffer`
  (built in `RenderResourcesFactory`). Task 1 hoists them into `RenderResources` so vegetation and water share one
  bake and one upload per frame. Per-object light culling (`LightCulling.select`) is per cell for vegetation: up to 8
  lights per cell, uploaded once per batch.
- **Per frame (CPU):**
  1. Frustum-cull cells (AABB, the same test as REN-28 §4.1).
  2. **Grass:** each visible cell × type has a **static** instance buffer, uploaded once at build, so there is one
     instanced draw per visible cell × type. GL 3.3 has no base-instance, so cells can't share one buffer. Distance
     density falloff runs in the vertex shader: an instance with `random > density(distance)` collapses to a
     degenerate position. Cells beyond `fadeEnd` (60 m default, quality-scaled) are skipped.
  3. **Meshes (trees):** per instance, `VegetationLod.select` (**new**, pure) picks LOD *i* by projected screen size,
     with 10% hysteresis stored per instance. Visible instances are bucketed by LOD into one `STREAM` instance buffer
     per type × LOD, so there is one draw per mesh part × type × LOD. That is a few thousand instances, cheap on the
     CPU.
  4. **LOD transitions:** dithered cross-fade over 0.3 s. `lodFade` drives a screen-door `discard` in the
     `ALPHA_TEST` path, which avoids blending.
- **Shadows:** `ShadowRenderer` (`engine/render/renderer/ShadowRenderer.kt`) gets instanced casters. Cascades use a
  LOD one coarser, `MASK` materials use the existing `alphaTestShader`, and LOD ≥ 2 and grass never cast.
  `ShadowLayerCache` (`engine/render/shadow/ShadowLayerCache.kt`) hashes caster signatures.
  - **Medium quality and above (human, §14):** wind-animated casters are drawn with the `WIND` shadow variant and add
    the wind time to their signature, so cascades with swaying trees re-render every frame.
  - **Low quality (human, §14):** tree shadows use the rest pose. The shadow variant is compiled without `WIND` and the
    wind time is left out of the signature, so the cascades stay cached while the trees themselves still sway.
  - The quality level comes from the REN-30 graphics preset; switching it invalidates the cache once.
- **Picking:** `PickingPass` draws each batch with the entity id of the owning `FoliageComponent` or terrain.
  Clicking a tree selects the patch, not the instance.
- **Stats:** add `EngineStats.foliageInstances`, `foliageDraws` and `waterDraws` (`engine/render/EngineStats.kt`).
  Draws already count through `drawCalls`.

### 4.3 Impostors (later slice)

`ImpostorBaker` (**new**, `engine/render/vegetation/ImpostorBaker.kt`) renders a `MESH` type's last LOD from 8 yaw
angles into an atlas (albedo+alpha, normal), using the offscreen path `ThumbnailRenderer` already uses. Bakes run
when the `.skfoliage` asset is imported or changes (AST hook) and are cached next to it. The far LOD is a camera-facing
quad that picks the nearest two views and blends them. Until then, the last mesh LOD is drawn down to
`cullScreenSize`.

### 4.4 Placement (shared with REN-28 §7)

- **Placer:** `ScatterPlacer` (REN-28 task 7, **new**: a jittered grid at `minSpacing`, rejection by rule, seeded per
  cell from `RandomService.streamSeed(projectSeed, "$seedName/$layer/$cx,$cz")` *(30g)*,
  `engine/ecs/random/RandomService.kt`). `FoliageComponent` uses the same placer with its area as the domain, so
  placement is deterministic, independent per cell and layer, and regenerates identically.
- **Instance randomness:** per-instance `random`, `tint` and `windPhase` come from the same per-cell stream, so a
  reroll changes them consistently.
- **Builds:** run on `JobSystem` at load and after edits. Cells swap in when ready (as REN-28 §9).

### 4.5 Terrain and biomes

REN-28 `ScatterRule` (§7) gains `foliageType: GUID?` as an alternative to `prefab`. A rule with a foliage type is
always drawn instanced, never spawned as entities. Biome scatter rules (REN-28 §6) then drive species, density and
scale per biome with soft borders. For example: beach (sparse dune grass), grassland (dense grass, a few trees), peak
(no grass, rocks with colliders). The `TERRAIN` placement of `FoliageComponent` reads `HeightField.heightAt` and
`normalAt`, so patches sit on sculpted ground.

### 4.6 Foliage on arbitrary meshes (human, §14: yes, now)

`FoliageComponent.placement = MESH` places plants on static geometry: rooftops, planters, ramps, rocks, a level built
from meshes.
- **`SurfaceProbe`** (**new**, pure interface: `engine/render/vegetation/SurfaceProbe.kt`):
  `castDown(x, z, top, bottom, layerMask, surfaces, out: SurfaceHits)`. It returns hit points and normals, and reuses
  `out`, so it doesn't allocate.
  - **Three implementations:**
    - `FlatSurfaceProbe` (`FLAT`);
    - `HeightFieldSurfaceProbe` (`TERRAIN`, REN-28 `HeightField`);
    - `PhysicsSurfaceProbe` (`MESH`), over `IPhysics3D.raycast` / `raycastAll` (`engine/physics3d/IPhysics3D.kt`
      *(30g)*, `PhysicsHit` in `PhysicsQueries.kt`).
  - `ScatterPlacer` (REN-28 task 7) takes a probe. Placement, rejection by slope, spacing and seeding are then the same
    code for all three modes, and unit tests use a fake probe.
- **Rays:** one vertical ray per candidate, from the top of the area box to its bottom, against `surfaceLayers`.
  - `TOP` keeps the first upward-facing hit.
  - `ALL` keeps every upward-facing hit, through `raycastAll`, so a multi-level car park gets plants on each level.
  - Hits are rejected when the normal is steeper than `maxSlope`, when they are on a dynamic body or a `Character`,
    or when the density mask (projected on the area's XZ) rejects them.
- **Edit-mode physics (with skate-physics, task 18):** rays need the scene's static colliders while the editor is not
  playing. Task 18 makes sure the physics world holds static and kinematic colliders in edit mode, kept in sync with
  transform and collider edits, without stepping. skate-physics confirms what `PhysicsSystem` already does in edit
  mode; if the colliders are already there, task 18 is only the sync guarantee and tests. Queries run on the main
  thread, which is how the engine already uses the Bullet and Jolt query APIs.
- **Bake:** the editor raycasts on the main thread with a 2 ms per frame budget, using the same time-budgeted queue
  pattern as REN-28's `StreamingUploadQueue`. A 20 × 20 m patch at 4 candidates per m² is 1,600 rays, about one
  frame. Cells swap in when complete.
- **Placement cache:** the baked instances are saved as `<scene>.foliage/<entity guid>/placement.bin`, the sidecar
  folder masks already use (§10).
  - Play and the exported game load the cache and **never raycast at load**. Placement then doesn't depend on
    backend raycast differences (Bullet versus Jolt), needs no physics at load and is deterministic.
  - The cache key is a hash of the component fields, the seed and a **surface signature**: GUID, transform and
    collider hash of every object whose bounds overlap the area box.
- **Dirty tracking (editor):** moving, adding or removing an overlapping object, or editing its collider, marks the
  patch stale. It rebakes 0.5 s after the edit ends, not during a gizmo drag. A "Rebake" button forces it.
- **Stale or missing cache at run time:** rebaked at load under the same budget, with one warning in the log.
- **Limits:** surfaces must be static. Foliage doesn't follow a mesh that moves at run time (documented), and dynamic
  bodies are never hit. Terrain scatter keeps using the `HeightField` (REN-28), not rays.

## 5. Wind

### 5.1 `WindField` (**new**, pure: `engine/environment/WindField.kt`)

- **Package:** a neutral `engine/environment` package, not `render`, because gameplay, particles and audio can sample
  wind without importing the renderer. Add it to `PureLogicIsolationGuardTest`.
- **Formula:** `sample(x, z, t, out: Vector3f)` returns `dir × speed × (1 + gustStrength × gust(x, z, t))`, where
  `gust` is a sum of 3 sines along the wind direction (`dot(p, dir) / gustScale − t × speed / gustScale`) with
  cross-wind modulation. Gust fronts visibly roll across a field.
- **GLSL mirror:** sines rather than noise, so `wind.glsl` (**new include**, like `fog.glsl`) evaluates exactly the
  same function without a texture. A table test pins the Kotlin output for fixed inputs.

### 5.2 Vertex displacement (`wind.glsl`, `ShaderFeature.WIND`)

- **`GRASS` profile:**
  - Bend: horizontal offset = `wind × stiffness⁻¹ × h²`, where `h` is the vertex's height in the clump (0 root,
    1 tip; `GrassMeshBuilder` writes it in `aColor.a`).
  - Flutter: `turbulence × sin(t × 9 + windPhase + x)` at the tips.
  - Length is kept by lowering the tip as it bends.
- **`TREE` profile:** vertex-colour convention, documented for artists in `docs/vegetation.md` (**new**):
  - Main bend: the whole tree sways around its root by height above the root, normalized by model bounds.
  - Branch: `aColor.r` = branch weight.
  - Leaf flutter: `aColor.g` = flutter weight, `aColor.b` = phase offset.
  - Models without vertex colours get main bend only.
- **Same displacement everywhere:** the shadow and picking variants apply the same displacement, so shadows and clicks
  match.
- **Uniforms:** `uWindDir`, `uWindSpeed`, `uWindGust`, `uWindTurbulence`, `uWindTime`. Names go in
  `engine/utils/ShaderConst.kt`.

### 5.3 Animation clock (`EnvironmentClockSystem`, **new**: `engine/ecs/systems/EnvironmentClockSystem.kt`; ECS reviews)

There is no scene time for shaders today. The clock advances `EnvironmentComponent.runtime.animationTime`:
- **In Play:** in `fixedUpdate`, as tick count × `fixedDt`. It is deterministic and identical on every machine with
  the same tick (netcode friendly). Rendering adds `fixedStepAlpha × fixedDt`, as `PhysicsSystem` interpolation does.
- **In edit mode:** in `update`, by frame dt, when `animateInEditor` is on.
- **Paused:** frozen, per `docs/design/pause-semantics.md` (systems frozen, no catch-up, no wall clock).

Water (§6) and buoyancy (§8) read the same clock, so what floats is what you see.

## 6. Water rendering

### 6.1 Surface mesh and waves

- **`WaterWaves`** (**new**, pure: `engine/environment/WaterWaves.kt`): a Gerstner sum of up to 4 waves.
  - `displace(x, z, t, out)` gives the surface point and `heightAt(x, z, t)` the height. `heightAt` inverts the
    horizontal Gerstner shift with 3 fixed-point iterations, which is exact enough for buoyancy.
  - `normalAt` and `jacobian` (for crest foam).
  - `water_waves.glsl` (**new include**) mirrors it, and a table test pins both formulas.
  - Steepness is normalized so the sum of `steepness × k × amplitude` stays ≤ 1, so waves never loop.
- **Grid:** `WaterMeshBuilder` (**new**, pure) builds a grid at `quadSize` in 32 × 32-quad chunks with 3 LOD index sets
  (step 1, 2, 4) and skirts, as REN-28 §4.1. A 20 × 20 m pool at 0.5 m is 40 × 40 quads in 2 × 2 chunks. Chunks are
  frustum-culled. LOD uses distance and wave amplitude, and the same screen-error selection as `TerrainLod`
  (REN-28 task 4). Water lands first in the phase order (§12), so task 7 creates the shared pure helper
  `ScreenErrorLod` (**new**, `engine/render/lod/ScreenErrorLod.kt`) and REN-28 task 4's `TerrainLod` reuses it.
- **Vertex shader:** the vertex shader displaces the grid with the Gerstner sum.

### 6.2 Pass split and `WaterPass` (**new**: `engine/render/renderer/passes/WaterPass.kt`)

1. **Task 1 split:**
   - `GeometryPass` keeps clear, opaque and MASK meshes, 2D sprites and the sky dome.
   - A new `TransparentPass` (`engine/render/renderer/passes/TransparentPass.kt`) draws the `AlphaMode.BLEND` meshes.
     Their order (scene order) is unchanged, so rendering stays pixel-identical when there is no water or vegetation.
   - This gives water the order rev 1 asked for: after opaque, before post. Transparent objects then draw over water.
2. **`WaterPass.prepare`:** collect enabled, visible `WaterComponent`s. If there are none, the pass does nothing and
   costs nothing.
3. **Scene copy:**
   - Blit `SceneColor` and `SceneDepth` into a `WaterSceneCopy` target (**new**, a `ColorTarget`-like pair at full
     resolution, created on first use).
   - `RhiCommandList.blit` copies colour only today (`GlCommandList.kt`:144, `GL_COLOR_BUFFER_BIT`), so task 7 adds a
     `mask` parameter (COLOR, DEPTH) on the RHI and the GL backend; the Vulkan backend follows in task 14a.
   - The copy avoids sampling the depth attachment being tested (a GL feedback loop).
   - MSAA is not a concern: `GlRenderTarget` uses single-sample textures (`resolve()` is a no-op).
   - New resource ids: `SceneColorCopy`, `SceneDepthCopy` in `RenderResourceIds` *(30g)*.
4. **Draw:** draw the water chunks with `water.glsl` (**new**), depth test on, **depth write on**, so the
   transparent and particle passes test against the surface. It is double-sided only when the camera is under the
   surface (underwater slice, §6.6).

### 6.3 Shading (`water.glsl`, includes `lighting.glsl` from REN-28 task 1, `fog.glsl`, `water_waves.glsl`, `wind.glsl`)

- **Normal:** the Gerstner normal plus two scrolling detail normal maps. They scroll with `flowVelocity` and the wind
  direction, at different scales.
- **Refraction:**
  - Sample `SceneColorCopy` at `screenUV + normal.xz × refractionStrength × saturate(thickness)`.
  - If the refracted texel's depth is in front of the water (an object above the surface), fall back to the
    un-offset UV. That avoids the classic halo.
- **Absorption:**
  - `thickness` = linearized `SceneDepthCopy` minus the water fragment depth. Where nothing is below, it uses
    `depth` (§3.4).
  - Colour = `mix(refraction × shallowColor, deepColor, 1 − exp(−thickness / absorptionDistance))` (Beer–Lambert).
- **Reflection:**
  - **IBL**, always available: `EnvironmentProbe` textures (`uEnvSky` for mirror-like water at roughness ≈ 0,
    `uEnvPrefiltered` for rough water), with the same `uEnvRotation`. This reuses REN-27 directly.
  - **Screen-space reflections (SSR):** a view-space ray march against `SceneDepthCopy`, then a binary refine, with
    fades at screen edges and for rays facing the camera. On a miss it falls back to IBL. This is what reflects the
    surrounding geometry (rev 1 acceptance 1). Step counts come from the Graphics quality preset (REN-30 settings):
    Low = IBL only, Medium = 16 steps, High = 32 steps.
  - **Planar reflections (human, §14):** not now. SSR + IBL is the design; a per-water planar option is a later task
    only if pools look wrong.
- **Lighting:** Fresnel–Schlick with F0 = 0.02, GGX sun specular, sun shadows via `sunShadow` (`lighting.glsl`), local
  lights through the shared `LightBuffer`, then fog.

### 6.4 Foam

- **Shoreline and intersection foam:** where `thickness < foamDistance` (default 0.6 m). Rocks, pillars, ramps and the
  terrain shore all produce it, because it reads the scene depth copy.
  - Two water bodies meeting: the second body isn't in the copy when the first draws. Overlapping bodies get no foam
    between them, a documented limitation.
- **Crest foam:** where the Gerstner Jacobian is below a threshold (steep crests), so it shows on ocean swell and not
  on calm pools.
- **Foam texture:** both foam kinds are masked by a tiling foam texture scrolled with the flow. Rev 1 asked for
  texture-driven foam.

### 6.5 Water material schema (`"water"`)

Registered in `MaterialSchemas` with shader `water.glsl`:

- **Params:** `shallowColor`, `deepColor`, `absorptionDistance` (3 m), `roughness` (0.04), `refractionStrength`
  (0.03), `normalScale` (2 values), `normalTiling` (2 values), `foamDistance`, `foamColor`, `ssrEnabled`.
- **Textures:** `normalA`, `normalB`, `foam`.
- **Bundled presets:** `water_default.skmat` (lake), `water_pool.skmat` (clear cyan, short absorption) and
  `water_ocean.skmat`. A different look is a different `.skmat` (rev 1 acceptance 4).

### 6.6 Picking, debug and editor visuals

- **Picking:** `PickingPass` draws the surface chunks (LOD 2) with the entity id.
- **Debug:** `DebugPass` draws the volume box (surface plus `depth`) when the entity is selected.
- **Underwater (human, §14: yes, a small later slice, no caustics; task 12):**
  - **Detection:** the camera is underwater when `WaterQueries.sample` (§8.4) at the camera position returns a
    surface above it. This works for bounded water and the ocean, and for any camera (editor, follow, a future swim
    camera, §8.3).
  - **Surface from below:** the water draws double-sided; from below, it shows total internal reflection outside
    Snell's window (a Fresnel term on the flipped normal) and the refracted sky inside it.
  - **Volume:** a new `PostEffect.UNDERWATER` (`engine/render/renderer/passes/PostEffect.kt`), first in
    `DEFAULT_PIPELINE` and a no-op above water, tints and fogs the scene with the material's
    `deepColor` and `absorptionDistance` (Beer–Lambert on scene depth), so it matches the view from above.
  - **Not included:** caustics, god rays, particles; underwater audio filtering is an AUD follow-up.

### 6.7 Infinite ocean (human, §14; tasks 19–21, built last)

The ocean is drawn by the same `WaterPass` with the same `water.glsl`, under a new `ShaderFeature.OCEAN` define. It
shares the scene copy, SSR, IBL, foam, fog and lighting with bounded water, and draws after the bounded bodies.

**Mesh: camera-following clipmap** (`OceanMeshBuilder`, **new**, pure: `engine/render/water/OceanMeshBuilder.kt`)
- **Rings:** an inner 64 × 64-quad patch at `quadSize` (32 m across at 0.5 m), then 7 rings with the same layout, each
  with double the quad size of the previous one. Ring 7 reaches ±4 km with 64 m quads.
- **Snapping:** each ring's origin is snapped to twice its quad size in XZ, so vertices don't swim as the camera
  moves. The outer quarter of each ring geomorphs towards the next ring's positions, so there are no seams or pops.
- **Horizon skirt:** a last flat ring from 4 km to the camera's `farPlane` (`CameraComponent.farPlane`), at
  `seaLevel`, without displacement.
- **Cost:** the buffers are static and built once per `quadSize`. Only the ring origins (`uOceanRingOrigin[8]`)
  change per frame, so nothing is uploaded per frame. Each ring is split into 4 quadrants for frustum culling, for at
  most 36 draws.
- **Camera height:** rings whose quads would be under 1/200 of the camera height above `seaLevel` are skipped, so
  flying high doesn't create tiny triangles.

**Waves and LOD**
- **Same waves:** `WaterWaves` (§6.1) and `water_waves.glsl`, using the ocean's `waves`.
- **Fading short waves:** in the vertex shader, each wave fades out where its wavelength is under 4 × the local quad
  size, which prevents aliasing. Detail normal maps and crest foam also fade with distance.
- **Physics doesn't fade:** `WaterWaves.heightAt` never fades a wave, so physics always sees the full sum. A difference
  appears only where the renderer has faded short waves, beyond about 64 m from the camera, where it isn't visible.

**Horizon**
- With fog on, `fog.glsl` blends both the ocean and the sky into the fog colour, so the horizon seam disappears.
- With fog off, the horizon skirt is seen at grazing angles, where Fresnel goes to 1. It then reflects `uEnvSky` in
  the horizon direction, which is the same texture the sky dome draws, so water and sky meet without a line.
- **Far plane:** `CameraComponent.farPlane` defaults to 1000 m, so the ocean ends at 1 km by default. The ocean
  inspector suggests 4 km (or fog) when an ocean is present; REN-28's far field wants the same.
- No earth curvature: worlds are capped at 16 km (REN-28 §16).

**Shoreline against REN-28 terrain and streaming**
- **Occlusion:** terrain detail chunks and the far field draw before water and write depth, so land hides the sea.
- **Near shore** (≤ 1 km): foam and absorption come from the scene depth copy, as for bounded water. They are right
  as soon as a detail chunk is drawn.
- **`OceanMask`** (**new**, `engine/render/water/OceanMask.kt`, pure builder):
  - **Build:** a flood fill over REN-28's `TerrainOverview` (8 m spacing, always resident, REN-28 §3.2), starting
    from the terrain border. A cell is **sea** if it is below `seaLevel` and connected to open water.
  - **Result:** an R8 texture at overview resolution (4 MB at 16 km), plus a CPU copy for queries (§8.4). Outside the
    terrain bounds it is all sea. Inland basins below sea level stay dry (open question 1).
  - **Use:** ocean fragments over non-sea texels are discarded, with a dithered one-texel edge.
  - **Far shore:** beyond 1 km, 24-bit depth (GL 3.3, no reversed Z) can't separate water from the far-field terrain
    reliably, so the far shoreline is decided **by the mask, not by depth**.
  - **Rebuilds:** on a worker, when the overview changes (REN-28 §15.7 incremental refresh) or `seaLevel` changes.
  - **Without terrain,** or with `shoreMask = NONE`, the sea is everywhere.
- **Shoaling:** sea depth is `seaLevel` minus the overview height, sampled from the overview R16 texture that REN-28
  already keeps on the GPU (§15.4), so it adds no memory. Wave amplitude scales by `saturate(depth / shoalingDepth)`
  and crest foam grows where waves shoal.
- **Streaming:** the ocean itself never streams. It uses only the clipmap, procedural waves and always-resident data
  (overview, mask). So it is correct in the far field before detail chunks arrive, and a dedicated server (no
  rendering, REN-28 §15.8) keeps only the mask for queries.
- **Bounded water in the ocean:** the ocean is cut out inside up to 8 `WaterComponent` rectangles with `cutsOcean`
  (an oriented-rect uniform array), for harbour pools and lagoons at another level.
- **Precision:** within REN-28's ±8 km cap, ring origins are world floats and vertices are ring-local, so precision
  is fine.

## 7. Performance budget (dev machine, measured with the REN-22 per-pass GPU timers)

| Item | Budget |
|---|---|
| `VegetationPass` (showcase patch, ~60k grass visible + 200 trees) | < 1.5 ms GPU, < 0.5 ms CPU |
| Vegetation shadows (trees, wind on) | < 1 ms GPU |
| `WaterPass` (one 20 × 20 m body, SSR High) | < 1 ms GPU, including the scene copy |
| Ocean (full screen, SSR High, *Big Island*) | < 1.5 ms GPU, < 0.1 ms CPU (ring origins only) |
| `MESH` foliage bake (editor) | ≤ 2 ms main thread per frame while baking; 0 at run time with a valid cache |
| Allocation per frame | none: instance staging is reused, like `ParticlePass.instanceBuffer` |

## 8. Physics: water volumes and buoyancy (PHY-16, needs skate-physics review)

### 8.1 Volumes

- A `WaterComponent` is **not** a physics body. Bodies are tested against the water's oriented box (surface rectangle
  plus `depth`, wave amplitude above) with a cheap AABB test. That avoids trigger bodies and works the same on every
  backend.
- `PhysicsEvents.kt` gets `WaterEntered(water, body)` and `WaterExited` events, following the existing
  `TriggerEntered` pattern, for splash audio (AUD) and gameplay (GPL).

### 8.2 `BuoyancySystem` (**new**: `engine/ecs/systems/BuoyancySystem.kt`, physics domain)

- **Placement:** runs inside `PhysicsSystem.fixedUpdate` before `physics3d.step(fixedDt)`, the same hook the vehicles
  use (the `vehicleSolver.step` loop). Only dynamic, non-kinematic `RigidBody3D`s are affected.
- **Samples:**
  - `BuoyancyMath.samples(shape)` (**new**, pure: `engine/physics3d/water/BuoyancyMath.kt`) turns a `ColliderShape`
    into sample spheres. Each sphere has a local position, a radius and a share of the shape's volume.
    - Box: 2 × 2 × 2, or 3 × 3 × 3 when any side is over 2 m.
    - Sphere: 7.
    - Capsule and cylinder: spheres along the axis.
    - Hull, compound and mesh: an AABB grid scaled to the shape's volume estimate.
  - Samples are built once per body and rebuilt when the shape or scale changes.
- **Per fixed step**, for each sample at world position `p`:
  - `h = WaterWaves.heightAt(p.x, p.z, t)`, with the same clock (§5.3);
  - submerged fraction `f = sphereCapFraction(h − p.y, r)`;
  - buoyancy `F = ρ g V_s f`, upward, applied with `IRigidBodyHandle.applyForce(force, relPos)`. Off-centre samples
    give the righting torque, so boxes settle flat and logs roll.
- **Drag:**
  - linear `−linearDrag × mass × f_total × (v − flow)`, where `flow` is the water's `flowVelocity`, so rivers carry
    things;
  - angular `−angularDrag × f_total × ω`, through `applyTorqueImpulse`.
- **Floating:** bodies float when mass / shape volume < ρ, which uses the real `RigidBody3D` mass.
- **Backends:** only uses `applyForce` and `applyTorqueImpulse`, so Bullet, Jolt and `SimplePhysics3D` behave the same.
  Jolt's native buoyancy is not used, to keep backend parity and determinism.

### 8.3 Characters: bail now, swimming later (human, §14)

The skater is driven by `PlayerController` and `SkateboardController` (`game/skateboard/SkateboardController.kt`), not
by free buoyancy.
- **Now (task 15, GPL):** entering water bails the skater and respawns them at the last safe spot. This is GPL
  **policy** in `PlayerController`: a `waterResponse = BAIL` setting that reacts to `WaterEntered`. The engine has no
  built-in "water kills the player" rule.
- **Later direction (GPL/ANI, recorded so nothing here blocks it):** the skater must be able to **swim**, at the
  surface and underwater:
  - **GPL:** swim states (tread, surface swim, dive, underwater swim, climb out), swim controls (stroke, dive, surface,
    sprint), and a `waterResponse = SWIM` option;
  - **ANI:** swim animations in the existing state machine (no engine change);
  - **Camera:** an underwater follow camera;
  - **AUD:** underwater filtering.
- **What this design guarantees for swimming:**
  1. **Queries rich enough for a swim controller:** `WaterQueries.sample` (§8.4) gives the surface height, normal,
     flow or current, density and floor height at any point, for bounded water and the ocean. `WaterQueries.submersion(
     capsule, out)` (**new**) gives the depth of the feet, centre and head, and the submerged fraction.
  2. **Characters aren't pushed around by `BuoyancySystem`:** a body opts out with `RigidBody3D.buoyancy` (**new
     field**). `AUTO` is normal buoyancy; `OFF` ignores water; `CONTROLLER` (the default for a body with a
     `PlayerController`) reports `WaterEntered`/`WaterExited` but applies no force, so a future swim controller applies
     its own.
  3. **Events per body:** `WaterEntered` and `WaterExited` carry the water source (bounded or ocean) and the body.
     Head-under or head-out transitions come from `submersion`, so no extra events are needed.
  4. **Underwater rendering is per camera, not per skater** (§6.6), so a diving follow camera just works.
  5. **No design rule assumes water is fatal or shallow:** bounded volumes have a `depth`, and the ocean has a floor
     from the terrain or none.

### 8.4 Ocean volume and queries (PHY-16 extension, task 20)

- **`WaterQueries`** (**new**, `engine/environment/WaterQueries.kt`, pure apart from reading the components) is the
  single entry point for physics, gameplay, audio and the underwater view: `sample(x, z, t, out: WaterSample):
  Boolean`, with surface Y, normal, flow, density, drags, floor Y and the source entity.
  - **Lookup order:** bounded bodies first (they cut the ocean), then the ocean where the `OceanMask` CPU copy says
    sea.
  - **Ocean floor:** REN-28 `HeightField.heightAt`, which falls back to the overview far away; without terrain there is
    no floor (−∞).
- **`BuoyancySystem` and the ocean:** the ocean has no bounds, so the broad phase is a test that a body's AABB minimum
  Y is below `seaLevel` plus the maximum crest (the sum of amplitudes), then the mask at the body's centre. This is
  O(bodies), with no volume AABB. Forces, drag and current then work as in §8.2.
- **Far bodies:**
  - **Inside the terrain bounds:** a body over a chunk without a collider is suspended by REN-28's rule (§15.5), even
    at sea, until a focus comes near. That keeps far simulation free.
  - **Outside the terrain bounds:** open sea never freezes, and floating bodies keep simulating. The cost is bounded
    by the body count.
- **Events:** `WaterEntered` and `WaterExited` with the ocean entity as the source.
- **Dedicated server:** queries and buoyancy need only the mask and the waves, with no rendering, so they run there
  unchanged.

## 9. Editor tools (editor domain, EDT tasks)

- **Foliage paint mode:**
  - It reuses the REN-28 §9 brush (radius, strength, falloff), extended to `FoliageComponent` areas.
  - Paint or erase the density mask per layer. Strokes are undoable with the same rectangle-diff command.
  - On terrain, the REN-28 scatter brush already covers per-rule masks.
- **`FoliageType` inspector** for `.skfoliage` assets: LOD list with screen sizes and a live LOD-colour debug view,
  wind profile with a preview, and a "Bake impostor" button after task 11.
- **Water inspector:** wave presets, a per-wave editor, material picker, a volume gizmo and a "Freeze time" toggle.
- **`MESH` placement:** the area box gizmo shows its height, a stale or baking badge, a "Rebake" button and a hit
  preview (dots on the hit surfaces) (§4.6).
- **Ocean inspector:** sea level, wave presets, shore mask mode with a mask preview on the REN-28 minimap, and the
  far-plane suggestion (§6.7). A bounded water's `cutsOcean` rectangle is drawn on the sea.
- **Environment inspector:** a wind section with presets. When the Environment is selected, a wind arrow is drawn in
  the viewport via `DebugPass`.

## 10. Assets and persistence (needs skate-assets review)

- **`.skfoliage`:** JSON, versioned, with a GUID `.meta` like other assets. It is saved under the current schema rules
  with no migrations (rule 8). Hot reload rebuilds the affected cells.
- **Packing:** `ReferenceCollector` (`engine/assets/pack/ReferenceCollector.kt` *(30g)*) follows LOD model GUIDs,
  the material override and the impostor atlas.
- **Foliage density masks:** `FoliageComponent` masks are a sidecar folder `<scene>.foliage/<entity guid>/<layer>.png`
  (R8), referenced by GUID. Masks on terrain stay in the `.skterrain` folder (REN-28 §10).
- **`MESH` placement cache:** `<scene>.foliage/<entity guid>/placement.bin` (§4.6): versioned, with the cache key in
  its header and packed instances, about 64 B per instance and deflated. It is packed with the scene. A stale cache
  is still loadable and is rebaked.
- **Ocean:** `OceanComponent` is serialized in the scene. `OceanMask` is **not** stored; it is rebuilt from the terrain
  overview on load (a flood fill over 2049² cells takes about 30 ms on a worker).
- **Water `.skmat`:** normal `.skmat` assets with schema id `"water"`. `MaterialAsset` already stores the schema id.
- **Bundled assets** (AST/TLG source them):
  - grass is generated (no asset);
  - 2 CC0 low-poly trees with 2 LODs each and the vertex-colour convention, plus a bush (human, §14: yes, e.g.
    Quaternius or Kenney; the licence goes in `docs/third-party.md`);
  - two tileable water normal maps and a foam texture (CC0, e.g. ambientCG/Poly Haven via AST-16, or generated
    offline from `NoiseField`).

## 11. Tests and showcase

- **Unit tests (no GL):**
  - `WindField`: determinism and its table, gust travel speed, zero at speed 0.
  - `WaterWaves`: table against fixed values; `heightAt` inverse within 1 cm of `displace`; the steepness clamp.
  - `GrassMeshBuilder`: vertex count and the height channel.
  - `VegetationLod`: selection, hysteresis and fade timing.
  - `FoliageCell` packing (16 floats, matrix layout).
  - `FoliageComponent` placement: deterministic per seed, independent per cell and layer, respects the mask.
  - `WaterMeshBuilder`: chunk borders and skirts.
  - The `VertexLayouts.INSTANCE_TRANSFORM` versus shader-locations check.
  - `ShaderPreprocessor` defines for `INSTANCED`/`WIND`.
  - Shadow signature changes with wind time only for wind-animated casters.
  - Pass order and the I/O declarations.
  - `BuoyancyMath`:
    - a 1 m³ box at 500 kg/m³ settles at half depth;
    - a sphere-cap fraction table;
    - the righting torque sign;
    - flow drag moves a body downstream.
  - `.skfoliage` round-trip.
  - `MESH` placement with a fake `SurfaceProbe`: `TOP` versus `ALL`, slope rejection, layer mask, dynamic bodies
    ignored, mask applied, deterministic; the cache key changes when an overlapping object moves and not when an
    unrelated one does; the placement cache round-trip; the bake respects its time budget (fake clock).
  - Tree shadow quality: at Low, the signature ignores wind time and the variant has no `WIND`; at Medium, it
    includes both.
  - Ocean:
    - clipmap ring layout, snapping (no vertex swim when moving under a snap step), geomorph continuity at ring
      borders, and ring skipping with camera height;
    - short-wave fade by quad size;
    - `OceanMask` flood fill: a basin below sea level and not connected stays dry, a bay connected through one cell
      is sea, and the area outside the bounds is sea;
    - shoaling scale;
    - the `cutsOcean` rectangle test.
  - `WaterQueries`: bounded water wins over the ocean; the floor comes from the `HeightField`; `submersion` depths for
    a capsule.
  - `BuoyancySystem` and the ocean: the broad-phase test; `CONTROLLER` bodies get events and no force; `OFF` bodies
    get neither.
  - Underwater detection at the camera for both water kinds.
- **GPU tests:**
  - With no vegetation or water, the task 1 split is pixel-identical to before.
  - Water refraction shows the pool floor.
  - Foam appears at an intersecting box.
  - Instanced draws match non-instanced ones for the same mesh and transform.
  - Timings are logged against §7.
  - Ocean: no seam between rings from a low grazing view, and the horizon meets the sky with fog off.
- **Feature Showcase** (`game/samples/FeatureShowcase.kt`, an `Area` entry in `AREAS`, TLG owns the file): a new
  **"Nature"** area behind the back row, next to REN-28's "Terrain" area.
  - **Water:** a 10 × 6 m pool, 1.5 m deep, using `water_pool.skmat`, with steps into it (shore foam). Inside are
    floating crates of 3 densities, a ball and a log, which bob, settle and roll (PHY-16 "done when").
  - **Vegetation:** a grass patch with two trees and bushes from a `FoliageComponent`.
  - **Wind button:** an overlay button that cycles calm, breeze and storm, like the existing fog button.
  - **Foliage on meshes:** bushes on top of a planter box and on a ramp deck, placed with `MESH`.
  - When REN-28 is in, the Terrain island gets biome vegetation. The **infinite ocean** is shown in REN-28's *Big
    Island* sample (task 21), where the shoreline, horizon and streaming matter. The showcase island keeps a bounded
    sea box, because the showcase has a short far plane.
- **Acceptance**, from rev 1 and made concrete:
  1. The water reflects the sky (IBL) and nearby showcase objects (SSR), and refracts the pool floor.
  2. A dropped debug cube and crates float and settle in water.
  3. Trees switch LOD with distance without popping, and grass and trees sway visibly with wind.
  4. Swapping a `.skfoliage` or water `.skmat` changes the look with no code change.
  5. On *Big Island*, the ocean reaches the horizon and meets the island shore with foam, without z-fighting at
     distance. Crates float far from the island.
  6. Bushes sit on the planter and the ramp, and moving the planter in the editor rebakes them.

## 12. Implementation tasks (after approval)

**Phase order (decided by the lead, human §14 decision 9), shared with REN-28 §12:**
**A** shared foundations (REN-28 tasks 1–2: `lighting.glsl`, `engine/procedural`) → **B** water and wind (this
note) → **C** REN-28 terrain → **D** REN-28 streaming → **E** vegetation (this note) → **F** infinite ocean (this
note; it needs terrain and streaming). Rows are in that order. Task numbers are kept from rev 2 so references stay
valid; new tasks are 17–21, and split tasks get a letter.

| Phase | # | Task | Domain | Deps |
|---|---|---|---|---|
| A | — | (REN-28 tasks 1–2) | REN | — |
| B | 1 | Split `GeometryPass` into opaque `GeometryPass` and `TransparentPass`; hoist `EnvironmentProbe`/`LightBuffer` into `RenderResources`; pixel parity | REN | REN-28 task 1 |
| B | 3 | Wind: `EnvironmentComponent` fields, `EnvironmentClockSystem`, `WindField` + `wind.glsl`, `ShaderFeature.WIND` | ECS (component, system review) + REN | 1 |
| B | 7 | Water: `WaterComponent` (incl. `cutsOcean`), `WaterWaves` + `water_waves.glsl`, `WaterMeshBuilder`, shared `ScreenErrorLod`, RHI `blit` depth mask (GL), `WaterPass` with refraction, absorption, IBL reflection, foam, shadows, fog, picking, water schema and presets | REN (+ ECS registers component) | 1, 3 |
| B | 8 | SSR for water, tied to the quality preset | REN | 7 |
| B | 9 | PHY-16: `BuoyancySystem`, `BuoyancyMath`, `RigidBody3D.buoyancy` (`AUTO`/`OFF`/`CONTROLLER`), `WaterEntered`/`WaterExited`, `WaterQueries.sample` and `submersion` (bounded water) | PHY | 7 |
| B | 10a | Editor: water and wind inspectors, gizmos, presets | EDT | 3, 7 |
| B | 13a | Showcase "Nature" area, part 1: pool, floating props, wind button; `docs/samples.md` | TLG + REN | 7, 9 |
| B | 14a | Vulkan parity for wind and water: depth blit, `WaterPass`, SSR through `GlslToVulkan` | VK | 7, 8 |
| B | 15 | Skater in water: bail + respawn as `PlayerController` policy (`waterResponse = BAIL`) on `WaterEntered` | GPL | 9 |
| B | 16 | Splash and water-ambience sounds on `WaterEntered` (optional) | AUD | 9 |
| C | 2 | Instancing core: `InstanceBatch`, `VAOLoader.instancedInput`, `VertexLayouts.INSTANCE_TRANSFORM`, `ShaderFeature.INSTANCED` in default/shadow/picking shaders, `ModelRenderer.drawInstanced`, `EngineStats` counters. First in phase C, because REN-28 task 7 (scatter) needs it | REN | 1 |
| D | — | (REN-28 streaming, tasks 14–27) | — | — |
| E | 4 | Vegetation: `FoliageType`, `GrassMeshBuilder`, `FoliageComponent` (`FLAT`), `FoliageCell`, `VegetationLod`, `VegetationPass`, shadows with the quality rule (§4.2), picking | REN (+ ECS registers component) | 2, 3, REN-28 task 7 (`ScatterPlacer`) |
| E | 5 | `.skfoliage` asset: save/load, GUID meta, hot reload, packing; foliage mask sidecars; bundled 2 CC0 trees + bush and water textures | AST | 4 |
| E | 6 | Terrain hookup: `ScatterRule.foliageType`, biome-driven vegetation, `FoliageComponent.TERRAIN`; foliage cells stream with REN-28 task 18 | REN | 4, REN-28 tasks 6, 7, 18 |
| E | 18 | Edit-mode physics queries: static and kinematic colliders present and raycastable while not playing, synced on transform/collider edits, no stepping (§4.6) | PHY | — |
| E | 17 | Foliage on meshes: `SurfaceProbe` (flat, heightfield, physics), `MESH` placement, budgeted bake, placement cache + surface signature, dirty tracking and rebake | REN | 4, 18 |
| E | 10b | Editor: foliage paint, `.skfoliage` inspector, `MESH` area gizmo, hit preview, Rebake | EDT | 4, 17, REN-28 task 10 (brush) |
| E | 11 | Impostor baker and far LOD | REN | 4, 5 |
| E | 13b | Showcase "Nature" area, part 2: grass, trees, bushes, foliage on meshes; terrain island vegetation; `docs/vegetation.md` | TLG + REN | 4, 6, 17 |
| E | 14b | Vulkan parity for vegetation: instance attributes 8–11, `VegetationPass`, wind shadows | VK | 4 |
| F | 19 | Ocean rendering: `OceanComponent`, `OceanMeshBuilder` clipmap, `ShaderFeature.OCEAN`, wave fade, horizon, `OceanMask` from the REN-28 overview, shoaling, `cutsOcean` (§6.7) | REN (+ ECS registers component) | 7, 8, REN-28 tasks 14, 20 (overview and its refresh) |
| F | 20 | Ocean physics: `WaterQueries` ocean source, `BuoyancySystem` broad phase for the ocean, events (§8.4) | PHY | 9, 19 |
| F | 21 | Ocean editor inspector, mask preview, *Big Island* sea and shoreline check, Vulkan parity of the ocean | EDT + TLG + VK | 19, 20, REN-28 task 22 |
| F | 12 | Underwater view for bounded water and the ocean: detection, surface from below, `PostEffect.UNDERWATER`; no caustics (§6.6) | REN | 7, 19 |
| Later | — | Planar reflections, only if pools look wrong with SSR + IBL | REN | 8 |
| Later | — | Grass interaction with the skater and wheels (up to 4 spheres) | REN | 4 |
| Later | — | Swimming: GPL swim states and controls, ANI swim animations, underwater follow camera, AUD underwater filter (§8.3) | GPL + ANI (+ AUD) | 9, 12, 20 |

Phase B needs no terrain. Only REN-28 task 1 (`lighting.glsl`, phase A) comes first. Bounded water keeps working
without an ocean, and the ocean works without terrain (`shoreMask = NONE`). It is scheduled last because its shoreline
and the *Big Island* check need REN-28's overview and streaming.

## 13. Open questions for the human

Rev 2's questions are answered in §14. Remaining questions from the ocean design:

1. **Inland basins below sea level:** should the ocean flood every terrain area below `seaLevel`, or only areas
   connected to the open sea?
   *Recommended:* only connected areas (the `OceanMask` flood fill, §6.7). A dry valley below sea level stays dry; a
   lake there is a bounded `WaterComponent`.
2. **Default far plane with an ocean or a REN-28 far field:** `CameraComponent.farPlane` defaults to 1000 m, so the
   ocean and the far field stop at 1 km unless fog hides the edge. Should scenes with an ocean or a streamed terrain
   default to a 4 km far plane?
   *Recommended:* yes. Set it automatically when an `OceanComponent` or a streamed terrain is added (undoable, with a
   note in the inspector). The far shoreline doesn't rely on depth precision (mask, §6.7).

## 14. Human decisions (2026-11-10, answers to rev 2 §13)

Recorded verbatim:

1. Reflections: SSR + IBL now; planar later only if pools look wrong.
2. Underwater: yes, a small later slice; no caustics.
3. Ocean: an INFINITE camera-following ocean TOO (besides bounded water): design it (far-field LOD, horizon,
   shoreline against REN-28 terrain and its streaming, buoyancy/volume queries for PHY-16), as its own task(s).
4. Skater in water: bail + respawn for now; in the future the skater must be able to SWIM, including underwater:
   record it as a later GPL/ANI direction (swim states, animations, controls, underwater camera) so nothing in this
   design prevents it.
5. Vegetation assets: yes, ship 2 CC0 low-poly trees and a bush; procedural grass.
6. Grass interaction: later follow-up.
7. Swaying tree shadows at Medium and above; cached rest-pose shadows at Low.
8. Foliage on arbitrary meshes: YES, NOW (raycast placement on meshes, physics queries in edit mode with
   skate-physics).
9. Order: decided by the lead: shared foundations (lighting.glsl, engine/procedural) -> water + wind -> REN-28
   terrain -> streaming -> vegetation -> infinite ocean last (it needs terrain and streaming). Make both notes' task
   lists consistent with that order.

Where each one landed:
1. §6.3, §1 non-goals; the "Later" planar row in §12.
2. §6.6; task 12 (phase F, after the ocean, so it covers both water kinds).
3. §1 goal 7, §3.5, §6.7, §8.4, §7, §11; tasks 19–21 (phase F).
4. §8.3 (bail as GPL policy now; swim hooks: `WaterQueries.sample`/`submersion`, `RigidBody3D.buoyancy = CONTROLLER`,
   per-camera underwater); task 15 and the "Later" swimming row.
5. §10; task 5.
6. §1 non-goals; the "Later" grass interaction row.
7. §4.2; tasks 4 and 14b.
8. §1 goal 8, §3.3, §4.6, §9, §10, §11; tasks 17 and 18 (PHY, edit-mode queries), 10b.
9. §12 here and REN-28 §12: both tables carry the same phases A–F.
