# Skate Engine — Domain Backlog

Skate is a 3D game engine and editor written in Kotlin (JVM 17). It uses LWJGL (OpenGL, GLFW, OpenAL, Assimp, stb), JOML, Dear ImGui, Bullet physics (libbulletjme) and a custom ECS. It started as an engine for skateboarding games but must work for **any** 3D game. The codebase is far along, but every area needs improvement and some systems need a full rewrite.

This file is the **single source of truth** for engine work. It is written for both humans and coding agents.
Each domain has a dedicated agent (see [Agents & herdr](#agents--herdr)).

---

## How to use this file

Done tasks live in [`docs/backlog-archive.md`](docs/backlog-archive.md). A `Deps` ID that is not in this file is done.

**Task format**

```
- [ ] **ID** `Priority` Title
  - Do: concrete steps
  - Done when: verifiable acceptance criteria
  - Deps: other task IDs (optional)
```

| Field | Meaning |
|-------|---------|
| `[ ]` / `[~]` / `[x]` / `[!]` | todo / in progress (append `@agent-name`) / done / blocked (append reason) |
| `P0` | Broken or blocking other work — do first |
| `P1` | Core capability needed for a usable engine |
| `P2` | Important improvement, not blocking |
| `P3` | Nice to have / future |

**Rules for agents**

1. Only work on tasks in **your domain** (the ID prefix). If you need a change in another domain, report it to `skate-lead` instead of making it. Exception: the lead may assign a task to another agent explicitly (`[~] @agent` on that task, e.g. EDT tasks spread across agents from round 16); the assignee owns it, follows the target domain's conventions, and the domain owner reviews.
2. Pick the highest-priority task whose `Deps` are all `[x]`. Before you start, read the code under the domain's paths.
3. Start every rewrite (`REWRITE` in the title) with a short design note in `docs/design/<task-id>.md`. Ask the lead for approval before you write code.
4. Respect the architecture guards in `src/test/kotlin/com/pafoid/skate/architecture/` (engine ↛ editor, physics via factory, UI mutations through `Command` + service layer).
5. Every task ships with unit tests (JUnit 5 + mockk). **No integration tests for now.** Name tests `Area_Behavior`.
6. Keep the build green: `cmd.exe /c gradlew.bat test` from WSL/herdr, or `.\gradlew.bat test` on Windows.
7. Only `skate-lead` edits checkbox state in this file. Domain agents report status back to the lead.
8. No backward compatibility while the engine is a work in progress: don't add migrations or upgrade-on-load code for old saved scenes/projects; the human recreates content. Instead, keep the **default projects** current: when a feature lands, update the default skate project (`PrefabsGenerator` default scenes) and the CLI samples to use it, and remove test-only content from them. The default editor project includes the **Feature Showcase** scene(s), which the human uses for manual testing: every new user-visible component or feature adds a small, labeled demo there in the same task.
9. **Feature Showcase upkeep** (human, Discord 2026-11-17): every task that adds or changes a user-visible feature puts its demo in the right scene (rule 12: `MainScene` if skateboarding-related, else the Feature Showcase) in the same task or names a `TLG` follow-up in its result; the lead adds that follow-up before marking the task done.
10. **Responsive editor** (human, Discord 2026-11-17): process-intensive editor work (project creation, imports, packing, scene loading, generation, baking) runs off the UI thread with coroutines and reports progress; only GPU uploads and ImGui calls stay on the main thread.
11. **Questions to the human** (human, 2026-11-17): agents never ask the human directly; they put questions in a design note or a `BLOCKED` line, and the lead asks them with the question tool, one decision per question with a recommended option.
12. **Human-testable content lives in the two scenes** (human, 2026-11-25): a new feature gets a labeled demo in the **default scenes** in the same task: `MainScene` if it is skateboarding-related (player, board, tricks, camera, skatepark, HUD), the **Feature Showcase** scene otherwise (`FeatureShowcase.populate`; sample `ShowcaseSeed.kts`). Whatever needs human testing (objects, components, scripts, UI, audio, particles, ...) is placed there ready to use, so the human never creates game objects or components by hand. The task's `docs/testing/<TASK-ID>.md` steps say which scene and object to look at, not how to build it. A task with no scene content cannot be marked `DONE` unless it states why none is needed.
13. **Minimal comments** (human, 2026-11-25): default to no comments. Never add narrating ("what") comments, section banners (`// --- ... ---`), boilerplate KDoc that restates names/types, commented-out code, or task-ID comments. Only add a comment when a non-obvious *why* (invariant, ordering constraint, or native/API quirk) cannot be conveyed through naming alone.

**PR size rules** (human, 2026-11-06/2026-11-07)

1. Keep PRs under **50 changed files** (aim for at most ~60 and **3–6 tasks**). Round 27 was 190 files — too many.
2. Anything larger is split across multiple tasks (one per task branch) or commits. The lead enforces this during integration: a branch over the limit is split before it is merged.
3. When a task spans many files, create sub-tasks with clear boundaries (e.g. "core logic", "editor integration", "export support"), each independently testable and reviewable.
4. Each agent's worktree branch contains only its domain's changes; cross-domain changes go to separate tasks.
5. For rewrites (`REWRITE`), the design note must be approved before any code is written, and the rewrite is split into **testable slices of ≤50 files each**. No slice spans more than two domains (e.g. engine + editor).
6. Integrate early and often: the lead opens a PR as soon as a coherent group of verified tasks is ready instead of batching a whole round, and splits a large round into **several PRs by area** (e.g. physics, editor windows, assets), **each based on the previous merged master**. Check the size with `git diff --stat origin/master...HEAD` before opening a PR.

---

## Stabilization phase (human decision 2026-11-25)

Engine stabilization comes before any new feature: no new P2/P3 feature work and no new slices of in-progress features until step 5 is done. Order: (1) finish EDT-108, then EDT-107; (2) QA-20 audit; (3) GPL-40 design, then rewrite; (4) bug and P0/P1 fixes (BUG-019/020/021, flaky tests, REN-47, REN-49, AST-49, EDT-114); (5) round 41b green and tested by the human, then features restart. Every engine feature must be represented and configurable in the editor.

## Ownership map

| Prefix | Domain | Agent | Primary paths |
|--------|--------|-------|---------------|
| `REN` | Rendering (renderer, RHI, materials, lighting, shadows, post-fx, particles, fog, textures) | `skate-render` | `engine/render/**`, `src/main/resources/shaders/**` |
| `VK` | Vulkan RHI backend for exported games (swapchain, frames in flight, descriptors, SPIR-V, pass parity) | `skate-vulkan` | `engine/render/rhi/vulkan/**` |
| `AST` | Asset management, serialization, export & packing | `skate-assets` | `engine/assets/**`, `engine/utils/Assets*.kt`, `engine/utils/ProjectExporter.kt` |
| `ECS` | Entity-component-system core | `skate-ecs` | `engine/ecs/**` (except physics/animation/audio/input systems) |
| `PHY` | Physics | `skate-physics` | `engine/physics3d/**`, `engine/ecs/systems/PhysicsSystem.kt`, `RagdollSystem.kt`, collider/rigid-body components |
| `ANI` | Animation | `skate-animation` | `engine/ecs/systems/AnimationSystem.kt`, `Animator.kt`, `Skeleton*`, `BoneOverride.kt`, `engine/assets/loaders/AnimationLoader.kt`, `BoneNameMapper.kt` |
| `AUD` | Audio | `skate-audio` | `engine/audio/**`, `AudioSystem.kt`, `AudioComponent.kt`, `SoundLoader.kt` |
| `INP` | Input | `skate-input` | `engine/input/**`, `InputSystem.kt`, `InputStateComponent.kt` |
| `GPL` | Gameplay: player/skate controllers, camera, prefabs | `skate-gameplay` | `game/**`, `PlayerController.kt`, `SkateboardController.kt`, `PlayerMotionSystem.kt`, `engine/assets/PrefabsGenerator.kt` |
| `GUI` | In-game (runtime) UI | `skate-gameui` | new `engine/ui/**` |
| `SCR` | Scripting (Kotlin Script, then TypeScript / Lua) | `skate-scripting` | `ScriptingSystem.kt`, `NativeScriptComponent.kt`, new `engine/scripting/**` |
| `NET` | Multiplayer & Steam | `skate-online` | new `engine/net/**`, new `engine/platform/steam/**` |
| `EDT` | Editor (ImGui, viewport, gizmos, UX, i18n, theming) | `skate-editor` | `editor/**`, `app/**`, `assets/strings/**` |
| `TLG` | Tooling: CLI, MCP, sample projects | `skate-tooling` | new `tools/cli/**`, new `tools/mcp/**`, sample projects |
| `QA` | Cross-cutting quality: bug triage, unit tests, standards | `skate-qa` | `src/test/**`, any file for fixes it is assigned |
| — | Planning, dispatch, integration, this file | `skate-lead` | `GameEngine.md`, `docs/**` |

Paths are relative to `src/main/kotlin/com/pafoid/skate/` unless they start at the repo root.

---

## Engine

### Rendering — `REN` (`skate-render`)

Round 27+ goals (human, 2026-11-06): post-processing quality, render-graph clarity, an industry-standard grid, environment lighting and reflections, then terrain, vegetation and water.

- [x] **REN-24** `P0` Post-processing blurs the frame; render-graph passes all named "Effect"
  - Do: Reproduce on master first. Find why enabling post-processing blurs everything (effect defaults, FXAA/bloom/DoF settings, intermediate target resolution or filtering, mip/sampling) and fix it. Give every pass/effect its real name in the render graph and the Render Graph window.
  - Done when: With post-processing on, a still frame is as sharp as with it off (apart from the intended effects, e.g. a GpuTest/edge-sharpness check). Every pass shows a distinct real name. Tests cover the naming and the cause of the blur.
- [x] **REN-25** `P1` Industry-standard editor grid
  - Do: Replace the thick grid with a thin, anti-aliased infinite grid as in Blender/Godot/Unity: minor/major lines that adapt to camera distance, distance fade, X axis red and Z axis blue, and no line thickening at grazing angles.
  - Done when: The grid looks like a standard editor grid at every distance and angle; the line-spacing/fade math has unit tests.
  - Deps: REN-24
- [x] **REN-26** `P1` Ambient light becomes an environment setting; default directional Sun
  - Do: Approved by the human: ambient light has no position. Move ambient color/intensity (or sky-based ambient) into the scene's Environment settings, remove the positional ambient component and its gizmo, and add a default "Sun" directional light, linked to day/night, to the default scenes. The editor draws the sun's direction arrow at the entity. Update the default projects and the showcase.
  - Done when: New scenes have Environment ambient + a Sun. No ambient gizmo at 0,0,0. Day/night drives the sun. Tests cover the environment settings and serialization.
  - Deps: REN-25
- [x] **REN-27** `P1` Environment reflections and a shiny metallic showcase object
  - Do: Add image-based lighting (prefiltered environment map + irradiance from the skybox, BRDF LUT) and/or reflection probes so metallic/smooth materials reflect their surroundings. Add a very shiny, reflective metallic object to the Feature Showcase Materials section.
  - Done when: The showcase metallic object clearly reflects the sky/scene; the IBL math/setup has unit tests; Vulkan parity follows in a VK task.
  - Deps: REN-26
- [x] **REN-28** `P2` Terrain and biomes: design note — approved by the human 2026-11-10 (`docs/design/REN-28.md` rev 3)
  - Do: Write `docs/design/REN-28.md`, reviewed by physics and assets: chunked heightmap terrain with LOD, splat materials (16 layers), a noise library (e.g. FastNoiseLite, MIT), biome maps driving materials and scatter, procedural or hand-painted (editor sculpt/paint tools), seeded through ECS-14, heightfield collider (PHY-15). Terrain must support **larger worlds** than the current park scale. Erosion filters are worth including. Split implementation tasks out after approval.
  - Done when: The human approves the note.
  - Deps: ECS-14
- [x] **REN-29** `P2` Vegetation and water: design note — approved by the human 2026-11-10 (`docs/design/REN-29.md` rev 3)
  - Do: Write `docs/design/REN-29.md`: instanced trees/grass with LOD and wind, a water surface shader (reflection/refraction, foam), water volumes for buoyancy (PHY-16), and the Feature Showcase demo area.
  - Done when: The human approves the note.
  - Deps: REN-27

- [x] **REN-30** `P1` Renderer honours overall quality, MSAA and texture quality
  - Do: From EDT-57: the Graphics settings for overall quality, MSAA and texture quality exist (`GraphicsSettings`) but the renderer ignores them. Apply them live (MSAA on the scene target, texture quality via mip bias/max size, quality preset driving shadows/post effects).
  - Done when: Changing each setting visibly changes rendering in the editor and exported game; tests.
- [x] **REN-33** `P1` Offscreen skinned-mesh preview viewport for the Animation Editor and Video to Animation
  - Do: From ANI-14 (skate-animation) and the human (Discord 2026-11-10: show the animation preview on the character): an offscreen viewport, used by the Animation Editor and the Video to Animation result, the Animation Editor feeds a bone pose to, drawing the skinned mesh instead of the skeleton lines.
  - Done when: The Animation Editor preview shows the skinned character following the scrubbed pose; tests for the viewport wiring.
  - Deps: ANI-14
- [x] **REN-34** `P2` World phase A: shared foundations (`docs/design/REN-28.md` §12, `docs/design/REN-29.md` §12)
  - Do: REN-28 tasks 1–2: extract `lighting.glsl` (GL/VK pixel parity), `engine/procedural` (FastNoiseLite, ECS-14 seeding). The lead dispatches the note's tasks one at a time, in the order of the tables.
  - Done when: Both tasks done with their tests; rendering unchanged.
  - Deps: REN-28, REN-29
- [x] **REN-35** `P2` World phase B: wind and water (`docs/design/REN-28.md` §12, `docs/design/REN-29.md` §12)
  - Do: REN-29 tasks 1, 3, 7, 8, 9 (PHY-16), 10a (EDT), 13a (TLG), 14a (VK), 15 (GPL: bail + respawn), 16 (AUD, optional), with the owners in the table.
  - Done when: The Showcase Nature area part 1 (pool, floating props, wind) works on GL and VK; tests per task.
  - Result (2026-11-17): REN-29 tasks 1, 3, 7, 8 done (TransparentPass, wind, WaterPass, SSR). The other owners' tasks continue as PHY-16 (task 9), EDT-90 (10a), TLG-17 (13a), VK-08 (14a), GPL-32 (15), AUD-12 (16); TLG-17 carries the Showcase acceptance.
  - Deps: REN-34
- [x] **REN-36** `P2` World phase C: instancing and terrain (`docs/design/REN-28.md` §12, `docs/design/REN-29.md` §12)
  - Do: REN-29 task 2 (instancing core) first, then REN-28 tasks 3–13 (terrain data, chunk meshes, splat, biomes, scatter, `.skterrain` (AST), heightfield collider (PHY-15), stamps, editor tools (EDT), showcase Terrain area (TLG)).
  - Done when: A generated, sculptable terrain with biomes, scatter and stamps plays in the editor and an export; tests per task.
  - Progress (2026-11-17): tasks 2 (instancing), 3 (terrain data), 4 and 13 done. REN tasks 5, 6, 7 and the REN part of 11 remain. Other owners: PHY-26 (task 9), AST-36 (task 8), EDT-92 (task 10), TLG-18 (task 11), ECS-17 (review + registration), VK-09 (Vulkan parity).
  - Result (2026-11-17): REN tasks 2–7, 13 and the REN part of 11 done. Other owners: PHY-26 (done), AST-36 (done) + AST-38, ECS-17 (done) + ECS-19, PHY-27, EDT-92, TLG-18, VK-09 (done) + VK-10; review fixes REN-42.
  - Deps: REN-34
- [ ] **REN-40** `P3` REN-29 design note: buoyancy drag formula (PHY-16)
  - Do: (NEEDS from PHY-16): update `docs/design/REN-29.md` §8.2 so drag reads −linearDrag · ρ · V_submerged · (v + ω × r − flow), applied per sample, instead of −linearDrag · m · f · (v − flow) (reasons in the `BuoyancySystem` KDoc).
  - Done when: The note matches the implementation.
  - Deps: PHY-16
- [ ] **REN-41** `P3` WaterPass honours `useFbo`
  - Do: (NEEDS from VK-08): `WaterPass` always calls `beginPass(frameBuffer.target)`, so with `useFbo = false` the water would draw into a target that is never presented. Latent: nothing passes `useFbo = false` today.
  - Done when: Water draws to the right target in both modes; test.
  - Deps: REN-35
- [x] **REN-42** `P1` TerrainComponent and TerrainSystem review fixes (ECS-17 review)
  - Do: (REVIEW by skate-ecs, 2026-11-17): (1) cap total samples (16384 m at 0.5 m is about 4.3 GB); (2) debounce the full main-thread rebuild on every inspector drag frame; (3) `key()` writes `seedName`, so load-then-save changes the file (EDT-60), and an unattached call fixes it as "terrain/Terrain"; (4) a NaN or zero size throws in `TerrainLayout` every frame with no catch in `SystemManager`; (5) a duplicate keeps `seedName` (identical ground), `MAX_LAYERS` is not enforced, a disabled terrain keeps its chunks.
  - Done when: Each finding is fixed or answered with a reason; tests for 1, 3 and 4.
  - Deps: REN-36
- [x] **REN-43** `P2` Grey rock material for the placeholder scatter rocks
  - Do: (human, 2026-11-17): the default "rocks" and "boulders" scatter rules use `engine:Models/sphere.obj` with no material, so they render as plain white spheres. Give them a grey rock material (engine default `.skmat`, packed) until real CC0 rock models are bundled.
  - Done when: Default terrain scatter renders as grey rocks in the editor, Play and an export; tests.
  - Deps: REN-36
- [~] @skate-render **REN-44** `P2` Terrain surface types: sand and snow
  - Do: (human, Discord 2026-11-17): plan sand and snow (and other surfaces) as part of TerrainComponent, next to water: material layers, deformation or footprints/trails, physics response (friction, sinking, skate grip), audio, and how biomes assign them. Design note `docs/design/REN-44.md` with sliced tasks across REN, PHY and AUD; ask for approval before code.
  - Done when: The note is approved and its slices are in the backlog.
  - Decisions (human, 2026-11-18): Q1 CHANGED: add skate-specific surfaces to the built-ins (render proposes: skatepark concrete, coping, plywood ramp, brick, tile, carpet); Q2 CHANGED: trails persist and are saved (per-chunk deltas, REN-28 §15.10); Q3 scene-wide snow cover as a small slice; Q4 built-in surfaces first, `.sksurface` assets later; Q5 shading-only depth first, trail mesh next; Q6 per-contact friction; Q7 sand slows hard and the skater steps off below walking speed (no bail).
  - Deps: REN-36
- [ ] **REN-45** `P1` Post-processing panel
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): a post-processing panel; design note first: where the values live (per scene on `EnvironmentComponent` or a new scene component, or per project) and how they combine with the quality preset.
  - Done when: The note is approved; every post effect is editable with undo; tests.
  - Decision (human, 2026-11-18): project defaults with a per-scene override.
  - Deps: none
- [ ] **REN-46** `P2` Sky / HDRI picker
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): a picker for `EnvironmentComponent.skyTexture`.
  - Done when: The sky can be picked or dropped, with undo; test.
  - Deps: EDT-108
