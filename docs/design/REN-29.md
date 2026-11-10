# REN-29: Vegetation and water (design)

Status: **proposal rev 2, awaiting approval** (rev 1 was rejected by the lead: not grounded in the code). Owner:
skate-render. Reviewers: skate-physics (§8, PHY-16), skate-assets (§10), skate-ecs (§3, §5.3), skate-vulkan (§12 task 14).
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

**Non-goals (for now)**
- GPU-driven culling or compute: the renderer is GL 3.3 core (`#version 330 core` in `shader_3d_default.glsl`), with
  no compute or SSBO. Culling stays on the CPU.
- FFT ocean, caustics, wakes or ripples from moving objects, waterfalls, spline rivers with banks.
- Infinite (camera-following) oceans: open question 3.
- Grass reacting to the skater: open question 6.
- Skeletal or bone-driven trees: wind is vertex-shader only.
- Swimming and character water locomotion: open question 4, GPL.

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
| `placement` | `FLAT` | `FLAT`: at the entity's Y. `TERRAIN`: snaps to the `HeightField` of the terrain below (REN-28 §3.2) and reads its normal. Raycasting onto meshes is open question 8. |
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

Presets in the inspector: *Calm pool*, *Lake*, *Ocean swell*, *River* (flow 1 m/s).

### 3.5 Runtime objects (render domain, not serialized)

- **`FoliageCell`** (**new**, `engine/render/vegetation/FoliageCell.kt`): a square cell (16 m by default, or REN-28
  terrain chunk size when on terrain) with an AABB and, per `FoliageType`, a packed instance array. Each instance is
  16 floats (64 B): a 3×4 world matrix (12), then `tint`, `random` (0..1, seeded), `windPhase` and `lodFade`.
- **`InstanceBatch`** (**new**, `engine/render/instancing/InstanceBatch.kt`): one mesh part × material × instance
  buffer, with a vertex input that combines the mesh's static buffers and the instance buffer. It is the "instance
  batch" REN-28 §7 refers to. **Whichever of REN-28 task 7 or this note's task 2 lands first creates it, and the
  other reuses it** (§12).
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
  `ShadowLayerCache` (`engine/render/shadow/ShadowLayerCache.kt`) hashes caster signatures. Wind-animated casters add
  the wind time to their signature, so cascades with swaying trees re-render every frame; static vegetation keeps the
  cache. Open question 7 covers freezing tree shadows at low quality.
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
  (REN-28 task 4); the water uses it if it lands first, otherwise a copy in `WaterLod`.
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
     `mask` parameter (COLOR, DEPTH) on the RHI and the GL backend; the Vulkan backend follows in task 14.
   - The copy avoids sampling the depth attachment being tested (a GL feedback loop).
   - MSAA is not a concern: `GlRenderTarget` uses single-sample textures (`resolve()` is a no-op).
   - New resource ids: `SceneColorCopy`, `SceneDepthCopy` in `RenderResourceIds` *(30g)*.
4. **Draw:** draw the water chunks with `water.glsl` (**new**), depth test on, **depth write on**, so the
   transparent and particle passes test against the surface. It is double-sided only when the camera is under the
   surface (open question 2).

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
  - **Planar reflections:** open question 1.
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
- **Underwater:** open question 2.

## 7. Performance budget (dev machine, measured with the REN-22 per-pass GPU timers)

| Item | Budget |
|---|---|
| `VegetationPass` (showcase patch, ~60k grass visible + 200 trees) | < 1.5 ms GPU, < 0.5 ms CPU |
| Vegetation shadows (trees, wind on) | < 1 ms GPU |
| `WaterPass` (one 20 × 20 m body, SSR High) | < 1 ms GPU, including the scene copy |
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

### 8.3 Characters

The skater is driven by `PlayerController` and `SkateboardController` (`game/skateboard/SkateboardController.kt`), not
by free buoyancy. What happens when the skater enters water is a GPL decision (open question 4). The
`WaterEntered` event and a `WaterQueries.surfaceAt(x, z)` helper (**new**, `engine/environment`) are the hooks.

## 9. Editor tools (editor domain, EDT tasks)

