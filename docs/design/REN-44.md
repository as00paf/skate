# REN-44: Terrain surface types (sand, snow and others) — design

Status: **approved, rev 2** (human decisions 2026-11-18, §13). Rev 2 adds the skate-specific surfaces (§3.1) and
persistent, saved trails (§4.2).
Request (human, Discord 2026-11-17): plan sand and snow (and other surfaces) as part of `TerrainComponent`, next to
water: material layers, deformation or footprints/trails, physics response (friction, sinking, skate grip), audio, and
how biomes assign them.

Builds on REN-28 (terrain layers, biomes, splat, streaming), REN-29 (water, wind, `WaterQueries`), PHY-15/26
(heightfield colliders), AUD-01 (mixer, voices, 3D audio) and ANI footstep events (`AnimationEvents`).

## 1. Goals and non-goals

**Goals**
1. A **surface type** is one engine-wide definition (sand, snow, grass, gravel, mud, ice, rock, concrete, wood,
   metal, water) that every system reads the same way: rendering, physics response, the skater's grip, audio and
   particles.
2. Terrain assigns surfaces **through its material layers**, so biomes already decide where sand and snow are (the
   beach biome's sand layer, the peak's snow layer). No second painting pass.
3. Meshes (ramps, rails, the park floor) get a surface from their material, so the skater sounds and grips the same on
   a wooden ramp anywhere.
4. **Deformable surfaces** (sand, snow, mud) show trails and footprints around the player: wheel tracks, footprints,
   a bail crater. Cheap, bounded memory, independent of terrain size.
5. **Physics response** per surface: friction, rolling resistance, sinking, and skate grip (sand stops a board; snow
   and ice slide).
6. **Audio** per surface: footsteps, wheel roll loops, landings and slides.
7. Works with streaming (REN-37), on GL and Vulkan, in the editor and in exported games.

**Non-goals (now)**
- Fluid or granular simulation (sand piles, avalanches, snow accumulation physics).
- Weather that falls and accumulates in real time (a global snow-cover amount is proposed as an optional slice, Q3).
- Hardware tessellation (GL 3.3 has none); depth comes from vertex displacement on near chunks plus parallax (§4.3).

## 2. Overview

```
 SurfaceType (data, engine/terrain/surface)          TerrainLayer.surface ─┐   MaterialAsset.surface ─┐
 ├─ physics: friction, rolling, sink, grip           (per layer, biomes     │   (meshes, ramps, rails)  │
 ├─ deform: trail kind, depth, width, recovery        assign layers)        │                           │
 ├─ audio: footstep / roll / land / slide sets                              ▼                           ▼
 └─ fx: particle preset, wetness, sparkle            SurfaceQueries.surfaceAt(world x, z) / surfaceOf(hit)
                                                     ├─ terrain: dominant splat layer → its surface
                                                     ├─ mesh: the hit object's material surface
                                                     └─ water: WaterQueries above the ground → WATER
           ┌──────────────────────────────┬──────────────────────────────┬──────────────────────────┐
           ▼                              ▼                              ▼                          ▼
   PHY: SurfaceResponse            GPL: skater grip,              AUD: surface sound sets     REN: DeformationField
   (friction / rolling per         board stop on sand,            (footsteps from ANI          (camera-centred clipmap,
   contact, sink offset)           slides on snow/ice             events, wheel loops)         trails, footprints)
```

## 3. Data model

### 3.1 `SurfaceType` (**new**, pure: `engine/terrain/surface/SurfaceType.kt`)

Built-in surfaces live in a registry (`SurfaceTypes`), keyed by a stable id string. Projects can add or override
surfaces later as `.sksurface` assets (AST, optional slice, Q4).