- [x] **REN-47** `P0` Change the model of an existing RenderComponent
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): picker or drop to change the model, keeping material overrides where slot names match.
  - Done when: The model changes with undo and keeps matching overrides; test.
  - Deps: EDT-108
- [ ] **REN-48** `P2` `.skmat` file inspector
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): select a material in the Project window to edit it (a `FileInspector`).
  - Done when: A material file can be edited and saved from the inspector; test.
  - Deps: none
- [x] **REN-49** `P1` Flaky `TerrainStreamingRenderTest.FarField_DrawsTilesWhereNoChunkIsUploaded`
  - Do: (lead, round 39 integration, 2026-11-18): the test failed once in a full suite run and passes alone (3/3). Find the timing or shared-state dependency (async uploads, frame budget, a static streamer) and make it deterministic.
  - Done when: 20 full-suite runs pass it; or it is listed in `docs/bugs.md` with the cause.
  - Deps: REN-37
- [x] **REN-37** `P2` World phase D: streaming and remembered scatter state (`docs/design/REN-28.md` §12, `docs/design/REN-29.md` §12)
  - Do: REN-28 tasks 14–27: far field, Vulkan parity, streaming core and focus, suspended bodies (PHY), streamed physics and scatter, random-access pack reads (AST), sparse edits, Play/export prefetch (GUI), *Big Island*, component state capture (ECS), scatter state deltas with optional per-rule respawn, save-game container, stateful props check.
  - Done when: *Big Island* streams within the budgets; destroyed or taken props stay that way across unload, save and load; tests per task.
  - Progress (2026-11-17): checkpoint with tasks 14, 15 and the REN part of 16 done. Remaining REN: 17 (REN part), 18, 20 (REN part), 21–23, 25 and 27 (REN parts). Other owners: PHY-28 (17), AST-40 (19), AST-41 (25), VK-11 (12), GUI-19 (21), TLG-20 (22, 27), EDT-95 (20).
  - Result (2026-11-18): REN parts of tasks 14–18, 20–23, 25 and 27 done. Phase acceptance continues in PHY-28, AST-40, AST-41, GUI-19, VK-11, EDT-95 and TLG-20 (which carries it).
  - Deps: REN-36
- [ ] **REN-38** `P3` World phase E: vegetation (`docs/design/REN-28.md` §12, `docs/design/REN-29.md` §12)
  - Do: REN-29 tasks 4, 5, 6, 18 (PHY: edit-mode physics queries), 17 (foliage on meshes), 10b (EDT), 11, 13b (TLG), 14b (VK).
  - Done when: Grass, trees and bushes on flat areas, terrain and meshes, streamed with the terrain; tests per task.
  - Deps: REN-37
- [ ] **REN-39** `P3` World phase F: infinite ocean and underwater (`docs/design/REN-28.md` §12, `docs/design/REN-29.md` §12)
  - Do: REN-29 tasks 19, 20 (PHY), 21 (EDT + TLG + VK), 12 (underwater view, no caustics); ocean floods only areas connected to the sea; draw distance auto-set to 4 km with an ocean or streamed terrain.
  - Done when: *Big Island* has a sea with a correct shoreline; floating and underwater views work; tests per task.
  - Deps: REN-37
- [ ] **REN-32** `P3` Skinned child meshes, per-part visibility, skin texture compositing (GPL-24)
  - Do: Per (`docs/design/GPL-24.md` §8): (1) skin a child mesh with the nearest ancestor's `SkeletonComponent` in the Geometry, Shadow and Picking passes; (2) per-part visibility and per-material overrides; (3) composite tattoo layers into a per-character texture on request. Vulkan parity with skate-vulkan.
  - Done when: A skinned jacket follows the skater, hidden parts don't render, a composited tattoo shows; tests.
  - Deps: GPL-24, AST-33

### Vulkan — `VK` (`skate-vulkan`)

Goal (human, after REN-13): full Vulkan support for **exported games**, chosen per project at creation (OpenGL stays the default; the editor always uses OpenGL). Builds on the REN-12 RHI and the REN-13 spike (`docs/design/REN-13.md`). `skate-render` helps with explicitly assigned VK tasks.

**Deferred indefinitely** (human, 2026-11-09): the human will not install the LunarG Vulkan SDK in the near future. VK-01..06 are closed on their merged code and parity tests; the validation proof (VK-07) is parked as a P3 and is not scheduled. No agent works on it until the human reopens it.

- [x] **VK-01** `P1` Windowed Vulkan runtime: surface, swapchain, frames in flight — code merged (round 23); validation proof deferred to VK-07
  - Do: Turn the offscreen spike into a presenting backend for the game runtime: GLFW no-API window, surface, swapchain (create/recreate on resize and minimize, present modes for vsync), frames in flight (fences/semaphores, per-frame command buffers and transient resources, deferred destruction), backbuffer as the RHI default target, validation layers in debug/test runs.
  - Done when: A test harness window presents frames with Vulkan, survives resize/minimize, zero validation errors; frame-in-flight bookkeeping unit-tested.
  - Deps: REN-13
- [x] **VK-02** `P1` Vulkan resources: memory, uploads, descriptors, samplers — code merged (round 23); validation proof deferred to VK-07
  - Do: Memory allocator (e.g. lwjgl-vma), staging uploads for buffers/textures (mips, arrays, cube maps, depth/compare formats), sampler objects, descriptor set layouts/pools derived from SPIR-V reflection, and the `ShaderParams` named-uniform mapping to per-draw UBO data (ring buffer per frame in flight).
  - Done when: Every `RhiDevice.create*` used by the passes works on Vulkan; reflection/layout/ring logic unit-tested; zero validation errors.
  - Deps: REN-13
- [x] **VK-03** `P1` Geometry pass parity on Vulkan — code merged (round 24); validation proof deferred to VK-07
  - Do: Port the geometry pass with materials (PBR inputs, alpha modes), UBO lights and culling, skinning, sky dome/skybox and fog to Vulkan.
  - Done when: MainScene's lit, textured, skinned frame matches OpenGL within a documented tolerance (GpuTest); zero validation errors.
  - Deps: VK-01, VK-02
- [x] **VK-04** `P2` Shadows on Vulkan — code merged (round 25); validation proof deferred to VK-07
  - Do: Cascaded sun shadows and spot/point shadow maps (depth arrays, compare samplers, depth bias/clamp) with the backend depth-range fix-up; REN-22 caching behaviour preserved.
  - Done when: Showcase shadow area matches OpenGL within tolerance at each quality level (GpuTest).
  - Deps: VK-03
- [x] **VK-05** `P2` Post-processing on Vulkan — code merged (round 25); validation proof deferred to VK-07
  - Do: PostChain ping-pong, blits, bloom/FXAA/tonemap/gamma with a documented texture-orientation (Y-flip) convention.
  - Done when: Showcase post-effects area matches OpenGL within tolerance (GpuTest).
  - Deps: VK-03
- [x] **VK-06** `P2` Runtime UI, particles and debug lines on Vulkan — code merged (round 25); validation proof deferred to VK-07
  - Do: UiPass (batches, SDF text, scissor, blending), instanced particles and debug lines on Vulkan.
  - Done when: Showcase UI/particles areas match OpenGL within tolerance (GpuTest).
  - Deps: VK-03
- [ ] **VK-07** `P3` Zero-validation-error proof for the Vulkan backend — deferred indefinitely (2026-11-09): needs the official LunarG Vulkan SDK, which the human will not install for now (do not load downloaded nightly layers)
  - Do: With the SDK's Khronos validation layer enabled, run every Vulkan GpuTest (VK-01..06 harnesses, QA-16 parity suite, exported Vulkan game) and fix every validation message; then close VK-01..06.
  - Done when: All Vulkan GpuTests pass with validation on and zero messages; VK-01..06 marked done.
  - Deps: QA-16
- [x] **VK-08** `P2` Vulkan parity for world phase B (REN-29 task 14a)
  - Do: (NEEDS from REN-35): depth copy in `VkCommands.blit` (`BlitMask.DEPTH`, colour only today), WaterPass (`water.glsl` with `uWaveA0..3`/`uWaveB0..3` vec4 uniforms, texture units 10–14), SSR, WIND shader variants and the new TransparentPass on Vulkan.
  - Done when: Water, wind and transparent meshes render on Vulkan at parity with GL; GPU parity tests.
  - Deps: REN-35
- [x] **VK-09** `P2` Vulkan parity for instancing and terrain (REN-36)
  - Do: (NEEDS from REN-36): the INSTANCED vertex stream (locations 10–13), TerrainPass and `terrain.glsl` (PICKING and DEPTH_ONLY variants) on Vulkan; they translate already but have never been rendered.
  - Done when: Instanced meshes and terrain render on Vulkan at parity with GL; GPU parity tests.
  - Deps: VK-08 (needs REN-36 tasks 3–4 (checkpoint on agents/render, integrated in round 35))
- [x] **VK-10** `P2` Vulkan parity for terrain splat shading and instanced scatter (REN-36 tasks 5–7)
  - Do: (NEEDS from REN-36): terrain splat shading on Vulkan (sampler2DArray units 0–4, the stream at location 1 is now vec4, `uBiomeColor0..7` as separate uniforms) and instanced scatter, including the instanced shadow pipeline.
  - Done when: Splat-shaded terrain and scatter render on Vulkan at parity with GL; GPU parity tests.
  - Deps: VK-09, REN-36
- [ ] **VK-11** `P2` Vulkan parity for the streamed far field (REN-37 task 12)
  - Do: (NEEDS from REN-37): far field (`uFarTile`, `uMacro` on unit 0, `uMacroArea`) and the streamed upload path on Vulkan.
  - Done when: A streamed terrain with far field renders on Vulkan at parity with GL; GPU parity tests.
  - Deps: VK-10 (needs the REN-37 checkpoint, round 38)

### Asset management — `AST` (`skate-assets`)

Round 27+ goals: the Asset Browser shows every asset type, one assets folder, export settings, an asset store, OSM city import, Blender support, and protobuf where it fits.

- [x] **AST-13** `P0` Asset Browser misses animations, textures and sounds; prefab thumbnails lack materials
  - Do: Reproduce on master first. The Asset Browser (`editor/ui/windows/assetBrowser/**`, lead-assigned to assets) lists animations, textures and sounds with suitable thumbnails/icons. Obstacle and generic prefab thumbnails render with their default materials instead of untextured.
  - Done when: All asset types appear in a new project; prefab thumbnails show materials; tests cover the type discovery and the thumbnail material resolution.
- [x] **AST-14** `P1` One assets folder in the default project
  - Do: Also (from AST-13, lead-assigned editor change): add `jpg` to `FileType.TEXTURE` in `editor/data/FileType.kt` and drop the Asset Browser workaround. The default project hierarchy has two assets folders. Keep one, update project creation, export, path resolution and the default projects/CLI samples.
  - Done when: A new project has a single assets folder, everything still loads and exports; tests cover the layout.
  - Deps: AST-13
- [x] **AST-15** `P2` Export UX and export settings: design note — approved by the human 2026-11-10 (`docs/design/AST-15.md` rev 2)
  - Do: Write `docs/design/AST-15.md`: refined export wizard flow plus settings (splash image, icon, target platform, graphics backend, compression/packing, debug vs release, target FPS).
  - Done when: The human approves the note.
  - Deps: EDT-59
- [~] @skate-online **AST-16** `P2` Asset store window — design approved 2026-11-06 (`docs/design/AST-16.md`, revised after the skate-assets review) — slices a, b, d (Kenney/Quaternius) and AST-25 merged in round 30; e (SimReady/USD) pending
  - Do: Human decisions: **no login, no credential management.** Slices in order: a) SPI, registry, HTTP seam, pipeline, window skeleton + Poly Haven, b) ambientCG + texture sets, c) Kenney/Quaternius catalogs + "Import downloaded pack" (manual download is fine), d) Poly Haven as the primary provider, e) SimReady via Hugging Face + USD→glTF through Blender (needs AST-19). **No Sketchfab** (requires login). **No license tracking** for now — skip attribution-ledger/credits work. If an asset is in an unsupported format, warn the user on import rather than blocking.
  - Done when: Each slice imports a real asset; tests on fixtures, no sockets.
- [x] **AST-17** `P2` City generation from OpenStreetMap: design note — approved by the human 2026-11-10 (`docs/design/AST-17.md` rev 3)
  - Do: Write `docs/design/AST-17.md` evaluating OSM2World (Java, open source, glTF export) as the open-source alternative to commercial OSM city tools: area picker, import to a scene with colliders and materials, licensing (ODbL attribution).
  - Done when: The human approves the note.
- [x] **AST-18** `P3` Protocol Buffers: design note — approved by the human 2026-11-10 (`docs/design/AST-18.md` rev 2)
  - Do: Write `docs/design/AST-18.md`. Proposed: protobuf only for runtime data (packed exports, network messages). Scenes, prefabs and settings stay JSON so git diffs/merges (EDT-60) stay readable. Measure load time/size gains first.
  - Done when: The human approves the note.
- [x] **AST-19** `P2` Full Blender support — design approved 2026-11-06 (`docs/design/AST-19.md`)
  - Do: Implement the note. Human decisions: commit the generated GLBs; minimum Blender **4.2 LTS**; an action deleted in Blender keeps its generated clip and warns; "Send to Skate" add-on later. Lead: action mode default `FILES`. Blender is installed on the dev machine; test file `assets/obj/baseball-cap.blend`.
  - Done when: Dropping `baseball-cap.blend` into a project's Assets gives a textured model that re-imports when the .blend is saved; GUIDs survive; tests.
- [x] **AST-20** `P2` Classify FBX files as model or animation by content
  - Do: From AST-16: `.fbx` is always classed as `FileType.ANIMATION`, so FBX models show up in the Animations tab and store packs with FBX models cannot be imported as models. Inspect the file (meshes vs. animations only) and classify it accordingly; a file with both counts as a model with clips.
  - Done when: FBX models appear as models, animation-only FBX as animations; tests with fixture files.

- [x] **AST-21** `P2` Recognise `.skanim` clips in the editor and exports
  - Do: From ANI-13 (lead-assigned to skate-animation, AST/EDT review): add `skanim` to `FileType.ANIMATION` (Asset Browser, search), register it in `AssetMeta`/`AssetPaths` and in export packing.
  - Done when: A `.skanim` shows in the Animations tab, can be assigned, and is packed in an export; tests.
  - Deps: ANI-13

- [x] **AST-22** `P1` Model texture paths relative to the model file; packing follows glTF URIs
  - Do: From the AST-16a review: `AssimpLoader` resolves texture URIs against the working directory, so a glTF with textures in a subfolder (e.g. Poly Haven `textures/`) loads untextured. Resolve URIs relative to the model file and through `AssetPaths`/the pack; make `ReferenceCollector` follow glTF `buffers[].uri`/`images[].uri`; have the Asset Browser rescan on `FileSystemChangedEvent`.
  - Done when: An imported Poly Haven glTF renders textured in the editor and in an exported game; tests load and pack such a model.

- [x] **AST-23** `P0` Asset Browser tab switch blocks the UI: load assets off the main thread
  - Do: Found by the human testing PR #29. Discovery and thumbnail loading in the Asset Browser tabs run on the UI thread. Move file discovery and image decoding to `JobSystem` IO with placeholders, upload textures on main in small batches, cache per tab.
  - Done when: Switching tabs never stalls a frame noticeably on the default project; tests on the async discovery/cache logic.


- [x] **AST-24** `P2` OBJ loader regenerates normals when a mesh has none
  - Do: From REN-27: Assimp's OBJ importer sends all-zero normals when the file has none, so smooth-normal generation is skipped. Detect it and regenerate normals.
  - Done when: A normal-less OBJ renders lit correctly; test.
- [x] **AST-25** `P2` Deleting a store asset updates the attribution ledger
  - Do: From AST-16a review item 11: `DeleteFileCommand` calls `AttributionLedger.removeAssetIds`, so credits stay correct.
  - Done when: Deleting an imported store asset removes it from the ledger and credits; undo restores it; tests.