- **Foliage paint mode:**
  - It reuses the REN-28 §9 brush (radius, strength, falloff), extended to `FoliageComponent` areas.
  - Paint or erase the density mask per layer. Strokes are undoable with the same rectangle-diff command.
  - On terrain, the REN-28 scatter brush already covers per-rule masks.
- **`FoliageType` inspector** for `.skfoliage` assets: LOD list with screen sizes and a live LOD-colour debug view,
  wind profile with a preview, and a "Bake impostor" button after task 11.
- **Water inspector:** wave presets, a per-wave editor, material picker, a volume gizmo and a "Freeze time" toggle.
- **Environment inspector:** a wind section with presets. When the Environment is selected, a wind arrow is drawn in
  the viewport via `DebugPass`.

## 10. Assets and persistence (needs skate-assets review)

- **`.skfoliage`:** JSON, versioned, with a GUID `.meta` like other assets. It is saved under the current schema rules
  with no migrations (rule 8). Hot reload rebuilds the affected cells.
- **Packing:** `ReferenceCollector` (`engine/assets/pack/ReferenceCollector.kt` *(30g)*) follows LOD model GUIDs,
  the material override and the impostor atlas.
- **Foliage density masks:** `FoliageComponent` masks are a sidecar folder `<scene>.foliage/<entity guid>/<layer>.png`
  (R8), referenced by GUID. Masks on terrain stay in the `.skterrain` folder (REN-28 §10).
- **Water `.skmat`:** normal `.skmat` assets with schema id `"water"`. `MaterialAsset` already stores the schema id.
- **Bundled assets** (AST/TLG source them):
  - grass is generated (no asset);
  - 2 CC0 low-poly trees with 2 LODs each and the vertex-colour convention, plus a bush (open question 5);
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
- **GPU tests:**
  - With no vegetation or water, the task 1 split is pixel-identical to before.
  - Water refraction shows the pool floor.
  - Foam appears at an intersecting box.
  - Instanced draws match non-instanced ones for the same mesh and transform.
  - Timings are logged against §7.
- **Feature Showcase** (`game/samples/FeatureShowcase.kt`, an `Area` entry in `AREAS`, TLG owns the file): a new
  **"Nature"** area behind the back row, next to REN-28's "Terrain" area.
  - **Water:** a 10 × 6 m pool, 1.5 m deep, using `water_pool.skmat`, with steps into it (shore foam). Inside are
    floating crates of 3 densities, a ball and a log, which bob, settle and roll (PHY-16 "done when").
  - **Vegetation:** a grass patch with two trees and bushes from a `FoliageComponent`.
  - **Wind button:** an overlay button that cycles calm, breeze and storm, like the existing fog button.
  - When REN-28 is in, the Terrain island gets biome vegetation and a sea `WaterComponent` around it.
- **Acceptance**, from rev 1 and made concrete:
  1. The water reflects the sky (IBL) and nearby showcase objects (SSR), and refracts the pool floor.
  2. A dropped debug cube and crates float and settle in water.
  3. Trees switch LOD with distance without popping, and grass and trees sway visibly with wind.
  4. Swapping a `.skfoliage` or water `.skmat` changes the look with no code change.

## 12. Implementation tasks (after approval)