| Field | Example (sand / snow) | Notes |
|---|---|---|
| `id` | `"sand"` / `"snow"` | Stable; stored in layers and materials. |
| `friction` | 0.55 / 0.12 | Coulomb friction for bodies and feet. |
| `rollingResistance` | 0.35 / 0.08 | Fraction of speed lost per second by wheels (skateboard, carts). |
| `sinkDepth` | 0.04 m / 0.12 m | How far feet and wheels visually sink, and how deep trails are. |
| `skateGrip` | 0.3 / 0.15 | Lateral grip multiplier for the board (1 = concrete). |
| `pushable` | false / false | Whether the skater can push off (false on deep snow: walk instead). |
| `deform` | `TRAIL` / `TRAIL` | `NONE`, `TRAIL` (wheels and feet leave grooves), `PRINT` (only footprints). |
| `trailRecovery` | 30 s / never | How fast trails fade (wind fills sand; snow stays until reset). |
| `audio` | `"surface.sand"` | Prefix of the sound sets: `.footstep`, `.roll`, `.land`, `.slide` (AUD). |
| `particles` | `"sand_puff"` / `"snow_spray"` | Particle preset for landings, turns and bails. |
| `wetness` / `sparkle` | 0 / 0.3 | Shading hints (wet sand darkening near water, snow glints). |

Built-ins (rev 1 list, human Q1: kept): `concrete` (default for meshes), `asphalt`, `wood`, `metal`, `grass`, `dirt`,
`gravel`, `sand`, `snow`, `ice`, `mud`, `rock`, `water`.

**Skate-specific built-ins (human Q1, rev 2; the human can veto any of them later):**

| id | Use | friction | rolling | skateGrip | pushable | deform | audio set | particles |
|---|---|---|---|---|---|---|---|---|
| `park_concrete` | Smooth skatepark concrete (bowls, ledges); the park floor default | 0.65 | 0.010 | 1.0 | yes | NONE | `surface.park_concrete` (smooth roll, crisp pops) | `concrete_dust` (faint) |
| `coping` | Metal coping on bowl and ramp lips: grinds | 0.25 | 0.015 | 0.8 | yes | NONE | `surface.coping` (grind screech, metallic tap) | `grind_sparks` |
| `plywood` | Plywood / Skatelite ramps and mini ramps | 0.55 | 0.015 | 0.95 | yes | NONE | `surface.plywood` (hollow roll, wooden thud) | none |
| `brick` | Brick plazas | 0.7 | 0.06 | 0.9 | yes | NONE | `surface.brick` (rumbly, bumpy roll with clacks per joint) | `brick_dust` (faint) |
| `tile` | Plaza or indoor tiles | 0.5 | 0.025 | 0.85 | yes | NONE | `surface.tile` (smooth roll, clicks on grout lines) | none |
| `carpet` | Indoor carpet (shops, home levels) | 0.9 | 0.45 | 0.6 | no (walk) | NONE | `surface.carpet` (muffled roll, soft steps) | none |

Values are first guesses for tuning in the Showcase "Surfaces" area (task 11); rolling resistance is the fraction of
speed lost per second, so `carpet` stops a board within a few metres and `park_concrete` barely slows it. Grind feel
on `coping` (balance, speed loss) stays GPL's trick system; the surface only gives its friction, sound and sparks.

### 3.2 Assignment

- **Terrain:** `TerrainLayer` gets `surface: String` (default from the layer's role: the default sand layer `"sand"`,
  grass `"grass"`, rock `"rock"`, snow `"snow"`). Biomes keep assigning layers by height and slope (REN-28 §6), so the
  beach is sand and the peaks are snow with no new data.
- **Surface at a point (terrain):** the **dominant layer** of the splat weights at that point, the same top-1 the
  shader blends towards, read from the chunk's splat (`TerrainChunkData.splat`). Height blending (stones poking through
  sand) is visual only; gameplay uses the dominant weight, so it never flickers between neighbours.
  - Optional hysteresis for audio and grip (a surface change needs 0.2 m of travel) avoids rapid switching on borders.
- **Meshes:** `MaterialAsset` gets an optional `surface` (default `concrete`); `RenderComponent` materials inherit it.
  A physics hit on a mesh reads the surface of the hit object's material.