- [x] **AST-26** `P1` REWRITE Typed asset folders: design note — approved by the human 2026-11-10 (`docs/design/AST-26.md` rev 2)
  - Do: From the human (2026-11-10): `Assets/` holds one folder per type: `Models`, `Shaders`, `Sounds`, `Textures`, `Materials`, `Scripts`, `Animations`; Store imports go to their type folder instead of `Assets/Store`. New-project option to copy the engine defaults into the project or not. Human decisions 2026-11-10: **no `EngineDefaults` folder anywhere** — copied engine defaults go straight into `Assets/<Type>/`; a store model stays **together** in `Assets/Models/<asset>/` (`.gltf`, `.bin`, `textures/`), texture-only and material downloads go to `Textures/` and `Materials/`. Design note `docs/design/AST-26.md` covering: how not-copied engine defaults resolve in the editor (reference scheme) and are packed on export, telling engine files apart once copied (updates, name clashes), multi-file assets (texture sets, `.blend` imports) staying loadable under AST-22 path rules, the type a file lands in (FBX via AST-20), store `.meta`/attribution, **no migration or old-layout support** (human, 2026-11-10: new projects only), and changes to samples, CLI, `skate validate` and export. Reviewed by skate-online (store), skate-editor (Asset Browser), skate-tooling (wizard, samples).
  - Done when: The human approves the note.
  - Deps: AST-14
- [x] **AST-27** `P1` Typed asset folders: layout and engine defaults
  - Do: Implement AST-26 (rev 2 decisions §0): project creation, typed layout (Prefabs, Fonts, Data, and Items for GPL-24 too), editor strings/themes/help moved to `src/main/resources/editor/`, engine repo `assets/` reorganised into typed folders (delete the Font Awesome desktop zip), pack dedupe by content hash, copy/no-copy resolution and export packing, path resolution, samples and CLI. No migration of existing projects.
  - Done when: New projects use the typed layout, open, play and export in both copy modes; tests cover the layout.
  - Deps: AST-26
- [ ] **AST-28** `P1` Asset store imports go to their type folder (owner: skate-online)
  - Do: Per AST-26: Poly Haven, ambientCG and Kenney/Quaternius imports land in `Models`/`Textures`/`Materials`/`Sounds`… instead of `Assets/Store`; undo, re-import in place, the attribution ledger and `.meta` store keys keep working.
  - Done when: Each provider's import lands in the right type folders and loads textured; tests on fixtures.
  - Deps: AST-27, AST-16
- [x] **REN-31** `P1` Project shaders: engine-default/override resolution and hot reload (AST-26 §5)
  - Do: Per AST-26 §5 (reviewed by skate-render): shaders resolve through `engine:Shaders/…` with project overrides in `Assets/Shaders/`, `@skate-contract` headers, includes, caches, hot reload of project shaders; basic shaders always copied; unedited copies follow the engine on project open (human, 2026-11-10); custom material shaders in the colour pass only for v1.
  - Done when: Editing a project shader changes rendering live; an incompatible edited override falls back with an error; tests.
  - Deps: AST-27
- [x] **GPL-26** `P1` Skater configs in the project assets, editable in the editor
  - Do: From AST-26 NQ2 (human, 2026-11-10: follow-up): `skater.character.json` and `skater.controller.json` become `engine:Data/skater.character.json` and `engine:Animations/skater.controller.json`, overridable per project like other engine defaults (with skate-animation). Human (Discord 2026-11-10): they live in the project's assets and are editable by users in the editor (panels, not hand-edited JSON).
  - Done when: A project override changes the skater; without one the engine config is used; tests.
  - Deps: AST-27
- [x] **AST-29** `P2` Export settings and export dialog (AST-15)
  - Do: Implement `docs/design/AST-15.md` rev 2: per-project export settings, the export dialog, icon mandatory with engine default, engine splash 2 s by default (or None/custom), gzip on/off, target FPS, debug/release; Windows only; `run-game.bat` stays.
  - Done when: Each setting persists and changes the exported game; warnings for missing icon/splash; tests.
  - Result (2026-11-17): export side done (settings, icon/splash fallback with warnings, Release/Debug launcher, frame cap, remembered options); the dialog UI continues as EDT-91.
  - Deps: AST-15, AST-27
- [ ] **AST-30** `P3` Linux and macOS exports: per-platform natives
  - Do: From AST-15 Q1 (human, 2026-11-10): add per-platform native classifiers (LWJGL, Bullet, Jolt) to `gameJar` and a platform choice in the export dialog (one export per platform).
  - Done when: A Linux and a macOS export start with rendering and physics; `verify-game-jar.sh` covers each platform.
  - Deps: AST-29
- [ ] **AST-31** `P2` Native launcher with bundled runtime (jpackage)
  - Do: From AST-15 Q5 (human, 2026-11-10): replace `run-game.bat` by a jpackage `.exe` with the project icon and a bundled Java runtime, so players need no Java install.
  - Done when: The exported `.exe` shows the icon and starts on a machine without Java; tests for the packaging step.
  - Priority raised to P2 (human, 2026-11-18): Steam publishing (NET-07) waits for the native launcher.
  - Deps: AST-29
- [ ] **AST-32** `P3` Measure the pack index; protobuf only if it pays (AST-18)
  - Do: Per `docs/design/AST-18.md` rev 2: measure the JSON atlas cost at game start-up; convert it to proto3 through `kotlinx-serialization-protobuf` only if it exceeds 5 %; otherwise keep JSON and add random-access pack reads. Editable files stay JSON; the network format is unchanged; no inspect command.
  - Done when: The measurement is recorded in the note, and the chosen path is implemented with tests.
  - Deps: AST-18, AST-27
- [ ] **AST-33** `P3` Keep mesh part and material names on import (GPL-24)
  - Do: Per (`docs/design/GPL-24.md` §8): keep Assimp mesh part names and material names on `MeshPart`/`Material`, including the packed/exported model path.
  - Done when: Imported and exported models expose part and material names; tests.
  - Deps: GPL-24
- [x] **AST-34** `P2` OSM city generator: data, tiles and generation (AST-17 slices a, c)
  - Do: Per `docs/design/AST-17.md` rev 3 §8: Overpass client, snapshot, parser, projection, JTS triangulation (editor-only), generation into chunks with mesh colliders by reference (box fallback on Simple after PHY-25), `.skmat` styling; OpenFreeMap vector tiles with cache and a custom raster URL; User-Agent with the repository URL; regenerate after a backend change; ODbL attribution in the ledger and credits.
  - Done when: A chosen area generates a playable, credited city; tests with no sockets.
  - Deps: AST-17, AST-27, PHY-24
- [x] **AST-35** `P1` AST-27 follow-ups from REN-34 and PHY-24
  - Do: (1) From skate-render: add `lighting.glsl` to the AST-26 basic (overridable) shader set next to `fog.glsl`. (2) From skate-physics: a GUID→path table in the pack so exported games resolve `MeshCollider3D.meshRef` (`CollisionMeshes.source = ModelCollisionMeshSource(...)` at game start-up), and set the editor's source from the project's asset registry on project open (with skate-editor). (3) From skate-editor: remove the unused `help.<id>.what/how/related` strings left in the animation, assetstore and render string files.
  - Done when: A referenced mesh collider works in an exported game; `lighting.glsl` is copied and overridable; no unused help strings; tests.
  - Deps: AST-27, REN-34, PHY-24
- [x] **AST-36** `P2` `.skterrain` asset (REN-28 task 8)
  - Do: (NEEDS from REN-36): `terrain.json` with the recipe, stamps and layers; region files for DATA and edited chunks; GUID meta, hot reload, packing. `TerrainSource.DATA` renders flat until chunk data can be loaded.
  - Done when: A sculpted terrain saves, reloads, hot-reloads and exports; tests.
  - Deps: none (needs REN-36 tasks 3–4 (checkpoint on agents/render, integrated in round 35))
- [x] **AST-37** `P1` Pack built-in water materials and water sounds
  - Do: (NEEDS from TLG-17 and AUD-12): built-in water materials (`WaterMaterials.ALL`, no file, resolved in `WaterSchema.BUILT_IN`) break the "every engine: ref is packed" check (`ProjectLifecycleEndToEndTest.Lifecycle_9`) now that the showcase uses `WaterMaterials.POOL`: have the packer and the check treat them as built-in (or ship real `water_*.skmat` files with REN). Add the `engine:Sounds/Water/*` clips to `EngineDefaults.Sounds.ALL` so exports pack them.
  - Done when: TLG-17's showcase passes the lifecycle test; exports contain the water sounds; tests.
  - Deps: AST-36
- [x] **AST-38** `P2` `.skterrain`: layers, biomes, climate and scatter references (REN-36)
  - Do: (NEEDS from REN-36): store layers, biomes, climate and biome source in `terrain.json`; persist `paintedBiomeIds` per chunk in region files; the pack's `ReferenceCollector` follows layer `.skmat`, scatter models, rule materials and prefabs.
  - Done when: A biome-painted, scattered terrain saves, reloads and exports with all its references; tests.
  - Deps: AST-36
- [x] **AST-39** `P0` Exported game crashes: "No suitable reader found for the file format of file '$$$___magic___$$$'"
  - Do: (human, round 35 testing, 2026-11-17): running an exported game fails at start-up in `AssimpLoader.loadModel(AssimpLoader.kt:68)` ← `AssetsManager.resolveModel` ← `RenderComponent.resolveModelFromByteArray` ← `ProjectManager.openProject` ← `GameMain.main`: a RenderComponent's model path resolves to packed bytes Assimp cannot read. A new project exported from the CLI (no models) runs fine. Find which object type/asset (round 35 candidates: water, blended material, terrain, AST-34 `.glb`, animator clips, compression), fix it, and add an export-and-load regression test. Also: on a case-sensitive folder the packer writes `builds/game.bin` but the exporter expects `Builds/`.
  - Done when: A project using every round-35 feature exports and starts; regression test.
  - Deps: none
- [ ] **AST-40** `P2` Random-access pack reads for streamed terrain (REN-37 task 19)
  - Do: (NEEDS from REN-37): read pack entries through a `FileChannel` (uncompressed region entries) so streamed chunks don't load all of `game.bin`; confirm `TerrainAssets.chunk` is safe from worker threads (streamed chunks call it from the common pool).
  - Done when: An exported streamed terrain loads chunks without reading the whole pack; thread-safety test.
  - Deps: AST-38 (needs the REN-37 checkpoint, round 38)
- [ ] **AST-41** `P2` Save-game container with named sections (REN-37 task 25)
  - Do: (NEEDS from REN-37): a save-game container with named sections, used for remembered scatter state (destroyed or taken props).
  - Done when: Sections round-trip and unknown sections survive; tests.
  - Deps: AST-38 (needs the REN-37 checkpoint, round 38)
- [ ] **AST-42** `P3` Pack item files and their models (GPL-27)
  - Do: (NEEDS from GPL-27): move the bundled item paths into `Assets.Items`, list `Assets/Items/` from the export pack, and pack `engine:` models referenced by item files.
  - Done when: An exported game equips the hat and glasses; tests.
  - Deps: none (needs the GPL-27 checkpoint, round 38)
- [ ] **AST-43** `P2` Single-jar game packaging review
  - Do: (human, Discord 2026-11-17): review game packaging and allow `game.bin` and everything else to be packaged inside the game jar (one file to ship), keeping the separate-file mode if it helps patching or streaming (AST-40). Design note first.
  - Done when: An export option produces a single runnable jar; tests.
  - Deps: AST-40
- [ ] **AST-44** `P2` Mesh downscaling tool
  - Do: (human, Discord 2026-11-17): a mesh simplification (decimation) tool, if not already available (check the Blender import options first): reduce a model's triangle count to a target ratio, preview the result, and optionally generate LOD levels.
  - Done when: A model can be reduced and saved from the editor; quality and ratio tests.
  - Deps: none
- [ ] **AST-45** `P2` Texture import settings
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): sRGB, filtering, mipmaps, wrap, compression in the inspector.
  - Done when: Settings save and apply on reimport; tests.
  - Deps: none
- [ ] **AST-46** `P2` Model import settings
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): scale, up axis, collision, material extraction, like EDT-66 for `.blend`.
  - Done when: Settings save and apply on reimport; tests.
  - Deps: none
- [ ] **AST-47** `P1` Decode scene assets in the background, upload on main
  - Do: (QA-19 audit, `docs/design/QA-19.md`, rule 10): when a scene opens, collect and decode its models, textures and sounds on a worker through the `AssetLoadService` decode functions; the main step only uploads, in per-frame batches with progress (or move `RenderComponent` and the sky to `requestModel`/`requestTexture` tickets with placeholders). Measured 0.6–0.8 s per new project.
  - Done when: Opening the new-project scenes runs no STB or Assimp decode on the main thread (fake-loader test).
  - Deps: none
- [ ] **AST-48** `P2` Pack the input actions asset
  - Do: (NEEDS from INP-11): confirm or make `Assets/Data/input.actions.json` packed in exports.
  - Done when: An exported game loads the project actions; test.
  - Deps: INP-11
- [x] **AST-49** `P1` AssetsManager.loadModel recurses forever when the default cube cannot load
  - Do: (NEEDS from EDT-59): fix the path-compare bug that makes `loadModel` recurse endlessly when the default cube fails to load (seen without a headless RHI device).
  - Done when: A failing default cube returns a fallback or error without recursion; regression test.

### ECS — `ECS` (`skate-ecs`)

Round 27+ goals: deterministic seeds for games and procedural generation.

- [x] **ECS-14** `P1` Seeded deterministic RNG service
  - Do: Engine RNG service with a project/game seed (project setting, runtime override), independent named streams per system so adding one system does not shift others, exposed to scripts. Show the seed in the Feature Showcase.
  - Done when: The same seed reproduces identical sequences per stream across runs; tests cover determinism and stream independence.

- [ ] **ECS-15** `P3` Remove unused GridLines fields
  - Do: From REN-25: `lodCloseDistance`, `lodFarDistance`, `edgeFadeEnabled` and `edgeFadeStart` on `GridLines` are unused now that the shader handles LOD and fade. Remove them and update the default scenes.
  - Done when: The fields are gone and scenes load; tests updated.
- [x] **ECS-16** `P2` Register WaterComponent so it can be added in the editor (REN-35)
  - Do: (from REN-35 test notes): register `WaterComponent` (and any other new REN-35 components) in the component registry and serialization so it can be added from Add Component and saved with the scene.
  - Done when: Water can be added to a GameObject in the editor and survives save/reload; tests.
  - Deps: REN-35
- [x] **ECS-17** `P2` Review and register TerrainComponent (REN-36)
  - Do: (NEEDS from REN-36): review `TerrainComponent` and `TerrainSystem`, and register the component in `ComponentType` and the Add Component menu.
  - Done when: Terrain can be added from Add Component and survives save/reload; tests.
  - Deps: none (needs REN-36 tasks 3–4 (checkpoint on agents/render, integrated in round 35))
- [x] **ECS-18** `P2` Engine.kt wiring: frame-rate option, water response and water audio (GUI-17, GPL-32, AUD-12)
  - Do: Engine.kt wiring (no owner, ECS takes it): (a) (NEEDS from GUI-17) register `FrameRateOption.option(...)` in `gameOptions` with a limiter that does nothing in the editor, so editor Play shows the same Frame rate row as the exported game (GUI-12 parity); `GameMain` registers it again with the real limiter, register replaces by id. (b) (NEEDS from GPL-32) call `playerMotionSystem.listenTo(eventSystem)` where `PlayerMotionSystem` is built, then remove the temporary call in `SkateGameplay.install`. (c) (NEEDS from AUD-12) construct `AudioSystem(audioEngine, logger, assetsManager, cameraManager, assetsManager.paths::localPath, eventSystem)` in `initializeSystems()` so splashes hear `WaterEntered`.
  - Done when: Editor Play shows the Frame rate row; every game reacts to water (bail, splash) without game-specific wiring; tests.
  - Deps: GUI-17, GPL-32, AUD-12
- [x] **ECS-19** `P2` Prefab scatter: hidden child entities under the terrain (REN-28 task 7)
  - Do: (NEEDS from REN-36): spawn non-serialized, hierarchy-hidden child entities under the terrain for `ScatterRule.prefab`, capped per rule (`maxEntities`), from the placed instances in `TerrainChunkData.scatter`. Overlaps the phase-D ScatterItem task 24.
  - Done when: Prefab scatter rules spawn capped, unsaved, hidden children that follow chunk changes; tests.
  - Deps: REN-36
- [ ] **ECS-20** `P2` Engine.kt: register the item, appearance and attachment systems (GPL-27)
  - Do: (NEEDS from GPL-27): in `engine/core/Engine.kt`, register `InventorySystem` (shared `ItemDatabase`, `slotsOf = SkeletonConfigs::slotsOf`), `AppearanceSystem` (`loadModel = assetsManager::loadModel`) and `BoneAttachmentSystem` (and `playerMotionSystem.listenTo(eventSystem)` if ECS-18 missed it), then remove the temporary registrations in `SkateGameplay.install`. Mark `Equip:*` children runtime-only (`GameObject.isRuntimeOnly`, ECS-19) so they are never saved (design Q3).
  - Done when: Every game gets inventory, appearance and attachments; equipped items are never saved; tests.
  - Also (GPL-27 final): register `HealthSystem(eventSystem, items)` and `InteractionSystem(eventSystem, pressed = { reader.wasActionPressed(Interaction.ACTION) })`, sharing the `ItemDatabase` with `InventorySystem`, and the `Interaction.PRESET` action preset (`Interaction::bindDefaults`); then remove the temporary registrations in `SkateGameplay.install` and `SkateGameplay.register`.
  - Deps: ECS-19 (needs the GPL-27 checkpoint, round 38)