| # | Task | Domain | Deps |
|---|---|---|---|
| 1 | Split `GeometryPass` into opaque `GeometryPass` and `TransparentPass`; hoist `EnvironmentProbe`/`LightBuffer` into `RenderResources`; pixel parity | REN | REN-28 task 1 (`lighting.glsl`) |
| 2 | Instancing core: `InstanceBatch`, `VAOLoader.instancedInput`, `VertexLayouts.INSTANCE_TRANSFORM`, `ShaderFeature.INSTANCED` in default/shadow/picking shaders, `ModelRenderer.drawInstanced`, `EngineStats` counters (shared with REN-28 task 7) | REN | 1 |
| 3 | Wind: `EnvironmentComponent` fields, `EnvironmentClockSystem`, `WindField` + `wind.glsl`, `ShaderFeature.WIND`, shadow-cache signature | ECS (component, system review) + REN | 2 |
| 4 | Vegetation: `FoliageType`, `GrassMeshBuilder`, `FoliageComponent` (`FLAT`), `FoliageCell`, `VegetationLod`, `VegetationPass`, shadows, picking | REN (+ ECS registers component) | 2, 3, REN-28 task 2 (seeding; `ScatterPlacer` from REN-28 task 7, or extracted here if first) |
| 5 | `.skfoliage` asset: save/load, GUID meta, hot reload, packing; foliage mask sidecars; bundled trees and water textures | AST | 4 |
| 6 | Terrain hookup: `ScatterRule.foliageType`, biome-driven vegetation, `FoliageComponent.TERRAIN` placement | REN | 4, REN-28 tasks 6–7 |
| 7 | Water: `WaterComponent`, `WaterWaves` + `water_waves.glsl`, `WaterMeshBuilder`, RHI `blit` depth mask (GL), `WaterPass` with refraction, absorption, IBL reflection, foam, shadows, fog, picking, water schema and presets | REN (+ ECS registers component) | 1, 3 (clock) |
| 8 | SSR for water, tied to the quality preset | REN | 7 |
| 9 | PHY-16: `BuoyancySystem`, `BuoyancyMath`, `WaterEntered`/`WaterExited` events, `WaterQueries` | PHY | 7 |
| 10 | Editor: foliage paint, `.skfoliage` inspector, water and wind inspectors and gizmos, presets | EDT | 4, 7, REN-28 task 10 (brush) |
| 11 | Impostor baker and far LOD | REN | 4, 5 |
| 12 | Underwater view (only if open question 2 says yes) | REN | 7 |
| 13 | Showcase "Nature" area, wind button, docs (`docs/samples.md`, `docs/vegetation.md`) | TLG + REN | 4, 7, 9 |
| 14 | Vulkan parity: instance attributes 8–11, depth blit, water and vegetation passes through `GlslToVulkan` | VK | 4, 7, 8 |
| 15 | Skater in water (open question 4) | GPL | 9 |
| 16 | Splash and water-ambience sounds on `WaterEntered` (optional) | AUD | 9 |

Water and wind (tasks 1, 3, 7, 8, 9) don't need terrain. Only task 1's `lighting.glsl` prerequisite and tasks 4 and 6
touch REN-28 work.

## 13. Open questions for the human

1. **Reflections:** is screen-space reflection plus IBL enough, or do you want true planar reflections for mirror-flat
   pools? Planar re-renders the scene mirrored, which roughly doubles the geometry cost.
   *Recommended:* SSR + IBL now; add planar later as a per-water "High" option only if pools look wrong.
2. **Underwater:** does the camera need to go underwater (blue fog and tint, surface seen from below)?
   *Recommended:* yes, as a small later slice (task 12). No caustics.
3. **Ocean:** do we need an infinite, camera-following ocean, or are bounded water boxes enough (a large box can
   surround an island)?
   *Recommended:* bounded boxes only for now.
4. **Skater in water:** when the skater rides into water, should they bail and respawn at the last safe spot, or float
   or swim?
   *Recommended:* bail + respawn (GPL task 15); crates and props float.
5. **Vegetation assets:** may we ship 2 CC0 low-poly trees and a bush (e.g. Quaternius/Kenney), with grass generated
   procedurally?
   *Recommended:* yes.
6. **Grass interaction:** should grass bend away from the skater and wheels (up to 4 interaction spheres, a uniform
   array)?
   *Recommended:* later, as a small follow-up after task 4.
7. **Tree shadows in wind:** swaying shadows force cascades to re-render every frame. Should they animate at every
   quality level?
   *Recommended:* animate at Medium and above; at Low, keep rest-pose shadows (cached).
8. **Foliage on meshes:** besides flat areas and terrain, should `FoliageComponent` place plants on arbitrary meshes by
   raycasting down (needs physics queries in edit mode)?
   *Recommended:* not now; `FLAT` + `TERRAIN` cover the park and terrain.
9. **Order with REN-28:** if REN-28 approval or implementation is delayed, should water and wind (tasks 1, 3, 7–9)
   start first? Task 1 would then extract `lighting.glsl` itself.
   *Recommended:* yes.