- **Water:** above-ground water from `WaterQueries.sample` (REN-29 §8.4) wins: the surface is `water`, so footsteps
  splash in a shallow pool on sand.
- **Far away (streaming):** `surfaceAt` falls back to the macro layer weights of the overview (REN-28 §15.4) when the
  chunk isn't resident, so AI or audio far from the player still get an answer.

### 3.3 Queries (**new**, `engine/terrain/surface/SurfaceQueries.kt`, pure)

- `surfaceAt(worldX, worldZ): SurfaceType` — terrain (dominant layer) or water above it.
- `surfaceOf(hit: PhysicsHit): SurfaceType` — mesh material, terrain at the hit point, or water.
- `blendAt(x, z, out)` — the top-2 surfaces and their weights, for audio cross-fades at borders.
- Allocation-free; safe on the main thread and in `fixedUpdate`.

## 4. Deformation: trails and footprints

### 4.1 Approach: a camera-centred deformation clipmap

Per-chunk deformation maps would cost memory proportional to the terrain (a 64 m chunk at 5 cm is 1.6 MB). Instead
one **`DeformationField`** (**new**, `engine/render/terrain/deform/DeformationField.kt`) follows the active camera:

- An **RG16F texture**, 2048² texels over 102.4 m (5 cm per texel): R = depth (m), G = age (s). About 16 MB GPU.
- **Toroidal addressing:** as the camera moves, the field scrolls by whole texels and only the newly exposed strip is
  cleared, so moving never re-renders the whole texture.