- [x] **ECS-21** `P1` FirstPersonCameraComponent edits don't always reach the Game Viewport
  - Do: (human, Discord 2026-11-17): changing FirstPersonCameraComponent values in the Properties window does not always show in the Game Viewport. Find which fields are cached or not marked dirty, fix it, and add a test that each field change reaches the camera used for rendering.
  - Done when: Every field change shows on the next frame, in edit mode and Play; tests.
  - Deps: none
- [ ] **ECS-22** `P2` New scenes get a camera by default
  - Do: (human, Discord 2026-11-17): new scenes already get a sun; also add a default camera (positioned to see the origin), in the editor's + scene path and the project templates.
  - Done when: A new scene has a sun and a camera; test.
  - Deps: none
- [ ] **ECS-23** `P2` Prefab overrides and prefab edit mode
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): list a PrefabInstance's overrides with Apply / Revert, and open a `.prefab` (double-click) in an isolated edit scene instead of the OS editor.
  - Done when: Overrides apply and revert with undo; prefabs open in an edit scene; tests.
  - Deps: EDT-107
- [ ] **ECS-24** `P3` Hide code-only components from Add Component
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): NativeScriptComponent (same list as INP-14), or a "set from code" note (EDT-106 Q2).
  - Done when: Code-only components are not offered; test.
  - Deps: none
- [ ] **ECS-25** `P3` Write saved scenes in the background
  - Do: (QA-19 audit, `docs/design/QA-19.md`, rule 10): `SceneManager.saveScene` encodes on the main thread and writes the file on a worker; failures through the existing save result. Measured 28–52 ms for the showcase.
  - Done when: Saving does no file I/O on the main thread; test.
  - Deps: none
- [ ] **ECS-26** `P2` Engine passes project text loading to InputSystem
  - Do: (NEEDS from INP-11): `Engine` passes `assetsManager::readText` to `InputSystem` so project actions load in the editor and the exported game.
  - Done when: Project actions load in both hosts; test.
  - Deps: INP-11

### Physics — `PHY` (`skate-physics`)

Round 27+ goals: a Jolt backend selectable per project, a terrain heightfield collider, and water.

- [x] **PHY-15** `P2` Heightfield terrain collider
  - Do: Heightfield collider shape across backends, following the approved REN-28 terrain design.
  - Done when: Bodies and the character collide correctly with terrain; tests.
  - Deps: REN-28
- [x] **PHY-16** `P2` Water volumes and buoyancy
  - Do: Buoyancy and drag in water volumes, following the approved REN-29 design.
  - Done when: Showcase objects float and settle realistically; tests.
  - Deps: REN-29
- [x] **PHY-26** `P2` Terrain heightfield collider component (REN-28 task 9)
  - Do: (NEEDS from REN-36): a `HeightfieldCollider3D` reading `TerrainComponent.runtime.chunks`: per chunk `ColliderShape.Heightfield(chunkQuads + 1, spacing, chunk.colliderHeights())`, body at terrain world position + (cornerX, 0, cornerZ), rotation ignored; rebuild when `runtime.version` changes or a chunk is dirty; `TerrainComponent.collider` turns it off.
  - Done when: Bodies and the character collide with a generated terrain on every backend; tests.
  - Deps: PHY-15 (needs REN-36 tasks 3–4 (checkpoint on agents/render, integrated in round 35))
- [x] **PHY-27** `P2` Static colliders for terrain scatter (REN-28 task 7)
  - Do: (NEEDS from REN-36): static colliders for scatter items of rules with `collider = true`, from `TerrainChunkData.scatter` (terrain-local position, yaw, scale, normal; world matrix from `TerrainScatterRenderer.matrixOf`). Rebuild when a chunk's `scatter` list changes.
  - Done when: The character collides with scattered rocks and trees on every backend; tests.
  - Deps: PHY-26
- [ ] **PHY-28** `P2` Streamed terrain physics (REN-37 task 17, PHY part)
  - Do: (NEEDS from REN-37): freeze and unfreeze bodies on Bullet, Jolt and Simple, keeping and restoring velocities; `HeightfieldCollider3D` and `ScatterCollider3D` follow only physics-ring chunks (planner `physicsWanted`), at most 2 adds/removes per fixed step, with a "collider present" bitset. Bodies over a chunk without a collider freeze until it arrives; nothing outside the terrain bounds freezes. Off-thread shape building per backend.
  - Done when: Bodies on a streamed terrain never fall through a missing chunk and resume with their velocity; tests on every backend.
  - Deps: PHY-27 (needs the REN-37 checkpoint, round 38)
- [~] @skate-physics **PHY-29** `P2` Destruction system design
  - Do: (human, Discord 2026-11-17): plan a destruction system (fractured meshes, pre-fractured vs runtime, debris pooling and lifetime, damage thresholds, effects and audio hooks, network and save-state concerns with REN-37's remembered props). Design note `docs/design/PHY-29.md` with sliced tasks; ask for approval before code.
  - Done when: The note is approved and its slices are in the backlog.
  - Decisions (human, 2026-11-18): runtime fracture is IN SCOPE (changed from the recommendation), alongside pre-fractured pieces; debris is cosmetic, expires and is never saved (`persistent` pieces for gameplay); each client simulates its own debris; budgets 256 pieces High / 96 Low, 10 s, 4 breaks per step; any body's impact can break a prop (`impactThreshold`); structural destruction later; HealthComponent (GPL-24) where present, `integrity` otherwise. The note gets a rev 2 for runtime fracture.
  - Runtime-fracture decisions (human, 2026-11-18, rev 2 §16.2): Q9 pieces computed on a worker (1–3 steps late, remove with effects after 100 ms); Q10 CHANGED: runtime fracture keeps an anchored base piece; Q11 CHANGED: pieces may break again (re-fracture allowed, needs depth and budget limits); Q12 unsuitable meshes fall back to remove with effects, logged once per model.
  - Deps: none
- [ ] **PHY-30** `P2` Collision layers in Project Settings
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): named layers in Project Settings, a layer matrix, and layer / mask dropdowns on RigidBody3D instead of raw ints.
  - Done when: Layers are named and edited in a matrix; bodies pick them from dropdowns; tests.
  - Deps: none
- [ ] **PHY-31** `P2` Collider gizmos
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): wireframe of the selected object's colliders and handles for size and offset.
  - Done when: Colliders of the selection draw and resize with undo; tests.
  - Deps: none
- [ ] **PHY-32** `P2` Vehicle wheel editor
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): add / remove wheels, viewport handles for wheel offsets.
  - Done when: Wheels are edited in the inspector and viewport with undo; tests.
  - Deps: EDT-107
- [ ] **PHY-33** `P3` Hull and mesh colliders: Generate from mesh
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): "Generate from mesh" and a vertex count summary instead of hidden lists.
  - Done when: Hulls generate from the model with undo; test.
  - Deps: none
- [ ] **PHY-34** `P3` Ragdoll profile as data
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): bone-to-body mapping, masses and limits as data with an inspector (today `RagdollBuilder` is Mixamo-only code).
  - Done when: A ragdoll profile asset drives the builder and is editable; tests.
  - Deps: none
- [ ] **PHY-35** `P2` IK world probes: ground normal, edge finder, hand-plant point (ANI-28 slice g)
  - Do: (ANI-28 §6 slice g): ground-normal probe (raycast under a point, returns hit normal), edge-finder probe (finds the nearest ledge edge over `IPhysics3D.overlap`), and hand-plant probe (sphere cast to wall/ground); all pure over `IPhysics3D`; tests with a fake backend.
  - Done when: All three probes have passing tests on a fake backend.
  - Deps: none
- [~] @skate-physics **PHY-17** `P2` REWRITE Jolt physics backend — slices a–e done; slice f (per-project selection) waits for EDT-59
  - Do: Implement the approved slices in order: a) preparation (`BodyDescription`, per-spec native loader, `ShapeWireframe` move, test helper), b) Jolt core (bodies, shapes, layers, queries, factory + fallback), c) contacts and triggers, d) joints/ragdoll/vehicle parity, e) debug draw, stats, leak/determinism tests, benchmark, f) per-project selection plumbing with EDT/AST/TLG. Decisions: physical centre of mass, ship both natives now (AST-15 strips the unused one), Bullet stays the default.
  - Done when: The physics test suite passes on Jolt as well as Bullet; the showcase runs on Jolt.

- [x] **PHY-18** `P2` Extract physics natives to a per-user folder
  - Do: From PHY-17: Bullet and Jolt natives extract to `USER_DIR`, which fails for games installed in read-only folders (Program Files). Extract to a per-user data folder instead.
  - Done when: An exported game in a read-only folder starts with physics; loader tests cover the path.
- [x] **PHY-19** `P3` Jolt-native ragdolls, vehicles and character controller — design approved by the human 2026-11-10 (`docs/design/PHY-19.md` rev 3)
  - Do: From PHY-17: optional Jolt `Ragdoll` (pose motors), `VehicleConstraint` and `CharacterVirtual` modes (the last one with GPL). Design note first.
  - Done when: The human approves the note.
  - Deps: PHY-17

- [x] **PHY-20** `P1` Ship Bullet's release native instead of SpDebug
  - Do: From the PHY-17 benchmark: `build.gradle.kts` uses libbulletjme's `SpDebug` classifier, so the editor and exported games run Bullet's debug build. Switch to the release single-precision classifier (keep debug only for tests if useful), update `verify-game-jar.sh`, re-run the benchmark.
  - Done when: Exports ship the release native; physics suite green; benchmark numbers updated in `docs/design/PHY-17.md`.
- [x] **PHY-21** `P1` Concurrent processes must not clobber the shared natives folder
  - Do: From round 30 integration (skate-audio, skate-tooling): since PHY-18, `NativeLibraryLoader` extracts with `CLEAN_EXTRACTION` into the shared `%APPDATA%\Skate\natives`, so concurrent JVMs (parallel worktree test runs, a running editor next to an exported game) wipe or lock each other's DLLs and physics fails with `UnsatisfiedLinkError`. Use a per-version (content-hash) subfolder and skip extraction when an identical DLL is already present; never delete a DLL another process may hold.
  - Done when: Two JVMs loading Bullet and Jolt at the same time both succeed; loader tests cover reuse and the versioned path.
  - Deps: PHY-18
- [x] **PHY-22** `P3` Scene query interface with overlap (GPL-24)
  - Do: Per (`docs/design/GPL-24.md` §8): raycast, shapeCast and a new `overlap(shape, position, rotation, layerMask)` on `IPhysics3D`, exposed from `PhysicsSystem`, implemented for Bullet, Jolt and Simple.
  - Done when: The same query tests pass on every backend.
  - Deps: GPL-24
- [ ] **PHY-23** `P3` Jolt ragdolls, vehicle drivetrain and character body (PHY-19 slices a–g)
  - Do: Per `docs/design/PHY-19.md` rev 3 §8: 19a joint motors (all backends; Bullet on New6Dof always), 19b POWERED ragdoll, then the bind-pose ragdoll frames task, 19c vehicle drivetrain on all backends (no migration of old fields), 19d inspector sections (EDT), 19e `CharacterBody3D` with inner body, 19f gameplay on the character body (GPL), 19g showcase walker with "Take control" only (TLG). Each project uses only its own backend.
  - Done when: Each slice's tests pass; cross-backend vehicle tests within the note's tolerances.
  - Deps: PHY-19
- [x] **PHY-24** `P2` `MeshCollider3D.meshRef` (AST-17 PHY-a)
  - Do: Load collider triangles from a model by GUID instead of inline vertices, on Bullet and Jolt.
  - Done when: A city chunk collides through a referenced model; tests.
  - Deps: AST-17
- [x] **PHY-25** `P3` SimplePhysics3D static oriented boxes and broad phase (AST-17 PHY-b)
  - Do: Static oriented boxes (contacts and rays) and a broad phase, so the city box fallback behaves as boxes.
  - Done when: Box contacts and rays tested; a box city is walkable on Simple.
  - Deps: AST-17

### Animation — `ANI` (`skate-animation`)

Round 27+ goals: an animation authoring tool and animation from video.

- [x] **ANI-12** `P2` Animation tool and video-to-animation: design note — approved 2026-11-06 (`docs/design/ANI-12.md`). Human: drop MotionBricks and the GEM-X backend for now (revisit later); the goal is a tool that makes creating and editing animations from video **easy**.
- [x] **ANI-13** `P2` `.skanim` clip format, loader and pure editing core
  - Do: ANI-12 §2.2 and §5.1: JSON `*.skanim` format (versioned), loader to the existing `Animation` types, and the pure editing core under `engine/animation/edit/` (keys, tangents, Euler↔quat curves, keyframe reduction, filters, loop seam, foot-lock).
  - Done when: A `.skanim` round-trips and plays on the skater; the core is unit-tested.
  - Deps: ANI-12
- [x] **ANI-14** `P2` Animation editor window: timeline, dope sheet, events, preview
  - Do: ANI-12 §2.1. Written by skate-animation, reviewed by skate-editor (lead decision); edits go through `Command`s; offscreen preview with REN help.
  - Done when: A clip can be scrubbed, keyed, retimed and saved, with undo; window logic tested.
  - Deps: ANI-13, EDT-52
- [x] **ANI-15** `P2` Curve editor and cleanup tools
  - Do: ANI-12 §2.1: curve editor (tangent modes) and cleanup tools (resample, reduction, smoothing, foot-lock, mirror, trim, loop seam).
  - Done when: The tools work on a mocap clip in the editor; core logic tested.
  - Deps: ANI-14
- [x] **ANI-16** `P2` Retargeter rest-pose compensation
  - Do: ANI-12 §3.4: compensate rest-pose differences (T-pose vs A-pose) per bone in `Retargeter`.
  - Done when: A T-pose source retargets cleanly onto the skater rig; tests.
- [x] **ANI-17** `P2` Video to animation with MediaPipe (local files)
  - Do: ANI-12 §3: process runner, BVH writer, MediaPipe adapter script and position→rotation solver with filtering, foot contacts and optional root motion; "Import mocap file…" for BVH/FBX/glTF from any tool; "Check setup" in Editor Settings and a setup doc. Make the flow **easy**: pick a video, preview, one click to a clip that opens in the animation editor.
  - Done when: A local sample video produces a clip on the skater rig that opens in the animation editor; solver tested on synthetic landmarks.
  - Deps: ANI-13, ANI-16
- [x] **ANI-18** `P0` Video to animation fails with current MediaPipe (`no attribute solutions`)
  - Do: From the human testing PR #37 (2026-11-10): current MediaPipe releases dropped the legacy `mp.solutions` API, which `mediapipe_extract.py` uses by default. Default to the Tasks API (`PoseLandmarker`, video mode), get the `.task` model without manual steps (path override in Setup), keep `solutions` only as a fallback; Check setup reports the API in use.
  - Done when: With the current MediaPipe from pip, Check setup reports Ready and Create animation produces a `.skanim` from a local video; docs and tests updated.
  - Deps: ANI-17
- [x] **ANI-19** `P3` Joint remap of skinned clothing by bone name (GPL-24)
  - Do: Per (`docs/design/GPL-24.md` §8): remap a skinned clothing mesh onto a target skeleton by bone name; refuse items whose bones don't match.
  - Done when: Pure remap function with tests, including the refusal case.
  - Deps: GPL-24
- [x] **ANI-20** `P1` Video to Animation: playback controls and a trim range
  - Do: (human, Discord 2026-11-10): play/pause, scrub and frame step in the video preview, and in/out markers so only a portion of the video becomes the animation.
  - Done when: A trimmed range produces a clip of that length only; tests for the range maths and the extractor arguments.
  - Deps: ANI-18
- [x] **ANI-21** `P1` REWRITE Animator window: create, edit, remove and duplicate states
  - Do: (human, Discord 2026-11-10): a user-friendly Animator window (state graph): add, rename, duplicate and delete states, assign clips, edit transitions, conditions and parameters, with undo; writes the animator controller asset. Design note first.
  - Done when: The human approves the note; then a controller can be built from scratch in the window and drives a character; tests.
  - Deps: ANI-14

### Audio — `AUD` (`skate-audio`)

Audio needs a more complete implementation.

- [x] **AUD-12** `P3` Splash and water-ambience sounds (REN-29 task 16, optional) (named AUD-01 in commits; renumbered because AUD-01 is archived)
  - Do: (NEEDS from REN-35): play a splash on PHY-16's `WaterEntered` and a looping water ambience near water volumes.
  - Done when: Entering the showcase pool splashes and the pool has ambience; tests.
  - Deps: PHY-16
- [x] **AUD-13** `P1` AudioSystemShowcaseTest ignores the showcase pool's water ambience (named AUD-02 in commits; renumbered because AUD-02 is archived)
  - Do: (NEEDS from TLG-17): the showcase's Nature pool now adds AUD-12's water ambience when the camera is at the origin, so two `AudioSystemShowcaseTest` tests fail. In the `system()` helper set `waterAudio = WaterAudioConfig(enabled = false)` (or assert on the song's voice instead of `al.playing.single()`).
  - Done when: `AudioSystemShowcaseTest` passes with TLG-17's showcase; tests.
  - Deps: AUD-12
- [ ] **AUD-14** `P3` Sound import settings
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): stream vs. load, default bus, loop points.
  - Done when: Settings save and apply; tests.
  - Deps: none