- **Writers:** "stamps" (`DeformationStamp`: position, radius or capsule, depth, surface) are queued by gameplay
  (wheel contact points each fixed step, foot plants from ANI footstep events, a bail's body contacts) and drawn into
  the field with a small max-blend shader each frame (a few hundred quads at most).
- **Only deformable surfaces accept stamps:** the stamp shader multiplies by the deformability of the surface under
  each texel (from the splat), so a trail stops where sand turns to rock.
- **Recovery:** the field's age channel and the surface's `trailRecovery` fade depth over time (sand fills in; snow
  stays until the field scrolls away or a reset).

### 4.2 Persistent, saved trails (human Q2, rev 2)

Trails persist across leaving the area, chunk unloads, and save/load, using the REN-28 §15.10 pattern: only the
difference from the untouched ground is stored, per chunk, never evicted with the chunk, and saved as a section.

- **Source of truth on the CPU:** `TrailStore` (**new**, pure: `engine/terrain/surface/TrailStore.kt`), one per terrain:
  chunk → sparse **trail tiles** of 4 m × 4 m at 5 cm (80² texels, R8 depth in units of `sinkDepth / 255`, plus the
  game time of the tile's last stamp). Stamps are applied **on the CPU** (a few hundred small discs per frame, cheap)
  into tiles that exist only where something touched the ground. A trodden 100 m path is about 25 tiles (160 KB raw,
  much less deflated).
- **The GPU field is an upload cache:** the camera-centred `DeformationField` (§4.1) is filled from the store as it
  scrolls (newly exposed strips upload their tiles) and patched when a tile changes. Nothing is read back from the GPU,
  so saving never stalls rendering.
- **Decay option:** each surface's `trailRecovery` (seconds to fade fully; 0 = never). Snow defaults to never, sand to
  10 minutes of game time, mud to never. A decay pass runs over a few touched tiles per frame (round-robin, game time),
  re-uploading the ones in the field; tiles that reach zero are deleted. A scene-wide override
  `EnvironmentComponent.trailDecay` (`SURFACE` default, `NEVER`, `FAST` for arcade) lets a game choose.
- **Memory cap:** `trailBudgetMB` (default 16 MB, independent of quality like the scatter state). Past the cap, the
  **least recently stamped** tiles farthest from every focus are dropped first, with one log line; this is the only
  case where a trail disappears without decaying (trails are cosmetic, unlike scatter state, which never drops).
- **Saving:** a `SKTR` section per terrain in the save container (AST-41), next to the `SKSD` scatter section:
  header (magic, version, terrain id, world fingerprint), then per chunk its tiles (deflated R8 + last-stamp time).
  Restore happens before the spawn prefetch. A world change (reseed, recipe edit) clears the store, as for scatter.
- **Streaming and server:** the store is per terrain and independent of residency; the server keeps no trails
  (visual only), so they are not replicated (NET later, if ever wanted).
- **Snow cover reset:** raising the scene's snow cover (§7, slice 13) can optionally clear snow trails ("fresh
  snowfall"), a flag on the snow-cover change.

### 4.3 Rendering the depth

- **Fragment:** `terrain.glsl` samples the field when a fragment lies inside it: normal from the depth gradient,
  parallax offset of the layer UVs, darkening and wetness (wet sand inside tracks), snow sparkle reduced in tracks.
- **Vertex (deep snow and mud):** LOD 0 chunks inside the field displace vertices down by the field depth (vertex
  texture fetch, available in GL 3.3 and Vulkan). Chunks at 1 m spacing are too coarse for footprints, so depth comes
  mostly from parallax; for deep surfaces (snow > 5 cm) a finer **trail patch mesh** (0.25 m grid, 32 m square,
  following the camera) replaces the terrain mesh under the player. Q5 asks whether that extra mesh is wanted now.
- **Physics stays on the undeformed heightfield.** Sinking is applied by controllers (§5), not by moving colliders,
  so streaming, colliders and determinism (REN-28 §15.8) are untouched.

## 5. Physics response (PHY, with GPL for the skater)

- **Heightfield bodies (PHY):** a heightfield has one friction today (`HeightfieldCollider3D.FRICTION`). Two options
  (Q6):
  1. **Per-contact friction:** a contact callback reads `surfaceOf(contact)` and sets the combined friction (Bullet
     `ContactAddedCallback`, Jolt `ContactListener::OnContactAdded` with `ContactSettings.mCombinedFriction`; Simple:
     per contact). Exact, per-backend work.
  2. **Per-chunk average** friction from the chunk's dominant surfaces. Cheap, wrong at borders.
  - Decided (Q6): option 1 on Bullet and Jolt, a per-contact lookup in `SimplePhysics3D`.
- **Rolling resistance** for wheeled bodies (vehicles, carts) and the board: applied by `VehicleComponent` /
  board controller as a speed-proportional drag from `surfaceOf(wheel contact).rollingResistance`.
- **Sinking:** controllers lower the visual root (feet, wheels) by `sinkDepth` × deformation coverage. The collider
  is not changed.
- **Skater (GPL):**
  - Board on sand: high rolling resistance and low grip slow the board hard; below walking speed the skater steps off
    (no bail, human Q7), so sand is a soft boundary, not a trap.
  - Board on snow and ice: low friction and grip, so carving slides; pushing is disabled where `pushable` is false.
  - Walking: speed and step length scale by surface (deep snow slower).
  - Landing on sand or snow is softer (lower bail threshold for impact), a crater stamp is written.
- **Bodies (crates, balls):** friction from the per-contact lookup; balls stop quickly on sand (rolling resistance).

## 6. Audio (AUD)

- **Sound sets per surface** (`surface.<id>.footstep|roll|land|slide`), each a random-without-repeat set
  (`ClipPicker`), on the SFX bus with 3D positioning.
- **Footsteps:** ANI footstep events (`AnimationEvents`) carry the foot; AUD resolves `surfaceAt(foot)` and plays.
- **Wheel roll loop:** one voice per board, the clip cross-fades between the top-2 surfaces from `blendAt` and pitches
  with speed; silent in the air.
- **Landings and slides:** GPL publishes landing and slide events with the contact point; AUD picks the set.
- **Water:** existing water splash (AUD-01, REN-29 task 16) wins when `surfaceAt` is water.

## 7. Particles and shading details (REN)

- Particle presets (`sand_puff`, `snow_spray`, `dirt_kick`, `water_splash`) triggered by GPL on landings, sharp turns
  and bails at the contact point, tinted by the macro colour so sand dust matches the beach.
- Wet sand: a darkening band near water (thickness from `WaterQueries` depth) on the sand surface.
- Snow: sparkle (high-frequency specular glints) and slightly blue shadows, reduced inside trails.
- Optional global **snow cover** (Q3): `EnvironmentComponent.snowCover` (0..1) adds a snow layer weight on flat,
  high ground in the shader and in `surfaceAt`, so a scene can be snowed in without editing biomes.

## 8. Editor (EDT)

- Surface picker on terrain layers and on materials; the inspector shows the surface's physics and audio summary.
- **Surface debug view** (like the biome view, REN-28 §6): tints terrain and meshes by surface id.
- Play-mode toggle "Show deformation field" (outline and depth heatmap) and "Clear trails".

## 9. Performance and memory budget

| Item | Budget |
|---|---|
| `DeformationField` | 16 MB GPU (2048² RG16F); < 0.2 ms GPU per frame for tile uploads and scrolling |
| `TrailStore` | ≤ `trailBudgetMB` (16 MB) CPU; stamping < 0.1 ms CPU per frame; decay a few tiles per frame |
| Trail patch mesh (if Q5 yes) | 129² vertices, one draw; < 0.2 ms GPU |
| `surfaceAt` / `surfaceOf` | < 1 µs, no allocation (splat byte read) |
| Per-contact friction callbacks | < 0.1 ms per fixed step with 50 contacts |
| Audio | one roll voice per board, footsteps through the existing voice manager |

## 10. Tests

- **Unit (no GL):** surface registry and defaults; `surfaceAt` picks the dominant layer, water wins, the overview
  fallback works far away; `blendAt` weights sum to 1; border hysteresis; mesh material surface; deformation field
  toroidal scroll keeps stamps at the right world position and fills exposed strips from the store; decay by
  surface and the scene override; stamps ignore non-deformable surfaces; `TrailStore` round-trip of a `SKTR`
  section byte for byte; trails survive chunk unload and reload; the memory cap drops the least recently stamped,
  farthest tiles first and logs once; a world change clears the store.
- **PHY:** a box slides farther on ice than on sand; a ball rolling onto sand stops sooner; contact friction follows
  the splat at a sand/rock border; every backend.
- **GPL:** board speed decays on sand, carving slides on snow, no push on deep snow.
- **AUD:** footstep set by surface, roll loop cross-fade at a border.
- **GPU:** a trail renders under a moving camera with no seams at the field edge; GL/VK parity.

## 11. Implementation tasks (after approval)

| # | Task | Domain | Deps |
|---|---|---|---|
| 1 | `SurfaceType` + built-in registry, `TerrainLayer.surface`, `MaterialAsset.surface`, `SurfaceQueries` (terrain dominant layer, mesh, water, overview fallback, blend, hysteresis), surface debug view | REN (+ AST for the `MaterialAsset` field) | — |
| 2 | Per-contact friction and rolling resistance on Bullet, Jolt and Simple; body tests | PHY | 1 |
| 3 | Skater response: grip, rolling resistance, no-push surfaces, walking speed, soft landings, sinking offset | GPL | 1, 2 |
| 4 | Surface sound sets: footsteps from ANI events, wheel roll loop with border cross-fade, landing and slide sounds | AUD | 1 |
| 5 | `TrailStore` (CPU tiles, stamping, decay, memory cap, world-change clear) and `DeformationField` (clipmap, toroidal scroll, tile uploads), terrain shading (normal, parallax, wetness) | REN | 1 |
| 5b | `SKTR` save section and `TrailStateService` snapshot/restore (REN), stored in the save container | REN + AST (container, AST-41) | 5 |
| 6 | Stamp writers: wheel contacts, ANI foot plants, bail craters | GPL (+ ANI events) | 5 |
| 7 | Trail patch mesh for deep surfaces (if Q5) and vertex displacement on LOD 0 | REN | 5 |
| 8 | Particle presets and triggers (sand puff, snow spray, splash) | REN (presets) + GPL (triggers) | 1 |
| 9 | Editor: surface pickers, debug view toggle, deformation overlay, clear trails | EDT | 1, 5 |
| 10 | Vulkan parity: deformation field, stamp pass, trail patch | VK | 5, 7 |
| 11 | Showcase "Surfaces" area: a sand pit, a snow slope and an ice patch beside concrete, with a board to try them; `docs/samples.md` | TLG | 2–5 |
| 12 | Later: `.sksurface` project assets (human Q4: after the built-ins) | AST | 1 |
| 13 | Global snow cover (human Q3: yes): `EnvironmentComponent.snowCover`, shader weight, `surfaceAt`, optional trail reset | REN (+ ECS reviews the field) | 1 |

## 12. Open questions for the human (rev 1; answered in §13)

1. **Surface list:** are the proposed built-ins right (concrete, asphalt, wood, metal, grass, dirt, gravel, sand,
   snow, ice, mud, rock, water)? Any game-specific ones (e.g. a "skatepark coping" or "carpet")?
   *Recommended:* this list now; more later as data.
2. **Trail persistence:** should trails survive leaving the area and saving, or just fade (visual only)?
   *Recommended:* fade, visual only (bounded memory, no save data). Persistence would reuse the REN-28 §15.10
   per-chunk delta mechanism later.
3. **Global snow cover:** do you want a scene-wide "snowed in" amount (0..1) that whitens flat high ground without
   editing biomes?
   *Recommended:* yes, as a small optional slice (task 13), useful for seasons.
4. **Project surfaces:** should projects define their own surfaces as `.sksurface` assets, or is the built-in list
   (editable values) enough for now?
   *Recommended:* built-ins first; assets later (task 12).
5. **Deep footprints:** is a finer trail mesh under the player (real depth in deep snow, about 0.2 ms) wanted now, or is
   shading-only depth (normal and parallax) enough for the first version?
   *Recommended:* shading-only first, trail mesh as task 7 right after.
6. **Body friction on terrain:** per-contact friction (exact, per-backend work) or a per-chunk average (cheap, wrong
   at borders)?
   *Recommended:* per-contact.
7. **Skating on sand:** should the board stop and the skater step off automatically, bail, or keep skating very
   slowly?
   *Recommended:* slow down hard and step off below walking speed (no bail), so sand is a soft boundary, not a trap.

## 13. Human decisions (2026-11-18, answers to rev 1 §12)

Recorded:
1. CHANGED: add skate-specific built-in surfaces; propose a short list with grip, sound and particle values (suggested:
   skatepark concrete, coping, plywood ramp, brick, tile, carpet); the human can veto later.
2. CHANGED: trails PERSIST and are saved, using the REN-28 §15.10 per-chunk delta mechanism, with a memory cap and a
   decay option.
3. Yes: scene-wide snow cover as a small slice.
4. Built-ins first, `.sksurface` assets later.
5. Shading-only depth first, the trail mesh next.
6. Per-contact friction.
7. Sand slows hard; the skater steps off below walking speed; no bail.

Where each one landed:
1. §3.1 skate-specific table (`park_concrete`, `coping`, `plywood`, `brick`, `tile`, `carpet`).
2. §4.2 rewritten (CPU `TrailStore` as the source of truth, decay option, memory cap, `SKTR` section); §9, §10;
   tasks 5 and 5b.
3. Task 13.
4. Task 12 marked later.
5. Task 5 shading only, task 7 (trail mesh) right after.
6. §5 option 1; task 2.
7. §5 skater bullet; task 3.