- [~] @skate-audio **AUD-15** `P1` Water audio fields on WaterComponent (splash and ambience editable in the inspector)
  - Do: (human, 2026-11-18): move splash and ambience audio settings out of `AudioSystem.waterAudio` and onto `WaterComponent` itself, so a designer can edit them in the Properties window without touching any system. Add these optional fields to `WaterComponent` (nulls mean "use engine default"): `ambienceClip: String?`, `ambienceVolume: Float?`, `ambienceMinDistance: Float?`, `ambienceMaxDistance: Float?`, `splashClips: List<String>?`, `splashVolume: Float?`, `splashMinDistance: Float?`, `splashMaxDistance: Float?`. `AudioSystem.updateAmbience` and `startSplashes` read per-entity values when set, falling back to `AudioSystem.waterAudio` defaults. Remove the `AudioComponent` escape-hatch check (`go.getComponent<AudioComponent>() != null → skip`) — a `WaterComponent` with its own audio fields is now the override path; an `AudioComponent` on the same entity still works for other sounds but no longer suppresses water audio. Update `FeatureShowcase.nature()`: set `ambienceClip`, `ambienceVolume` and `splashClips` on the pool's `WaterComponent` to their current defaults so they appear in the inspector. Inspector: the water inspector (EDT-90) already renders the `WaterComponent`; ensure the new audio fields show up in a collapsible "Audio" section with the same label/undo treatment as other fields.
  - Done when: Selecting the pool in Properties shows an Audio section on `WaterComponent`; editing `ambienceClip` or `ambienceVolume` changes the runtime sound without restarting the editor; splashes still fire on `WaterEntered`; tests cover the per-entity override and the system-default fallback.
  - Deps: AUD-13, EDT-90

### Input — `INP` (`skate-input`)

Round 27+ goals: input is too tightly coupled with skateboarding; it needs a game-agnostic abstraction.

- [x] **ANI-22** `P0` Video to Animation window: scrolling is broken
  - Do: (human, round 32 testing): the Video to Animation window does not scroll (after ANI-20 added playback and trim). Fix the window's child/scroll layout so the whole content scrolls with the mouse wheel and scrollbar.
  - Done when: All controls are reachable by scrolling at a small window size; a test or guard for the layout if practical.
  - Deps: ANI-20
- [x] **ANI-24** `P0` Animator: clipped icon buttons; inspector Play/Reset do nothing
  - Do: (human, round 33 testing): the Animator window's parameter delete button is too short for its icon; the Animator component's Play and Reset buttons have no effect. Play previews in edit mode and pauses/resumes the controller in play mode; Reset returns to the default state.
  - Done when: Icons fit; Play/Reset change the pose in both modes; tests.
  - Deps: ANI-21
- [x] **ANI-25** `P0` Animator: items without room; inspector playback overridden by the player state
  - Do: (human, round 33 re-testing): some Animator window items don't render for lack of room; inspector Play still doesn't play the skater's animations properly, likely because the player/character state drives the animator every frame. Size rows from the style; inspector playback wins while active and hands back on stop/Reset.
  - Done when: All items visible at every density and small sizes (headless test); inspector Play works on the real skater in edit and play mode; tests.
  - Deps: ANI-24
- [x] **ANI-23** `P2` Animator window: blend tree editor in the state inspector
  - Do: (ANI-21 design answer, 2026-11-10): edit 1D/2D blend trees of a state in the Animator window's state inspector, with live preview.
  - Done when: A blend tree can be created and edited from the window and saved in the controller file; tests.
  - Deps: ANI-21
- [x] **ANI-26** `P2` Animator window lists the parameters the character writes (GPL-31)
  - Do: (NEEDS from GPL-31): in `AnimatorWindow.parametersPanel()`, call `CharacterAnimationDriver.writtenParameters(controller.preview)` and list the ones not declared yet under a "Written by the character" heading, with a one-click declare. New `lbl.animator_window.*` keys (EN, FR).
  - Done when: The Animator window lists the character's parameters and can declare them; tests.
  - Deps: GPL-31
- [ ] **ANI-27** `P2` Animation Editor: Mirror as a toggle, steady frame indicator
  - Do: (human, Discord 2026-11-17): the Mirror button becomes a toggle that shows whether the clip is mirrored; the frame indicator gets a fixed width for the clip's largest frame number so it no longer shifts when 9 becomes 10.
  - Done when: Mirror shows its state and toggles back with undo; the indicator keeps its position; tests.
  - Deps: ANI-15
- [x] **ANI-28** `P1` IK review for skateboarding and parkour
  - Do: (human, Discord 2026-11-17): a complete review of the inverse kinematics system for skateboarding (feet on the board, hands on the rail/ledge, look-at) and parkour (hand plants, foot placement on ledges and stairs): what exists, what is missing (3D markers or targets as components, IK chains, weights, blending with animation), and a super user-friendly editor UI (gizmos for targets, presets for the skater). Design note `docs/design/ANI-28.md` with sliced tasks; ask for approval before code.
  - Done when: The note is approved and its slices are in the backlog.
  - Decisions (human, 2026-11-18): all recommendations accepted: IK targets are scene objects (IkTarget GameObjects); chains by humanoid slot with a bone-name override; per-state weights in the controller asset (Animator window); edit-mode preview behind a toggle; IK and probes now (slices b–h), parkour movement later under its own GPL note (slice i); bug F1 fixed now as slice a (GPL-38); `BoneOverride` removed in slice b.
  - Deps: none
- [x] **ANI-28b** `P1` IkTarget/IkSetup components and IkSetupSystem (ANI-28 slice b)
  - Do: (ANI-28 §6 slice b): `IkTarget` and `IkSetup` components, `IkSetupSystem`, humanoid-slot resolution, serialization, `BoneOverride` removed; tests.
  - Done when: `IkTarget`/`IkSetup` serialize; `IkSetupSystem` resolves chains by slot; tests.
  - Deps: ANI-28
- [ ] **ANI-29** `P2` IK solvers: pelvis offset, foot roll, FABRIK chain, look spread (ANI-28 slice c)
  - Do: (ANI-28 §6 slice c): pelvis offset, foot roll to surface normal, FABRIK n-bone chain with joint limits, look-at spread over spine+neck+head, solve order (pelvis before legs); tests.
  - Done when: All solvers pass the solver test suite; the skater's feet follow a ramp slope.
  - Deps: ANI-28b
- [ ] **ANI-30** `P2` Per-state IK weights in the controller asset (ANI-28 slice d)
  - Do: (ANI-28 §6 slice d): per-state IK weight tracks in the `.controller.json` asset and the Animator window; weight ramps over transition durations; tests.
  - Done when: A controller state drives IK weight; ramp tests pass.
  - Deps: ANI-28b
- [ ] **ANI-31** `P2` IK inspector, target gizmos and edit-mode preview (ANI-28 slice e)
  - Do: (ANI-28 §6 slice e): IK inspector with presets, target gizmos, create-target, debug toggle, edit-mode preview; render tests.
  - Done when: A designer can create IK targets and see the rig update live in the editor without pressing Play; render tests.
  - Deps: ANI-28b, ANI-29
- [ ] **ANI-32** `P2` IK weight curves in `.skanim` clips (ANI-28 slice h)
  - Do: (ANI-28 §6 slice h): IK weight curves in `.skanim` clips (Animation Editor) feeding the per-state weight system (§4.2 of the design).
  - Done when: A clip can key an IK weight; the Animation Editor shows the curve; tests.
  - Deps: ANI-30
- [ ] **ANI-33** `P3` Parkour IK design note (ANI-28 slice i)
  - Do: (ANI-28 §6 slice i): own design note for parkour IK (vault, stairs, ledge grab, motion warping), using ANI-28b–g results.
  - Done when: The human approves the note and its slices are in the backlog.
  - Deps: ANI-28b, ANI-29, ANI-30, ANI-31
- [ ] **ANI-34** `P1` Foot IK is editable in the inspector and the walk looks right again (off by default since round 41a via FootIkConfig.enabled; do after GPL-40)
  - Do: (human, round 41a test): since GPL-38 foot IK runs on the skater and the walk looks wrong, and `PlayerController.footIk` (`FootIkConfig`) is not editable in the editor. Expose it in the PlayerController inspector: enabled toggle, global weight, per-leg bones and probe offset, blend speed, plus a debug view of the targets and resolved bones (strings and tooltips EN+FR). Find why the walk is distorted (targets, hip offset, weight while moving) and fix it; default to IK only when standing or slow if needed.
  - Done when: The Skater in MainScene can toggle and tune foot IK live from the inspector; walking looks natural with IK on and off; tests.
  - Deps: GPL-38
- [x] **INP-10** `P1` REWRITE Decouple input from skateboarding
  - Do: Design note `docs/design/INP-10.md` first. Generic action maps and input contexts (gameplay/UI/vehicle/...) with push/pop priority in the engine; skate-specific actions, bindings and gesture/flick logic move to the game layer (`game/**`, owned by gameplay). Scripts and in-game UI use the generic API.
  - Done when: The design is approved and implemented, `engine/input/**` has no skateboarding concepts (architecture-guard test), and the skate game plays as before.
- [~] @skate-input **INP-11** `P2` Project input actions
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): define actions and contexts in a project asset, edited in Key Bindings (add, rename, remove, default bindings), so a non-skate game needs no code for its actions. REWRITE-sized: design note first.
  - Done when: The note is approved; a project defines and rebinds its own actions; tests.
  - Decision (human, 2026-11-18): P2, design note first.
  - Decisions (human, 2026-11-18): Q1 `Assets/Data/input.actions.json`; Q2 plain names now; Q3 "Change id…" with a warning; Q4 "active at start" now, an InputContext component later.
  - Deps: none
- [ ] **INP-12** `P2` Input action picker
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): `@InputActionRef` on action-id fields (`CameraCycleComponent.actionId` first) and a dropdown of known actions.
  - Done when: Action fields pick from known actions; test.
  - Deps: INP-11
- [ ] **INP-13** `P3` Rumble test in the Input Testing panel
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): pad, strength, duration, pattern.
  - Done when: Rumble can be tested from the panel; test.
  - Deps: none
- [ ] **INP-14** `P3` InputStateComponent read-only while playing
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): remove it from Add Component and show it read-only (live values) while playing (EDT-106 Q2).
  - Done when: It is not addable and shows live values in Play; test.
  - Decision (human, 2026-11-18): code-only components are hidden from Add Component and shown read-only with live values (also ECS-24).
  - Deps: none
- [ ] **INP-15** `P3` InputContext component
  - Do: (INP-11 Q4, human 2026-11-18): an `InputContext` component that pushes its context while its object is enabled, for games without scripts.
  - Done when: Enabling/disabling the object pushes/pops its context; tests.
  - Deps: INP-11

### Gameplay — `GPL` (`skate-gameplay`)

Round 27+ goals: a fuller skate demo with camera modes and standard control schemes, then plans for new game systems.

- [x] **GPL-20** `P0` Prefab library barrel rolls and clips halfway into the ground
  - Do: Reproduce on master first. Fix the barrel's collider/pivot/mesh offset and its rest state in the prefab library showcase.
  - Done when: The barrel rests on the ground without rolling or clipping; a test checks that the collider bounds match the mesh bounds for library prefabs.
- [x] **GPL-21** `P1` Skateboarding control schemes
  - Do: Ship four default schemes on the INP-10 API: **EA Skate (default)** (flick-it right-stick tricks), Session (each stick a foot), Skater XL (sticks = feet, triggers turn), THPS (face buttons + direction combos). Selectable in the pause menu settings and persisted.
  - Done when: Each scheme can perform the core trick set; scheme definitions have tests.
  - Deps: INP-10
- [x] **GPL-22** `P1` MainScene Camera button cycles FPV, third person and fixed cameras
  - Do: Replace the Restart button in the MainScene HUD with a Camera button that cycles first-person, third-person and fixed cameras (also bound to an input action).
  - Done when: The button and the action cycle the three cameras in editor Play and the exported game; tests cover the cycle.
  - Deps: GPL-20
- [~] @skate-gameplay **GPL-23** `P2` Fuller skate demo — design approved with changes 2026-11-06 (`docs/design/GPL-23.md`)
  - Do: Human decisions: 40 × 40 m park with the four zones and the new prefabs (Euro-gap deck); combo chaining (link window, manual linking, lose on bail) but **no points/score yet**; **Free skate only** (no Session mode, no timer, no goals yet); control hints on screen with a pause-menu switch; the third camera mode becomes spot cameras that **follow the skater** (no static fixed camera); one HUD for editor Play and the exported game.
  - Done when: MainScene is the new park, skateable end to end in both hosts; combos chain and break correctly; tests.
- [x] **GPL-24** `P3` Game systems and character customization: design note — approved by the human 2026-11-10 (`docs/design/GPL-24.md` rev 2)
  - Do: Write `docs/design/GPL-24.md`: reusable inventory, combat, interaction/pickup systems, and character customization (clothes, tattoos, headwear, eyewear via attachment points, mesh swaps and material layers).
  - Done when: The human approves the note.
- [x] **GPL-27** `P3` Items, inventory, health, interaction and accessories (GPL-24 slices a, b, d, e)
  - Do: Per (`docs/design/GPL-24.md` §8): 24a items and inventory (`*.item.json` in `Assets/Items/`), 24b health and damage (applied synchronously in the `HitEvent` subscriber), 24d interaction and pickup, 24e `SkeletonConfig` (in `Assets/Data/`) with a **dynamic slot list with defaults** (HEAD, EYES, NECK, TORSO, LEGS, FEET, HANDS; add/rename/remove per config) and rigid accessories on the skater. No combat in the skate demo (pickups and cosmetics only).
  - Done when: Each slice's tests in the note pass; a hat and glasses equip on the skater.
  - Progress (2026-11-17): checkpoint with slices 24a (items, inventory) and 24e (skeleton config, hat and glasses on the skater) done; 24b and 24d remain. Other owners: ECS-20, AST-42.
  - Deps: GPL-24, AST-27
- [ ] **GPL-28** `P3` Combat, clothes and tattoos (GPL-24 slices c, f, g)
  - Do: Per (`docs/design/GPL-24.md` §8): 24c `CombatSystem` through the PHY-22 query interface; 24f appearance, skinned clothes (same skeleton required, joint remap by bone name), covered body parts hidden by name; 24g tattoos baked into a per-character texture when the list changes.
  - Done when: Combat works in the Feature Showcase; a jacket and a tattoo apply on the skater; tests.
  - Deps: GPL-27, PHY-22, REN-32, ANI-19, AST-33
- [ ] **GPL-29** `P3` Swimming, including underwater (direction, later)
  - Do: From REN-29 Q4 (human, 2026-11-10): the skater bails in water for now; later the skater swims, including underwater: GPL swim states and controls, ANI swim animations, underwater follow camera, AUD underwater filter (`docs/design/REN-29.md` §8.3).
  - Done when: Design note approved, then the skater swims on and under water.
  - Deps: REN-39
- [x] **GPL-30** `P1` Remove the Camera button from MainScene
  - Do: (human, Discord 2026-11-10): remove the GPL-22 Camera button from the MainScene HUD.
  - Done when: MainScene has no Camera button; tests updated.

### In-game UI — `GUI` (`skate-gameui`)

Round 27+ goals: menu parity between the editor and the game, and a map view.

- [x] **GPL-31** `P2` Publish the animator parameter names the character writes
  - Do: (ANI-21 design answer, 2026-11-10): `CharacterBehavior` exposes the names of the animator parameters it writes (speed, airTime, …) so the Animator window's Parameters panel can suggest them under "Written by the character".
  - Done when: The Animator window lists the character's parameters; tests.
  - Result (2026-11-17): engine side done (`CharacterBehavior.animationParameters`, `CharacterAnimationDriver.writtenParameters`); the window display continues as ANI-26.
  - Deps: ANI-21
- [x] **GPL-32** `P2` Bail and respawn on entering water (REN-29 task 15)
  - Do: (NEEDS from REN-35): a `PlayerController` policy `waterResponse = BAIL` that bails and respawns the player on PHY-16's `WaterEntered`.
  - Done when: Riding into the showcase pool bails and respawns the player; tests.
  - Deps: PHY-16
- [ ] **GPL-33** `P2` Camera components in the editor
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): add CameraFocusComponent and FreeFlyCameraComponent to Add Component, a targets list for CameraFocus, and an object list instead of comma text for `CameraCycleComponent.cameraNames`.
  - Done when: Every camera component is addable and edited without raw text; tests.
  - Deps: EDT-107
- [ ] **GPL-34** `P2` PlayerController inspector
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): behaviour dropdown from the registered behaviours, `configPath` as an asset ref, water response section.
  - Done when: PlayerController is fully editable; tests.
  - Deps: EDT-107, EDT-108
- [ ] **GPL-35** `P2` Control scheme tuning per project
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): trick and flick thresholds per scheme editable and saved per project (INP reviews the flick part).
  - Done when: Thresholds are edited and saved per project; tests.
  - Deps: none
- [ ] **GPL-36** `P2` `.item.json` inspector
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): an inspector for item files (part of or right after GPL-27).
  - Done when: Items are edited and saved from the inspector; tests.
  - Deps: GPL-27
- [ ] **GPL-37** `P2` Prefab and showcase generation without GPU calls
  - Do: (QA-19 audit, `docs/design/QA-19.md`, rule 10): `PrefabsGenerator.createDefaultScenes`, `spawnDefaultsSync` and `FeatureShowcase.populate` use path-only references resolved when the scene opens, so generation is safe on a worker.
  - Done when: Generation runs with an `AssetsManager` that throws on any load call (unit test).
  - Deps: none
- [x] **GPL-38** `P1` Foot IK never runs on the skater (ANI-28 bug F1, slice a)
  - Do: (ANI-28 review, approved (human, 2026-11-18)): the skater's ground foot IK never runs because of wrong bone names. Fix the bone names (or resolve by humanoid slot) and add a test that foot IK runs on the skater.
  - Done when: Foot IK runs on the skater in Play; test.
  - Deps: none
- [~] @skate-gameplay **GPL-40** `P0` REWRITE Player and skater control in the standard ECS way (design first)
  - Do: (human, 2026-11-25): player/skater control is not ECS-standard today (PlayerController holds config, behavior, ground state and foot IK; PlayerMotionSystem drives it). Write `docs/design/GPL-40.md`: split into small data components and systems (input state, motion/intent, ground and character state, skate state, foot IK, camera follow); systems read and write components, no behaviour object with hidden state, everything serialized and shown in the inspector. InputStateComponent is part of the refactor (who writes it, who reads it, how the project input actions of INP-11 feed it). Include migration of saved scenes and prefabs and the MainScene and Feature Showcase impact. Folds in GPL-34 and ANI-34. Needs human approval before code.
  - Done when: The human approves the design; the rewrite then lands with tests, the Skater is fully editable in the inspector, and walking, riding and tricks behave as before.
  - Deps: EDT-107, EDT-108
- [ ] **GPL-39** `P2` Board sockets as IkTargets; skater preset feet, hands and look-at (ANI-28 slice f)
  - Do: (ANI-28 §6 slice f): `IkTarget` GameObjects on the board prefab (front/back foot sockets); the Skater IK preset drives feet while riding, hands for grabs/handplants, and look-at the landing; tests.
  - Done when: The skater's feet track the board sockets while riding; the foot IK targets follow the sockets; tests.
  - Deps: ANI-28b, ANI-30
- [x] **GUI-12** `P0` Esc in the exported game shows the same menu as editor Play
  - Do: Reproduce on master first. Find the remaining differences between the exported game's Esc menu and the editor Play pause menu (GUI-09) and make them identical (items, layout, behaviour).
  - Done when: Both show the same menu; a test compares the menu definitions used in both paths.
- [x] **GUI-13** `P2` Map view in the Feature Showcase
  - Do: Minimap widget and a full-screen map (top-down render or generated texture, player marker, POI markers), demoed in the showcase.
  - Done when: The map shows the player's live position; tests cover the world↔map transform.

- [x] **GUI-14** `P0` Esc still closes the exported game instead of opening the pause menu
  - Do: Found by the human testing PR #29 (GUI-12 did not fix it). Find what closes the window on Esc in the exported game (e.g. `engine/core/WindowController.kt` `glfwSetWindowShouldClose`, a GLFW key callback, or `ui.cancel` with no handler) and route Esc to the pause menu exactly as in editor Play. Lead-assigned; skate-gameui reviews.
  - Done when: Esc in the exported game opens the pause menu and Esc again resumes; Quit is the only way to close from the menu; test on the key path.

- [x] **GPL-25** `P0` GPL-21 review fixes: saved control scheme applied at startup
  - Do: From the GUI review of GPL-21 (`/tmp/skate-gameui-reviews.md`): must fix — the saved option is applied before the settings store attaches, so the default scheme runs while the menu shows the saved one (re-apply when the store changes; test). Should fix — cap the pause panel height, apply options once, cache the HUD text components, find the rider recursively. Lead-assigned to skate-gameui; skate-gameplay reviews.
  - Done when: The saved scheme is active from the first frame in both hosts; tests.
- [x] **GUI-15** `P1` Exported game reads the project's window and runtime settings
  - Do: From EDT-57: the exported game reads resolution, window title, fullscreen, supported languages, startup language and the project physics rate from the project settings.
  - Done when: Each setting changed in Project Settings takes effect in an exported game; tests.
- [x] **GUI-16** `P2` Reset ImGui capture hooks when the ImGui context is destroyed
  - Do: From the GUI-14 review: `ImGuiLayer.destroy()` must reset `captureKeyboard`/`captureMouse` to `{ false }` before destroying the context, so input read after shutdown cannot end the JVM.
  - Done when: Reading input after `destroy()` is safe; test.

- [x] **GUI-17** `P2` Player frame-rate option in the game's settings menu
  - Do: From AST-15 Q4 (human, 2026-11-10, v1): a frame-rate option (project cap, 30, 60, 120, 144, uncapped) in the in-game settings menu, stored in `GameSettings` and overriding the project's target FPS.
  - Done when: The option persists and changes the frame cap in the exported game; tests.
  - Deps: AST-29
- [ ] **GUI-18** `P3` Inventory and equipment screen (GPL-24)
  - Do: Per (`docs/design/GPL-24.md` §8): inventory and equipment screen (move, equip, durability), gamepad navigable, driven only by the inventory request/result events.
  - Done when: Items can be moved and equipped with mouse and gamepad; tests.
  - Deps: GPL-27
- [ ] **GUI-19** `P2` Prefetch streamed terrain behind the loading screen (REN-37 task 21)
  - Do: (NEEDS from REN-37): Play and the exported game call `TerrainStreamingSystem.streamer(terrain)?.prefetch(spawnX, spawnZ, rings)` behind the loading screen, with radii scaled by the graphics quality.
  - Done when: Play starts with the spawn area loaded, no pop-in; tests.
  - Deps: none (needs the REN-37 checkpoint, round 38)
- [x] **GUI-20** `P1` The editor Play pause menu differs from the exported game's
  - Do: (human, Discord 2026-11-17): the in-game menu in editor Play is different from the exported game's menu. List every difference (rows, order, styling, actions) after ECS-18's frame-rate row and make them identical; extend PauseMenuParityTest to compare the full menus.
  - Done when: Both hosts build the same menu; parity test covers every row and style.
  - Deps: none
- [ ] **GUI-21** `P2` Map components in the editor
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): UIMapComponent and UIMapMarkerComponent in the Create UI menu, `@GameObjectRef` on `target`, `openButton`, `closeButton`.
  - Done when: Maps are created and wired from the editor; tests.
  - Deps: none
- [ ] **GUI-22** `P3` Font asset preview and settings
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): preview, size and atlas settings.
  - Done when: Fonts preview and their settings save; test.
  - Deps: none

### Scripting — `SCR` (`skate-scripting`)

Scripting needs to be implemented. Kotlin Script is the primary language; TypeScript and maybe Lua come later.

- [ ] **SCR-07** `P1` Compile scripts off the main thread, with a disk cache
  - Do: (QA-19 audit, `docs/design/QA-19.md`, rule 10): `ScriptRuntime` compiles on a `JobSystem` worker and starts the script (`onCreate`) on the main thread when ready; Play keeps drawing with "Compiling scripts…" shown. Precompile open scenes' scripts on open and save; give the host a `cacheDir` under the project cache. Hot reload uses the same path. Measured 7–13 s cold, 0.45–0.77 s warm on the main thread today.
  - Done when: The main thread never calls `KotlinScriptHost.load`/`reload`; a fake blocking host shows `ScriptRuntime.update` returning without waiting.
  - Deps: none
- [ ] **SCR-08** `P2` Script parameters in the inspector
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): show and edit `ScriptComponent.properties` from the script's declared parameters.
  - Done when: Declared parameters are edited with undo; tests.
  - Deps: EDT-107
- [ ] **SCR-09** `P3` Open `.glsl` files in the Script Editor
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor).
  - Done when: Shaders open in the built-in Script Editor; test.
  - Deps: none
- [ ] **SCR-10** `P3` `pushContext(id)` with project defaults
  - Do: (NEEDS from INP-11): an optional `pushContext(id)` overload in the script API that uses the project's context defaults.
  - Done when: Scripts push project contexts by id; test.
  - Deps: INP-11


### Networking & platform — `NET` (`skate-online`)

Multiplayer and Steam support need to be added.

- [~] @skate-online **NET-07** `P2` Publish to Steam from the editor (named NET-01 in commits; renumbered because NET-01 is archived)
  - Do: (human, Discord 2026-11-17): publish an exported game to Steam from the editor (app and depot IDs, branch, build description, SteamPipe/steamcmd upload with progress and log). Design note `docs/design/NET-07.md` first (credentials handling, steamcmd install, Steamworks SDK licensing); ask for approval before code.
  - Done when: The note is approved; a build uploads to a Steam branch from the editor.
  - Decisions (human, 2026-11-18): steamcmd cached session only (no stored password); managed steamcmd download after confirmation; never set a branch live automatically; wait for AST-31 (native launcher) before Steam upload; one Windows depot; the Steamworks spike waits until the human has time (do everything else first); editor + `skate publish steam` CLI. Note renamed to `docs/design/NET-07.md`.
  - Deps: AST-43, AST-31
- [ ] **NET-08** `P3` Networking components in the editor
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): networking components and `NetConfig` in the editor, once multiplayer is user-facing (deferred).
  - Done when: Networking setup is editable; tests.
  - Deps: none


---

## Editor — `EDT` (`skate-editor`)

Editor UIs for a domain's own back end may be written by that domain's agent and reviewed by `skate-editor` (human, 2026-11-17).

Round 27+ goals (human, 2026-11-06): fix editor bugs, make every window, dialog and menu consistent, add beginner help, and overhaul project creation. EDT tasks may be lead-assigned to other agents (rule 1).

- [x] **EDT-44** `P0` Camera/light editor icons change shape with the camera; light icons ignore light color
  - Do: Reproduce on master first. Camera and light representations keep a fixed shape and a constant screen size when the editor camera moves or rotates (billboards and wire gizmos). Light icons/gizmos are tinted with the light's color.
  - Done when: Icons keep their shape under any camera motion; light icons follow the light color live; tests cover the billboard/size math and the tint.
- [x] **EDT-45** `P0` Play-mode lifecycle: Stop, Pause and Reset semantics; stop on close; toolbar stays stable
  - Do: Reproduce on master first. **Stop** restores the saved scene state; **Pause** keeps the current state; **Reset** restores everything to its saved state (transforms, physics bodies, animators, audio, scripts, day/night, UI). Closing a project or quitting the editor stops the simulation, so the next project opens stopped. During Play, gizmo buttons are disabled (grayed out) instead of hidden, so the toolbar does not shift. ECS reviews the snapshot/restore.
  - Done when: Each control behaves as specified, opening another project never starts in Play, and the toolbar layout is identical in Edit and Play; tests cover the state machine and the restore.
- [x] **EDT-47** `P0` Console scrolling
  - Do: Reproduce on master first. Manual scrolling up turns auto-scroll off; scrolling back to the bottom turns it on again; the scrollbar works.
  - Done when: The behaviour holds with a fast-logging scene; the auto-scroll state logic has tests.
- [x] **EDT-48** `P0` Search Anywhere input cannot be focused
  - Do: Reproduce on master first. The text field gets keyboard focus when Search Anywhere opens and can be clicked into; Esc closes it.
  - Done when: You can type immediately after opening it; tests where feasible.
- [x] **EDT-49** `P0` Delete-component confirmation dialog shows twice or has a broken layout
  - Do: Reproduce on master first. One modal with a proper layout when deleting a component; Cancel and Confirm both behave correctly.
  - Done when: Exactly one well-laid-out dialog appears; tests cover the popup state.
- [x] **EDT-50** `P0` Missing editor strings
  - Do: Find hardcoded UI text and keys missing from `assets/strings/**` (all locales) and fix them. Extend the key test so any missing key or hardcoded label fails.
  - Done when: No raw keys or missing strings in the editor; the test enforces it.
- [x] **EDT-51** `P1` Editor consistency audit: design note — approved 2026-11-06 (human decisions + lead approval of the chunk plan, `docs/design/EDT-51.md`)
- [x] **EDT-52** `P1` Apply the editor consistency standard (all chunks A–I done; allow-list empty, round 30)
  - Do: Apply EDT-51 in the chunks of `docs/design/EDT-51.md` §5: A (foundation, alone, first), then B scene, C project & assets, D debug, E settings dialogs, F modals & palette, G menus, H domain windows (AUD, SCR) in parallel, then I input-testing move (INP, after E). Chunk A: skate-editor.
  - Done when: Every item in the EDT-51 inventory meets the standard; a test checks windows register header icon, help and context menu.
  - Deps: EDT-51
- [x] **EDT-53** `P1` Beginner help in every window — content as Markdown articles (skate-editor reviews) — 6 articles merged in round 30; still missing: console, profiler, render_graph, script_editor, project_settings, project_wizard, asset_browser, audio_inspector, keybindings; rename `getting-started.md` to `getting_started.md`
  - Do: Redirected by the human 2026-11-07 (see EDT-68): write the plain-language explanations (what it is, how to use it, related windows), written for someone who never used a 3D program, as **one Markdown file per window/topic** under `assets/help/en/**` and `assets/help/fr/**` (English fallback). The articles are consumed by the EDT-68 help window; no tooltip/popover text.
  - Done when: Every registered window has a Markdown article; a test checks coverage and that each article parses.
  - Deps: EDT-51
- [x] **EDT-54** `P1` REWRITE Render Graph window overhaul — design approved by the lead 2026-11-06 (`docs/design/EDT-54.md` rev 2, reviewed by skate-render)
  - Do: Step 2a (engine API: `RenderGraphInfo`, `PassTimings`, signature-based version, all post effects as stages, `lbl.render_pass.*` keys; skate-vulkan, skate-render reviews), then step 2b (the window, after EDT-52 chunk A so it follows the new window standard). Step 2c (per-effect GPU timers) deferred. The window opens docked by default as a background tab next to Console.
  - Done when: The human approves the design and the window is usable on MainScene and the showcase; layout logic tested.
  - Deps: REN-24
- [x] **EDT-55** `P1` Script editor shown by default and refined
  - Do: Dock the script editor by default; add tabs, unsaved-state markers, an error gutter (from script compile errors), find/replace and go-to-line.
  - Done when: The script editor is visible in the default layout and the features work; tests for the editor state logic.
- [x] **EDT-56** `P1` Loading screens for long operations
  - Do: A modal progress/loading screen for creating and opening projects, loading scenes and exporting, without blocking the UI thread.
  - Done when: Each long operation shows progress and the UI stays responsive; tests for the progress model.
  - Deps: EDT-51
- [x] **EDT-57** `P1` Missing project and editor settings; tooltip toggle
  - Do: Add the settings listed in EDT-51 to the project and editor settings windows, including "Show tooltips" (persisted, honored by every tooltip).
  - Done when: All listed settings exist and persist; the tooltip toggle works everywhere; tests.
  - Deps: EDT-51
- [x] **EDT-58** `P1` Remember the editor camera per scene
  - Do: Save the editor camera position/rotation/mode per scene (editor-only data, not in the exported game) and restore it when the scene opens.
  - Done when: Switching scenes and restarting restores each scene's camera; tests.
- [~] @skate-tooling **EDT-59** `P1` REWRITE New Project wizard and templates — design approved by the human 2026-11-10 (`docs/design/EDT-59.md` rev 2); implementation after AST-27
  - Do: Design note first. Templates (Empty, Skate game, Feature Showcase, Tetris, Marble Run, later demos), which separates the skate game from the showcase. Settings: target FPS/uncapped, physics backend (Bullet, Simple, Jolt once PHY-17 lands), graphics backend (exists), include AI agent/skill files, splash screen image. Templates come from skate-tooling.
  - Done when: The human approves the design; each template creates a project that opens and runs; tests for the template registry and settings.
  - Deps: EDT-56, AST-14, AST-27
- [~] @skate-ecs **EDT-60** `P2` Git version control (phases 0 and 1 done; later phases: GitHub sign-in, LFS) — approved 2026-11-06 (`docs/design/EDT-60.md`)
  - Do: Phase 0 (ECS/AST): persistent GameObject ids so an unchanged scene saves byte-identical; load→save→load→save test on MainScene and the Feature Showcase. Phase 1 (EDT): local repository panel with JGit (init, status, diff, commit, history, branches, discard, conflict-safe loading; no Git install needed). Conflicts: whole-file "Keep mine / Take theirs" for now (human). Human decisions for later phases: GitHub sign-in supports **both** browser sign-in (a registered "Skate" OAuth app, set up with the human when phase 2 starts) and pasting a personal access token; **Git LFS on by default** for new projects (models, textures, audio).
  - Done when: Phase 1 commits and diffs a project locally; tests.
## Testing & tooling

- [x] **EDT-61** `P0` Search Anywhere does not find asset files (e.g. `james.glb`)
  - Do: Found by the human testing PR #29. Asset files (models, textures, sounds, animations, prefabs, scripts) must be searchable by file name; opening a result selects/reveals it in the Project window or Asset Browser.
  - Done when: Searching "james" finds `james.glb`; tests on the asset search provider.
- [x] **EDT-63** `P0` Switching scenes during Play stops Play
  - Do: Human request (PR #29 testing): when the user changes the scene while Play runs (scene tab, Open Scene, Project/Asset double-click), stop Play first (restoring every scene, EDT-45) and then switch. Scene switches made by game scripts during Play keep playing.
  - Done when: A user scene switch during Play ends in Edit mode with all scenes restored; script switches still work; tests.
- [x] **EDT-62** `P1` Delete confirmation for game objects and children
  - Do: Human request: deleting a GameObject (Hierarchy, Viewport, Delete key) shows the standard `EditorModal` confirmation naming the object and how many children it has; Esc = Cancel; a "Don't ask again" option tied to the EDT-51 §4.1 "Ask before deleting" setting.
  - Done when: Every delete path confirms first; undo still restores; tests on the confirmation state.


- [x] **EDT-64** `P1` EDT-52 follow-ups from the chunk reviews
  - Do: Centre `EditorDialog` on first use (`ImGuiCond.FirstUseEver`); expose the footer height from `DialogFooter`; remove the unused export/project-settings keys and the `WindowCategory` entries of the three settings dialogs; let `WindowStandardGuardTest` accept modal hosts so `ProjectSwitcherDialog` and `ProjectWizardWindow` leave the allow-list.
  - Done when: The listed items are done and the guard allow-list shrinks accordingly; tests.
  - Deps: EDT-52

- [x] **EDT-65** `P1` Tooltip calls honour the Show tooltips setting everywhere
  - Do: From EDT-57: convert the 14 files listed in `TooltipToggleGuardTest`'s allow-list (chunks C, D, G, H, I and EDT-54) to `Tooltips.text` (control help) or `Tooltips.always` (explanations, data), and empty the list.
  - Done when: The allow-list is empty; tests.
  - Deps: EDT-52, EDT-57
- [x] **EDT-66** `P2` Blender import settings in the inspector and Editor Settings
  - Do: From AST-19: an inspector panel for `.blend` import settings (`blender.*` meta keys) with a Re-import button (`BlenderSync.reimport`), a "Needs Blender" badge, and a Blender section in Editor Settings (path, timeout, import on open, a Test button using `BlenderLocator`).
  - Done when: Settings edit and persist; Re-import works; tests.
  - Deps: AST-19
- [x] **EDT-67** `P1` Help button on every window and unclipped button background
  - Do: From the human testing the window standard (2026-11-07): only **some** windows show the `?` help button, and on the ones that do the **button background is clipped at the top**. Give every registered window the control and fix the clipping (padding/rect/clip-space) at any DPI and window size. The fix must work with the pending EDT-52 chunk branches (C, E, F, G, H, I) and EDT-54, which the lead integrates before final verification.
  - Done when: Every registered window shows a correctly rendered `?` button; a test asserts coverage and the layout math is covered.
  - Deps: EDT-52
- [x] **EDT-68** `P1` Help window with Markdown articles and a Help menu item — design approved 2026-11-10 (`docs/design/EDT-68.md`)
  - Do: From the human (2026-11-07): clicking `?` must open a **help window** showing the article for the selected topic — **not** a bigger tooltip/popover. The window is docked like other editor windows, lists the topics (table of contents) and renders the `.md` articles written by EDT-53. Add a **Help** menu item that opens the same window independently of any `?` button. Implement the approved design note.
  - Done when: `?` opens the window on the right topic, the Help menu item opens it with the topic list, articles are `.md` files under `assets/help/**`, and tests cover article discovery/loading, the menu entry and the `?` routing.
  - Deps: EDT-53
- [x] **EDT-69** `P1` Asset Browser: one tab per asset type
  - Do: Per AST-26: tabs for Models, Shaders, Sounds, Textures, Materials, Scripts, Animations, Prefabs, Fonts, Data and Items (current tabs: Prefabs, Animations, Textures, Sounds…; keep Prefabs unless AST-26 says otherwise), each with the shared search, empty states and context menu of EDT-52C; engine defaults shown whether copied or not.
  - Done when: Every type folder has a tab listing its files; tests.
  - Deps: AST-27
- [x] **EDT-70** `P1` New Project: copy the engine default assets or not
  - Do: From the human (2026-11-10): a project-creation setting choosing whether `EngineDefaults` is copied into the project's assets folder; the behaviour of each mode comes from AST-26. Fold into the EDT-59 wizard design if it is approved first, otherwise add to the current wizard.
  - Done when: Both choices create a project that opens, plays and exports; the choice is a creation-time action, not stored (EDT-59 Q2, human 2026-11-10); tests.
  - Deps: AST-27
- [ ] **EDT-71** `P2` Map widget and city dialog (AST-17 slice b)
  - Do: Per `docs/design/AST-17.md` rev 3 §8: generic ImGui map widget (`MapView`, `WebMercator`, `AreaSelection`), the city dialog with link/coords/search and the embedded map, feature overlay.
  - Done when: An area can be picked on the map and generated; widget maths tested.
  - API (from AST-34, 2026-11-17): build the dialog on `OsmMapServices` (tiles, attribution, `overpass()`), `OsmLink`, `GeoBox` (square/clamped) and `CityBuilder.stats()`, then call `CityGenerator.generate/regenerate/outdatedCities`. Also: an undoable command adding `PrefabService.instantiate(result.prefab)` to the scene, the Simple-backend note, the `CityGenerator.ATTRIBUTION` line, and Editor Settings fields for `osmTileUrl`/`osmTileAttribution`/`overpassUrl` plus "Clear map cache" (`clearCache()`).
  - Deps: AST-34
- [x] **EDT-72** `P0` `?` help button still clipped at the top
  - Do: (human, Discord 2026-11-10): after EDT-67 the `?` button on every editor window is still clipped at the top (likely header padding/clip rect). Reproduce at 100 % and 150 % UI scale and with EDT-76's padding settings; fix the shared header layout, not per window.
  - Done when: The human sees an unclipped `?` on every window; a layout test covers the header rect.
  - Deps: EDT-67
- [x] **EDT-73** `P1` Editor camera moves only when the Game Viewport is focused
  - Do: (human, Discord 2026-11-10): WASD/mouse fly, orbit and zoom of the editor camera act only while the Game Viewport window is focused (hovered for the wheel), never while typing or working in another window.
  - Done when: Keys typed in other windows never move the camera; tests on the input gating.
- [x] **EDT-74** `P1` Multiple selection in the Scene Hierarchy (Ctrl/Shift-click)
  - Do: (human, Discord 2026-11-10): Ctrl-click toggles, Shift-click selects a range; Properties shows shared components; delete, duplicate, copy/paste, drag-reparent and the move/rotate/scale gizmo act on the whole selection as one undo step.
  - Done when: Multi-select works in the Hierarchy and the viewport with undo; tests for selection and the multi-object commands.
- [x] **EDT-75** `P1` Gizmos for UI items
  - Do: (human, Discord 2026-11-10): select a runtime UI element (canvas child) in the viewport and move/resize it with a 2D gizmo (anchors, pivot, rect), with snapping and undo; skate-gameui reviews.
  - Done when: UI elements can be moved and resized in the viewport with undo; tests for the rect maths.
- [x] **EDT-76** `P1` Menu and button padding; Interface settings review
  - Do: (human, Discord 2026-11-10): menu items and some editor buttons have no padding. Review Editor Settings › Interface and add appearance options (item/frame padding, spacing, rounding, font size, density presets), applied everywhere through the theme, persisted.
  - Done when: Menus and buttons have padding by default; each new option visibly changes the editor and persists; tests.
- [x] **EDT-77** `P1` Project Settings › Random: seed field too wide
  - Do: (human, Discord 2026-11-10): the seed text field overflows the window; size it like the other rows.
  - Done when: The Random page fits at any dialog width; a layout test.
- [x] **EDT-78** `P2` Viewport toolbar: World and Snap at the end, before `?`
  - Do: (human, Discord 2026-11-10): move the World/Local and Snap controls to the end of the Game Viewport toolbar, just before the `?` button.
  - Done when: The toolbar order matches; toolbar tests updated.
- [x] **EDT-79** `P2` REWRITE Git branch button in the status bar, replacing the Version Control window
  - Do: (human, Discord 2026-11-10): an IntelliJ-like branch button in the bottom status bar (current branch; popup with branches, new/checkout/delete, update, commit and changes entries) replaces the EDT-60 Version Control window. Design note first: where changes, diffs, commit and conflict resolution live once the window is gone.
  - Done when: The human approves the note; then the status-bar workflow covers every EDT-60 phase 1 action and the window is removed; tests.
  - Deps: EDT-60
- [x] **EDT-80** `P1` Shader field in the material inspector and a "New Shader" action
  - Do: From REN-31 (skate-render): a material inspector "Shader" field listing the project `.glsl` files plus the basic engine shaders (writes `MaterialAsset.shader`/`Material.shader`); an Asset Browser "New Shader" action writing `ShaderTemplates.PBR_MATERIAL` to `Assets/Shaders/<name>.glsl`; copy `shader` in `MaterialState` (`MaterialCommands.kt`) so material undo keeps it.
  - Done when: A new project shader can be created, assigned to a material and undone; tests.
  - Deps: REN-31
- [x] **EDT-81** `P0` Select tool cannot pick runtime UI elements in the viewport
  - Do: (human, round 32 testing): with the Select tool, clicking a UI element (e.g. the Map button in the Feature Showcase) does not select it, so EDT-75's UI gizmo never appears. Make viewport picking hit-test canvas children (top-most first) before 3D objects.
  - Done when: Clicking the Feature Showcase Map button selects it and shows the UI gizmo; tests for the UI hit-test order.
  - Deps: EDT-75
- [x] **EDT-85** `P0` Window header clamp breaks scrolling in every window
  - Do: (from ANI-22, round 32 testing): EDT-72's clamp in `HelpButtonLayout.row` keeps the header 2px below the window top even when the window is scrolled, so windows that scroll their whole body below `header()` snap back. Apply the clamp relative to the scroll position.
  - Done when: A window using `header()` scrolls at a small size and the `?` stays unclipped at 100 %/150 %; guard test.
  - Deps: EDT-72
- [x] **EDT-86** `P0` Inspector buttons after a field are clipped or pushed out
  - Do: (human, round 33 testing): texture Delete buttons clipped at the end; the Animator component's Edit/Refresh buttons sometimes have no room. Shared trailing-button layout sized from the current style.
  - Done when: Trailing buttons fit at every density preset and narrow widths; guard test.
  - Deps: EDT-76
- [x] **EDT-87** `P0` Expanding the Input State component crashes the editor
  - Do: (human, round 33 testing): expanding Input State in the Properties window crashes the editor. Fix it, and add a guard test that draws every registered component inspector expanded (default and populated data) with no ImGui stack imbalance or exception.
  - Done when: Input State expands without crashing; the all-inspectors guard passes.
  - Deps: EDT-86
- [x] **EDT-88** `P1` Maximize editor tool windows over the Game Viewport, or full screen
  - Do: (human, 2026-11-11): the Script Editor, Animation Editor and Video to Animation windows can be maximized: a "Maximize" button in the standard window header (and a shortcut, e.g. Shift+Space, plus double-click on the tab) that either docks the window over the Game Viewport's node as a tab, or fills the whole editor (hiding the other panels until restored). Today docking over the viewport doesn't work because the Game Viewport's dock node hides its tab bar, so nothing can be tabbed into it: show the Game Viewport tab (or allow docking into its node) without breaking its toolbar layout. Restore returns the previous layout exactly. Generalize to every window using the standard header if it costs nothing extra. Short design note first (layout save/restore, interaction with play mode and the viewport's input focus from EDT-73).
  - Design (`docs/design/EDT-88.md`, approved 2026-11-17, all recommendations accepted): default mode Full editor (plus an Editor Settings › Interface choice), "Over Game Viewport" in the tab menu; shortcut Shift+Space (rebindable; ignored while typing or with the viewport focused); always start restored; Stop doesn't re-maximize; Restore over the viewport discards other docking changes; full-editor mode keeps separate ImGui scroll/tree state; every docked standard window except the Game Viewport; F12 undo fix split out to EDT-89.
  - Done when: Each of the three windows can be maximized and restored, both over the viewport and full editor; the layout is restored exactly; tests for the layout save/restore.
  - Deps: EDT-67
- [ ] **EDT-89** `P3` F12 viewport full screen becomes view state, not an undoable Command
  - Do: (EDT-88 design answer, 2026-11-17): `ToggleFullScreenCommand` changes no project data but adds Undo entries. Make viewport full screen plain view state (like EDT-88's maximize) and remove it from the undo stack.
  - Done when: F12 toggles full screen without adding Undo entries; tests.
  - Deps: EDT-88
- [x] **EDT-90** `P2` Water inspector and Environment wind section (REN-29 task 10a)
  - Do: (NEEDS from REN-35): water inspector (presets `WaterPreset`, per-wave editor, material picker, volume gizmo, Freeze time via `EnvironmentComponent.animateInEditor`) and an Environment wind section with presets (`WindField.CALM/BREEZE/STRONG/STORM`). Wind strings already exist in `components.properties`; the selected-water volume box already draws through the debug renderer.
  - Done when: Water and wind can be tuned from the inspector with undo; tests.
  - Deps: REN-35
- [x] **EDT-91** `P2` Export dialog UI for the AST-29 settings
  - Do: (NEEDS from AST-29): in `ExportWizardWindow`, build the wizard with `ExportWizard(ExportMemory.inSettings({ settingsManager.editorSettings }, settingsManager::update))`; add the Game/Build/Summary sections from AST-29 §3 using `ProjectExporter.art()`/`estimate()`, show `ExportResult.warnings` (keys `lbl.export.warning.*`), and pass art to `canStart`/`issues`.
  - Done when: Each export setting is editable in the dialog and persists; missing icon/splash warnings show; tests.
  - Deps: AST-29
- [~] @skate-vulkan **EDT-92** `P2` Terrain inspector and tools (REN-28 task 10)
  - Do: (NEEDS from REN-36): terrain inspector (presets via `TerrainComponent.applyPreset`), sculpt and paint brushes writing `TerrainChunkData.setHeight` then `runtime.markEdited()`, a stamp gizmo, "Flatten terrain under", all undoable.
  - Done when: A terrain can be sculpted, painted and stamped from the editor with undo; tests.
  - Also (REN-36 tasks 5–7): inspectors for biomes, layer rules, scatter rules, layers and climate; a toggle for `TerrainDebug.showBiomes`; biome paint writing `paintedBiomeIds` with `biomeSource = PAINTED`, then `runtime.markEdited()` (sculpting calls it too and now recomputes splat and scatter). (AST-36): create a terrain asset from the inspector with `TerrainAssetStore.create(<project>/Assets/Data, name, terrain)` and call `TerrainAssets.save(terrain)` on scene save and after sculpt strokes.
  - Also (REN-42): when the editor creates a TerrainComponent, write `seedName = "terrain/<entity name>"` (Add Component path or this inspector) so renaming the entity keeps its ground (`key()` no longer does it); show a note when `spacingCapped()` is true.
  - Design (`docs/design/EDT-92.md`, approved 2026-11-17): tools picked in the inspector only; the first stroke auto-creates the terrain asset; brushes Raise, Lower, Smooth, Flatten and Paint biome (Noise and Ramp in EDT-97); stamps edited with viewport handles plus fields; "Flatten terrain under" in the object context menu; the biome, layer-rule, scatter-rule and climate inspectors are split out to EDT-96.
  - Deps: ECS-17, AST-36
- [ ] **EDT-93** `P2` Asset Browser: copy or replace engine assets (AST-26 §8)
  - Do: (NEEDS from EDT-69): "Copy into project" and "Replace with engine version" as undoable Commands (`EngineAssetCopier.copy` exists), plus the "update available" notification.
  - Done when: An engine asset can be copied into the project or reset to the engine version with undo; tests.
  - Deps: EDT-69
- [x] **EDT-94** `P2` Hide runtime-only children (terrain scatter) in the editor
  - Do: (NEEDS from ECS-19): `SceneHierarchyWindow` (and the `ScenePlaySnapshot` selection paths and viewport picking) list `GameObject.persistentChildren` instead of `children`, so runtime-only terrain scatter entities (`isRuntimeOnly`) are hidden; they are never saved.
  - Done when: Scatter children never show in the Hierarchy or get picked; tests.
  - Deps: ECS-19
- [ ] **EDT-95** `P2` Pin edited terrain chunks (REN-37 task 20, EDT part)
  - Do: (NEEDS from REN-37): add a chunk to `runtime.pinned` on its first edit and remove it on save, with an inspector note when the detail radius was reduced.
  - Done when: Edited chunks never unload before save; tests.
  - Also (REN-37): warn before a reroll when `runtime.hasEdits`, offering `discardEdits()`.
  - Deps: EDT-92 (needs the REN-37 checkpoint, round 38)
- [ ] **EDT-96** `P2` Terrain biome, layer-rule, scatter-rule and climate inspectors (split from EDT-92)
  - Do: (EDT-92 design answer, 2026-11-17): inspectors for biomes, layer rules, scatter rules, layers and climate; a toggle for `TerrainDebug.showBiomes`.
  - Done when: Every biome, layer, scatter and climate setting is editable with undo and saved in the terrain asset; tests.
  - Also (REN-37): generate a unique `ScatterRule.id` when a rule is created (keys and seeds depend on it).
  - Deps: EDT-92
- [ ] **EDT-97** `P3` Terrain Noise and Ramp brushes
  - Do: (EDT-92 design answer, 2026-11-17): Noise and Ramp sculpt brushes alongside EDT-92's core set.
  - Done when: Both brushes sculpt with undo; tests.
  - Deps: EDT-92
- [x] **EDT-98** `P0` Sounds tab of the Asset Browser crashes when an item is selected
  - Do: (human, Discord 2026-11-17): selecting an item in the Sounds tab (EDT-69) crashes the editor. Reproduce in a headless test, fix it, and guard every Asset Browser tab against selecting each item type.
  - Done when: Selecting any item in any tab works; guard test.
  - Deps: EDT-69
- [x] **EDT-99** `P1` The + button creates two scenes instead of one
  - Do: (human, Discord 2026-11-17): creating a new scene with the + button (or tab) creates two scenes. Fix it with a test on the create-scene path.
  - Done when: One click creates exactly one scene, with undo; test.
  - Deps: none
- [x] **EDT-100** `P1` Windows docked on the right clip their values
  - Do: (human, Discord 2026-11-17): with the Properties window docked on the right, Transform values sit too far right and some Environment values can't be reached; the same happens in many editor windows. Find the shared cause (label column width, fixed offsets, missing scroll), fix it, and add a guard that draws every inspector and window at narrow widths with no clipped or unreachable field. Also add the Environment component's missing strings (EN, FR).
  - Done when: Every window works docked on the right at the narrowest default width; Environment has no raw keys; guard test.
  - Deps: none
- [~] @skate-editor **EDT-101** `P1` Window header: help and maximize buttons next to the close button
  - Do: (human, Discord 2026-11-17): the `?` button sometimes sits alone on its own row and wastes space. Move `?` and Maximize/Restore (EDT-88) into the window's title bar or tab, next to the close button, if ImGui docking allows; otherwise keep them on the first content row and never wrap them alone. Short design note if the title-bar route needs custom drawing.
  - Done when: No window shows `?` alone on a row; both buttons sit next to the close button where possible; HelpButtonClipTest extended.
  - Design (`docs/design/EDT-101.md`, approved 2026-11-18): option B (title bar for floating windows and dialogs; docked windows keep the buttons on their tools row, or over the content's top-right corner when they have no tools, never alone on a row) and a rebindable F1 for Help.
  - Deps: EDT-88
- [ ] **EDT-102** `P2` Every color property uses a color picker
  - Do: (human, Discord 2026-11-17): every component color property in the Properties window uses the color picker (ColorEdit with picker), not raw numbers. Add a guard test over all registered components.
  - Done when: The guard finds no color field without a picker; test.
  - Deps: none
- [ ] **EDT-103** `P2` Editor FPS counter over the Game Viewport, gray with a white outline
  - Do: (human, Discord 2026-11-17): move the editor FPS counter so it sits over the Game Viewport (it is too far left), draw it gray instead of green, with a white outline if possible.
  - Done when: The counter is inside the viewport rect at every layout, gray with an outline; test for its placement.
  - Deps: none
- [ ] **EDT-104** `P2` Export button in the top menu
  - Do: (human, Discord 2026-11-17): add an Export button to the left of the Settings button in the editor's top menu; it opens the export dialog (EDT-91).
  - Done when: The button opens the export dialog; tooltip and EN/FR strings; test.
  - Deps: EDT-91
- [ ] **EDT-105** `P2` Version Control category in Editor Settings
  - Do: (human, Discord 2026-11-17): a Version Control category in Editor Settings for the Git settings (EDT-60/EDT-79: identity, default branch, fetch interval, line endings, …).
  - Done when: The Git settings are editable there and persist; test.
  - Deps: none
- [x] **EDT-106** `P1` Missing editor UI audit
  - Do: (human, Discord 2026-11-17) "Plan all missing editor UI": list every engine component, system setting and asset type that has no editor UI (or only raw fields), and turn each gap into a task in the owning domain (domain agents write UIs for their own back ends, skate-editor reviews). Write the list to `docs/design/EDT-106.md` and report the proposed tasks to the lead.
  - Done when: The audit note exists and every gap has a backlog task.
  - Deps: none
- [x] **EDT-107** `P1` Generic inspector draws lists, maps and nested objects
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): `reflectedImgui` draws `List`/`MutableList` (add, remove, reorder), `Map<String, *>` and nested `@Serializable` data classes recursively, with undo. Unblocks VehicleComponent wheels, ScriptComponent properties, CameraFocusComponent targets, PlayerController `waterResponse`, PrefabInstance overrides.
  - Done when: A guard test finds no saved component field that the inspector silently skips.
  - Deps: none
- [x] **EDT-108** `P0` Asset reference fields: picker and drag and drop
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): an `@AssetRef(kind)` annotation (with skate-ecs, next to `@GameObjectRef`); the inspector shows a picker filtered by kind, accepts drops from the Asset Browser and flags missing files. Domain agents then annotate their fields (particle/UI texture, `configPath`, `meshRef`, `materialPath`, sound paths, …).
  - Done when: Annotated fields show a filtered picker, accept drops and flag missing files; tests.
  - Priority raised to P0 (human, 2026-11-25): RenderComponent's model cannot be changed from the editor, which blocks human testing; do EDT-108 then REN-47 first.
  - Deps: none
- [ ] **EDT-109** `P2` Add Component menu: translated names, categories and search
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): translated component names, categories, and search on the translated name.
  - Done when: Every component shows a translated name in a category and is found by search in EN and FR; test.
  - Deps: none
- [ ] **EDT-110** `P2` Copy and paste component values
  - Do: (EDT-106 audit, `docs/design/EDT-106.md`; UI written by the owner, reviewed by skate-editor): enable the disabled "Copy component" item; paste as a new component or onto the same type, undoable.
  - Done when: Copy/paste works for every component type with undo; test.
  - Deps: none
- [ ] **EDT-111** `P1` Create Project builds its scenes off the main thread
  - Do: (QA-19 audit, `docs/design/QA-19.md`, rule 10): split `ProjectManager.createProject` so generating, saving and decoding the default scenes happen in the background step; the main step only closes the old project and opens the decoded scenes. Per-file copy progress; move the dirty-scene saves in `closeProject` behind the loading screen.
  - Done when: The main step of Create Project is under 100 ms with fake assets (`EditorResponsivenessTimingTest`).
  - Deps: GPL-37, AST-47
- [ ] **EDT-112** `P2` New projects get an input actions asset
  - Do: (NEEDS from INP-11): new projects get `actionsPath` and an empty `Assets/Data/input.actions.json`; skate-editor also reviews INP-11's Key Bindings UI.
  - Done when: A new project has the actions asset and opens it in Key Bindings; test.
  - Deps: INP-11
- [~] @skate-editor **EDT-113** `P1` Tool window sidebar polish: separators, centered icons, even padding
  - Do: (human, 2026-11-18): the EDT-84 tool window sidebars need three visual fixes: (1) vertical separator lines between the icon buttons to visually group/divide them; (2) icons inside the sidebar buttons must be centered both horizontally and vertically, not offset; (3) the padding around each icon button must be equal on all four sides and slightly more generous than the current value. Fix all three in the sidebar rendering code; add a render test that checks button dimensions and icon centering.
  - Done when: Sidebars show separator lines; icons are centered; all four sides of each button have equal padding; render/layout tests pass.
  - Deps: EDT-84
- [~] @skate-tooling **EDT-114** `P1` Wire the editor wizard to ProjectCreator (EDT-59 slice 2)
  - Do: (NEEDS from EDT-59 slice 1): wire `ProjectWizardWindow` and `ProjectActionHandler` to `templates/ProjectCreator`, add the new `Project` settings fields and typed folders, remove `createDefaultScenes`.
  - Done when: Each template created from the wizard opens and runs; tests for the settings and the wizard model.
  - Deps: EDT-59 slice 1
- [x] **EDT-82** `P2` Editor-wide "unsaved assets" prompt on close
  - Do: (ANI-21 design answer, 2026-11-10): one prompt when the project or editor closes, listing the unsaved scene, animation clips and animator controllers, with Save all / Discard / Cancel.
  - Done when: Closing with unsaved assets asks first; tests for the dirty-document registry.
  - Deps: ANI-21
- [ ] **EDT-83** `P2` Git: "Merge into current" in the branch menu
  - Do: (EDT-79 design answer, 2026-11-10): merge another branch into the current one from the status-bar branch popup, using EDT-79's Conflicts dialog; needs its own design review (risk of losing work).
  - Done when: A clean merge and a conflicting merge both work from the popup; tests.
  - Deps: EDT-79
- [x] **EDT-84** `P1` IntelliJ-style tool window bars on the left and right
  - Do: (human, EDT-79 design answers, 2026-11-10): vertical bars on the left and right edges of the editor with one button per tool window, as in IntelliJ, to open, close and focus windows (Hierarchy, Inspector, Asset Browser, Version Control, …). Design note first.
  - Done when: Every registered window can be toggled from a side bar, and its position is saved with the layout; tests.
  - Design (`docs/design/EDT-84.md`, approved 2026-11-17): no Game Viewport button; bottom-zone windows split by category (Assets and Scripting left; Debug, Project and Help right); one-way link (moving a button re-docks its window, dragging a tab does not move the button); icons with tooltips; bars hideable from View > Tool Window Bars (on by default, saved in editor settings); Alt+1…9 style shortcuts are part of EDT-84 (one rebindable shortcut per window in Key Bindings).
  - Deps: none

### Tooling & samples — `TLG` (`skate-tooling`)

Round 27+ goals: tooling fixes, an Android feasibility study and new demo projects.

- [x] **TLG-11** `P1` `discord-dm.sh` reads attachments
  - Do: Add a `files <message-id> [dir]` subcommand that lists and downloads a DM's attachments (owner messages only), and show attachment names in `read`. Never print the token.
  - Done when: The lead can fetch a DM attachment with the script; shellcheck clean.
- [ ] **TLG-12** `P3` Android export feasibility spike
  - Do: Write `docs/design/TLG-12.md`: LWJGL has no official Android support. Compare options (OpenGL ES backend vs. the Vulkan backend, launcher/packaging, input, assets), estimate cost and risk, and recommend go/no-go.
  - Done when: The human approves the note.
- [ ] **TLG-13** `P3` Parkour, FPS and car-racing demo projects
  - Do: Three new templates/samples (car racing uses `RaycastVehicleSolver`), each playable in the editor and exportable. Split into one task per demo when it starts.
  - Done when: Each demo is selectable in New Project and plays.
  - Deps: EDT-59

- [x] **TLG-14** `P1` Samples write absolute asset paths into generated projects
  - Do: From GUI-14: the skate sample writes absolute asset paths, so a CLI export of a sample cannot load its scene or assets. Make every sample/CLI path project-relative and check other export paths for absolute references.
  - Done when: A CLI-exported sample runs from another folder; test.
- [ ] **TLG-15** `P3` Feature Showcase: items, chest, damage target, equip screen (GPL-24)
  - Do: Per (`docs/design/GPL-24.md` §8): a showcase area with pickups, a chest, a damage target and the equip screen.
  - Done when: The area plays end to end in the editor and an export.
  - Deps: GPL-28, GUI-18
- [x] **TLG-16** `P2` Logical UI hierarchies in the sample projects
  - Do: (human, Discord 2026-11-10): in the Feature Showcase, the legend panel's items become children of the legend panel object and the other UI items are regrouped logically (HUD, menus, panels); same for every sample (Tetris, Marble Run, MainScene).
  - Done when: Each sample's Hierarchy shows grouped UI; samples still play; sample tests updated.
- [x] **TLG-17** `P2` Showcase Nature area part 1 (REN-29 task 13a)
  - Do: (NEEDS from REN-35): a 10 × 6 × 1.5 m pool with `WaterComponent(material = WaterMaterials.POOL)` and steps, floating crates of three densities, a ball and a log, a UI button named in `EnvironmentComponent.windCycleButton`, and `docs/samples.md`.
  - Done when: The Showcase Nature area part 1 (pool, floating props, wind) works on GL and VK; tests.
  - Status (2026-11-17): code complete on agents/tooling (bb1e5d26); the full suite fails `ProjectLifecycleEndToEndTest.Lifecycle_9` until AST-37 lands.
  - Deps: REN-35, PHY-16, VK-08
- [ ] **TLG-18** `P2` Showcase Terrain area (REN-28 task 11)
  - Do: (NEEDS from REN-36): a 96 m procedural island, a stamp under a ramp, a reroll button (`seedOffset`). Best after REN-36 tasks 5–7 (biomes, scatter).
  - Done when: The Showcase Terrain area plays in the editor and an export; tests.
  - API (REN-36): place `TerrainComponent.showcaseIsland()` with a ramp on its pad (default local (-18, 10), height 3), and a "Reroll seed" button calling `reroll()`.
  - Deps: REN-36, PHY-26, ECS-17, TLG-17
- [ ] **TLG-19** `P3` CLI export uses `builds/` instead of `Builds/`
  - Do: (NEEDS from AST-39): `tools/cli/.../SkateCliService.kt:124` (pack) creates and checks `File(projectDir, "builds")` while `AssetsPacker` writes to `ProjectLayout.BUILDS_DIR` (`Builds/`); on a case-sensitive folder the CLI fails with "Packing did not produce …". Use `AssetsPacker.defaultOutputDir(project)`.
  - Done when: `skate export` works on a case-sensitive folder; test.
  - Deps: none
- [ ] **TLG-20** `P2` Big Island sample and stateful props check (REN-37 tasks 22, 27)
  - Do: (NEEDS from REN-37): a 4 km streamed island sample (flattened plateau, free-fly camera, streaming stats overlay from the `EngineStats.terrain*` counters), budget and hitch measurements; and the stateful props check (task 27).
  - Done when: *Big Island* streams within the budgets; destroyed or taken props stay that way across unload, save and load; tests.
  - Deps: PHY-28, AST-40, AST-41, GUI-19
- [ ] **TLG-21** `P1` Feature Showcase audit and upkeep
  - Do: (human, Discord 2026-11-17): make sure the Feature Showcase covers every user-visible feature done so far (list missing areas, e.g. terrain, buoyancy, blend trees, OSM city, items), add the missing ones in small slices, and keep a checklist in `docs/samples.md` that rule 9 points to.
  - Done when: The showcase covers every done feature in the checklist; samples tests pass.
  - Deps: none
- [x] **TLG-22** `P1` herdr setup: question tool and task observer
  - Do: (human, Discord 2026-11-17): make the herdr agent setup (`.github/agents/*.agent.md`, `tools/herdr/sync-agents.sh`, skills) explicitly say that only the lead asks the human, with the question tool (rule 11), and make sure the `task-observer` skill is loaded for every herdr agent session. Also review the `claude-code-setup:claude-automation-recommender` suggestions for this repo and report which plugins, skills or hooks fit.
  - Done when: Every generated agent file mentions the question rule and loads task-observer; the recommendations are reported to the lead.
  - Deps: none
- [~] @skate-tooling **TLG-23** `P1` Feature Showcase and MainScene: round 38–39 demos and water audio control
  - Do: (human, 2026-11-18): update the Feature Showcase and MainScene so every round 38–39 feature has a labeled demo there instead of requiring the human to build a scene themselves. Specifically:
    1. **Water audio (AUD-15)** — once AUD-15 lands, the pool's `WaterComponent` will have audio fields; set them to their defaults in `nature()` so they are visible in Properties from day one. Do not add a separate `AudioComponent` for this.
    2. **ECS-21 FirstPersonCamera demo** — add a labeled `[Camera] FirstPerson` entity with a `FirstPersonCameraComponent` in the showcase (not the default camera; a secondary one the user can select to test that inspector edits reach the Game Viewport on the next frame).
    3. **REN-43 grey rock scatter / terrain with biomes** — the showcase's terrain area (or a new "Terrain" area if none exists) must have a small streamed terrain with at least one scatter rule using the engine's default rock material so the grey rocks are visible in Play and the editor.
    4. **EDT-90 water and wind** — the Nature pool already has a `WaterComponent`; make sure the `EnvironmentComponent` on the scene has a non-zero wind value and the wind-cycle button cycles visibly.
    5. **GPL-27 skater hat and glasses** — these belong in `MainScene` (skateboarding-specific). Confirm the default skater in `MainScene` equips a hat and glasses from `Assets/Items/` and they are visible at startup; update `PrefabsGenerator` if needed.
    6. **ANI-19 clothing remap** — `MainScene`: add a second NPC skater (or use the existing one) with a skinned clothing mesh that is remapped by bone name so the tester can see it working.
    7. **TLG-17 existing pool** — verify the pool, floating props, ball and log all appear on a fresh New Project (run `ProjectLifecycleEndToEndTest` or equivalent); fix any missing references.
  - Done when: A fresh New Project opens `FeatureShowcase` and `MainScene`, presses Play, and every item above is present and working; `ProjectLifecycleEndToEndTest` passes or failures are pre-existing in `docs/bugs.md`; tests updated.
  - Deps: TLG-17, ECS-21, REN-43, EDT-90, GPL-27, ANI-19, AUD-15

### Quality — `QA` (`skate-qa`)

Round 27+ goals: a warning-free build.

- [x] **QA-17** `P0` Fix build warnings
  - Do: Remove the remaining Kotlin compiler and Gradle warnings (deprecations included) across the codebase, then make new warnings fail the build (`allWarningsAsErrors` or an equivalent CI check) with justified suppressions only.
  - Done when: `gradlew build` prints no warnings and CI fails on a new warning.
- [x] **QA-18** `P3` Flaky `NoiseFieldTest`
  - Do: (reported by skate-tooling, 2026-11-17): `NoiseFieldTest` failed once in a full run and passed on rerun. Find the source of non-determinism and fix it, or list it in `docs/bugs.md`.
  - Done when: 20 consecutive runs pass; or the cause is documented.
  - Deps: none
- [x] **QA-19** `P1` Editor responsiveness audit
  - Do: (human, Discord 2026-11-17): the editor is sometimes sluggish when creating a project or pressing Play. Profile those paths and every other heavy editor operation, list what runs on the UI thread that shouldn't (rule 10), and propose one task per owning domain to move it to coroutines with progress. Add a guard or timing test for the worst offenders.
  - Done when: An audit note `docs/design/QA-19.md` with measured timings exists and each offender has a backlog task.
  - Deps: none
- [x] **QA-20** `P0` Editor coverage audit: every engine feature configurable, every component inspector complete
  - Do: (human, 2026-11-25): list each engine feature and component and check it is configurable in the editor; for every existing component inspector, check each serialized field and setting is shown, editable (right widget, ranges, asset pickers, lists/maps/nested objects), tooltipped and translated. Write the gap list to `docs/design/QA-20.md` by domain (feature or field, where it is hardcoded, owner, proposed task); the lead adds the tasks to the backlog.
  - Done when: The report covers every component and engine system; each gap has an owner and a task ID; a guard test fails when a serialized component field has no inspector entry (with an explicit allowlist).
  - Deps: EDT-107, EDT-108

---

## Agents & herdr

Each prefix above maps to an agent definition:

- **Copilot CLI** (source of truth): `.github/agents/<agent>.agent.md`
- **OpenCode**: `.opencode/agents/<agent>.md` (generated: `tools/herdr/sync-agents.sh`)

[herdr](https://herdr.dev) runs the agents in persistent terminal panes and tracks their state (`working` / `blocked` / `idle`).

```bash
# from WSL, at the repo root (herdr server running)
tools/herdr/herd.sh                          # lead + all domain agents (copilot)
tools/herdr/herd.sh render physics editor    # lead + selected domains
tools/herdr/herd.sh --kind opencode render   # use OpenCode instead
tools/herdr/herd.sh --worktrees render ecs   # one git worktree per domain agent
```

The `skate-lead` agent reads this file, picks ready tasks, and dispatches them:

```bash
herdr agent prompt skate-render "Work on REN-01 from GameEngine.md. Report back when done." --wait
herdr agent read skate-render --source recent-unwrapped --lines 120
```

See `tools/herdr/README.md` for details.
