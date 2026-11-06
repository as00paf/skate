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

---

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
- [~] @skate-render **REN-26** `P1` Ambient light becomes an environment setting; default directional Sun
  - Do: Approved by the human: ambient light has no position. Move ambient color/intensity (or sky-based ambient) into the scene's Environment settings, remove the positional ambient component and its gizmo, and add a default "Sun" directional light, linked to day/night, to the default scenes. The editor draws the sun's direction arrow at the entity. Update the default projects and the showcase.
  - Done when: New scenes have Environment ambient + a Sun. No ambient gizmo at 0,0,0. Day/night drives the sun. Tests cover the environment settings and serialization.
  - Deps: REN-25
- [ ] **REN-27** `P1` Environment reflections and a shiny metallic showcase object
  - Do: Add image-based lighting (prefiltered environment map + irradiance from the skybox, BRDF LUT) and/or reflection probes so metallic/smooth materials reflect their surroundings. Add a very shiny, reflective metallic object to the Feature Showcase Materials section.
  - Done when: The showcase metallic object clearly reflects the sky/scene; the IBL math/setup has unit tests; Vulkan parity follows in a VK task.
  - Deps: REN-26
- [ ] **REN-28** `P2` Terrain and biomes: design note
  - Do: Write `docs/design/REN-28.md`, reviewed by physics and assets: chunked heightmap terrain with LOD, splat materials, a noise library (e.g. FastNoiseLite, MIT), biome maps driving materials and scatter, procedural or hand-painted (editor sculpt/paint tools), seeded through ECS-14, heightfield collider (PHY-15). Split implementation tasks out after approval.
  - Done when: The human approves the note.
  - Deps: ECS-14
- [ ] **REN-29** `P2` Vegetation and water: design note
  - Do: Write `docs/design/REN-29.md`: instanced trees/grass with LOD and wind, a water surface shader (reflection/refraction, foam), water volumes for buoyancy (PHY-16), and the Feature Showcase demo area.
  - Done when: The human approves the note.
  - Deps: REN-27

### Vulkan — `VK` (`skate-vulkan`)

Goal (human, after REN-13): full Vulkan support for **exported games**, chosen per project at creation (OpenGL stays the default; the editor always uses OpenGL). Builds on the REN-12 RHI and the REN-13 spike (`docs/design/REN-13.md`). `skate-render` helps with explicitly assigned VK tasks.

**Scheduled last** (human, 2026-11-06): the validation proof (VK-07, then closing VK-01..06) runs after all other planned work, once the LunarG Vulkan SDK is installed.

- [~] @skate-vulkan **VK-01** `P1` Windowed Vulkan runtime: surface, swapchain, frames in flight — code merged (round 23); zero-validation-error run pending the human installing the LunarG Vulkan SDK
  - Do: Turn the offscreen spike into a presenting backend for the game runtime: GLFW no-API window, surface, swapchain (create/recreate on resize and minimize, present modes for vsync), frames in flight (fences/semaphores, per-frame command buffers and transient resources, deferred destruction), backbuffer as the RHI default target, validation layers in debug/test runs.
  - Done when: A test harness window presents frames with Vulkan, survives resize/minimize, zero validation errors; frame-in-flight bookkeeping unit-tested.
  - Deps: REN-13
- [~] @skate-render **VK-02** `P1` Vulkan resources: memory, uploads, descriptors, samplers — code merged (round 23); zero-validation-error run pending the human installing the LunarG Vulkan SDK
  - Do: Memory allocator (e.g. lwjgl-vma), staging uploads for buffers/textures (mips, arrays, cube maps, depth/compare formats), sampler objects, descriptor set layouts/pools derived from SPIR-V reflection, and the `ShaderParams` named-uniform mapping to per-draw UBO data (ring buffer per frame in flight).
  - Done when: Every `RhiDevice.create*` used by the passes works on Vulkan; reflection/layout/ring logic unit-tested; zero validation errors.
  - Deps: REN-13
- [~] @skate-vulkan **VK-03** `P1` Geometry pass parity on Vulkan — code merged (round 24); validation run pending the SDK
  - Do: Port the geometry pass with materials (PBR inputs, alpha modes), UBO lights and culling, skinning, sky dome/skybox and fog to Vulkan.
  - Done when: MainScene's lit, textured, skinned frame matches OpenGL within a documented tolerance (GpuTest); zero validation errors.
  - Deps: VK-01, VK-02
- [~] @skate-render **VK-04** `P2` Shadows on Vulkan — code merged (round 25); validation run pending the SDK
  - Do: Cascaded sun shadows and spot/point shadow maps (depth arrays, compare samplers, depth bias/clamp) with the backend depth-range fix-up; REN-22 caching behaviour preserved.
  - Done when: Showcase shadow area matches OpenGL within tolerance at each quality level (GpuTest).
  - Deps: VK-03
- [~] @skate-vulkan **VK-05** `P2` Post-processing on Vulkan — code merged (round 25); validation run pending the SDK
  - Do: PostChain ping-pong, blits, bloom/FXAA/tonemap/gamma with a documented texture-orientation (Y-flip) convention.
  - Done when: Showcase post-effects area matches OpenGL within tolerance (GpuTest).
  - Deps: VK-03
- [~] @skate-gameui **VK-06** `P2` Runtime UI, particles and debug lines on Vulkan — code merged (round 25); validation run pending the SDK
  - Do: UiPass (batches, SDF text, scissor, blending), instanced particles and debug lines on Vulkan.
  - Done when: Showcase UI/particles areas match OpenGL within tolerance (GpuTest).
  - Deps: VK-03
- [!] **VK-07** `P1` Zero-validation-error proof for the Vulkan backend — blocked: the human must install the official LunarG Vulkan SDK (do not load downloaded nightly layers)
  - Do: With the SDK's Khronos validation layer enabled, run every Vulkan GpuTest (VK-01..06 harnesses, QA-16 parity suite, exported Vulkan game) and fix every validation message; then close VK-01..06.
  - Done when: All Vulkan GpuTests pass with validation on and zero messages; VK-01..06 marked done.
  - Deps: QA-16

### Asset management — `AST` (`skate-assets`)

Round 27+ goals: the Asset Browser shows every asset type, one assets folder, export settings, an asset store, OSM city import, Blender support, and protobuf where it fits.

- [x] **AST-13** `P0` Asset Browser misses animations, textures and sounds; prefab thumbnails lack materials
  - Do: Reproduce on master first. The Asset Browser (`editor/ui/windows/assetBrowser/**`, lead-assigned to assets) lists animations, textures and sounds with suitable thumbnails/icons. Obstacle and generic prefab thumbnails render with their default materials instead of untextured.
  - Done when: All asset types appear in a new project; prefab thumbnails show materials; tests cover the type discovery and the thumbnail material resolution.
- [x] **AST-14** `P1` One assets folder in the default project
  - Do: Also (from AST-13, lead-assigned editor change): add `jpg` to `FileType.TEXTURE` in `editor/data/FileType.kt` and drop the Asset Browser workaround. The default project hierarchy has two assets folders. Keep one, update project creation, export, path resolution and the default projects/CLI samples.
  - Done when: A new project has a single assets folder, everything still loads and exports; tests cover the layout.
  - Deps: AST-13
- [ ] **AST-15** `P2` Export UX and export settings: design note
  - Do: Write `docs/design/AST-15.md`: refined export wizard flow plus settings (splash image, icon, target platform, graphics backend, compression/packing, debug vs release, target FPS).
  - Done when: The human approves the note.
  - Deps: EDT-59
- [~] @skate-online **AST-16** `P2` Asset store window: design note
  - Do: Write `docs/design/AST-16.md`: a provider plugin interface (search, preview, license, download, import), license display and attribution tracking. Providers: NVIDIA SimReady via Hugging Face (https://huggingface.co/spaces/nvidia/simready-central, USD assets, so it needs USD→glTF conversion), Poly Haven, ambientCG, Kenney, Quaternius, Sketchfab (CC licenses, login). No Mixamo (no public API; human decision 2026-11-06: no scraping).
  - Done when: The human approves the note.
- [ ] **AST-17** `P2` City generation from OpenStreetMap: design note
  - Do: Write `docs/design/AST-17.md` evaluating OSM2World (Java, open source, glTF export) as the open-source alternative to commercial OSM city tools: area picker, import to a scene with colliders and materials, licensing (ODbL attribution).
  - Done when: The human approves the note.
- [ ] **AST-18** `P3` Protocol Buffers: design note
  - Do: Write `docs/design/AST-18.md`. Proposed: protobuf only for runtime data (packed exports, network messages). Scenes, prefabs and settings stay JSON so git diffs/merges (EDT-60) stay readable. Measure load time/size gains first.
  - Done when: The human approves the note.
- [~] @skate-assets **AST-19** `P2` Full Blender support: design note
  - Do: Write `docs/design/AST-19.md`: `.blend` import via headless Blender → glTF, re-import when the `.blend` is saved, materials/actions mapping, and an optional "Send to Skate" Blender add-on.
  - Done when: The human approves the note.

- [ ] **AST-20** `P2` Classify FBX files as model or animation by content
  - Do: From AST-16: `.fbx` is always classed as `FileType.ANIMATION`, so FBX models show up in the Animations tab and store packs with FBX models cannot be imported as models. Inspect the file (meshes vs. animations only) and classify it accordingly; a file with both counts as a model with clips.
  - Done when: FBX models appear as models, animation-only FBX as animations; tests with fixture files.

### ECS — `ECS` (`skate-ecs`)

Round 27+ goals: deterministic seeds for games and procedural generation.

- [x] **ECS-14** `P1` Seeded deterministic RNG service
  - Do: Engine RNG service with a project/game seed (project setting, runtime override), independent named streams per system so adding one system does not shift others, exposed to scripts. Show the seed in the Feature Showcase.
  - Done when: The same seed reproduces identical sequences per stream across runs; tests cover determinism and stream independence.

- [ ] **ECS-15** `P3` Remove unused GridLines fields
  - Do: From REN-25: `lodCloseDistance`, `lodFarDistance`, `edgeFadeEnabled` and `edgeFadeStart` on `GridLines` are unused now that the shader handles LOD and fade. Remove them and update the default scenes.
  - Done when: The fields are gone and scenes load; tests updated.

### Physics — `PHY` (`skate-physics`)

Round 27+ goals: a Jolt backend selectable per project, a terrain heightfield collider, and water.

- [ ] **PHY-15** `P2` Heightfield terrain collider
  - Do: Heightfield collider shape across backends, following the approved REN-28 terrain design.
  - Done when: Bodies and the character collide correctly with terrain; tests.
  - Deps: REN-28
- [ ] **PHY-16** `P2` Water volumes and buoyancy
  - Do: Buoyancy and drag in water volumes, following the approved REN-29 design.
  - Done when: Showcase objects float and settle realistically; tests.
  - Deps: REN-29
- [~] @skate-physics **PHY-17** `P2` REWRITE Jolt physics backend — design approved by the lead (2026-11-06, `docs/design/PHY-17.md`)
  - Do: Implement the approved slices in order: a) preparation (`BodyDescription`, per-spec native loader, `ShapeWireframe` move, test helper), b) Jolt core (bodies, shapes, layers, queries, factory + fallback), c) contacts and triggers, d) joints/ragdoll/vehicle parity, e) debug draw, stats, leak/determinism tests, benchmark, f) per-project selection plumbing with EDT/AST/TLG. Decisions: physical centre of mass, ship both natives now (AST-15 strips the unused one), Bullet stays the default.
  - Done when: The physics test suite passes on Jolt as well as Bullet; the showcase runs on Jolt.

- [ ] **PHY-18** `P2` Extract physics natives to a per-user folder
  - Do: From PHY-17: Bullet and Jolt natives extract to `USER_DIR`, which fails for games installed in read-only folders (Program Files). Extract to a per-user data folder instead.
  - Done when: An exported game in a read-only folder starts with physics; loader tests cover the path.
- [ ] **PHY-19** `P3` Jolt-native ragdolls, vehicles and character controller
  - Do: From PHY-17: optional Jolt `Ragdoll` (pose motors), `VehicleConstraint` and `CharacterVirtual` modes (the last one with GPL). Design note first.
  - Done when: The human approves the note.
  - Deps: PHY-17

### Animation — `ANI` (`skate-animation`)

Round 27+ goals: an animation authoring tool and animation from video.

- [~] @skate-animation **ANI-12** `P2` Animation tool and video-to-animation: design note
  - Do: Write `docs/design/ANI-12.md`: an animation editor window (timeline, keyframes, curve editor, preview, events) and a video→animation pipeline from **local video files only** (human decision 2026-11-06). Run an existing pose-estimation tool as an external process (evaluate MediaPipe, GVHMR/WHAM, FreeMoCap, and NVIDIA's offering the human mentioned, "motion bricks") on license, quality, hardware needs and install story, then output BVH/glTF and use the existing retargeting.
  - Done when: The human approves the note.

### Audio — `AUD` (`skate-audio`)

Audio needs a more complete implementation.


### Input — `INP` (`skate-input`)

Round 27+ goals: input is too tightly coupled with skateboarding; it needs a game-agnostic abstraction.

- [~] @skate-gameplay **INP-10** `P1` REWRITE Decouple input from skateboarding
  - Do: Design note `docs/design/INP-10.md` first. Generic action maps and input contexts (gameplay/UI/vehicle/...) with push/pop priority in the engine; skate-specific actions, bindings and gesture/flick logic move to the game layer (`game/**`, owned by gameplay). Scripts and in-game UI use the generic API.
  - Done when: The design is approved and implemented, `engine/input/**` has no skateboarding concepts (architecture-guard test), and the skate game plays as before.

### Gameplay — `GPL` (`skate-gameplay`)

Round 27+ goals: a fuller skate demo with camera modes and standard control schemes, then plans for new game systems.

- [x] **GPL-20** `P0` Prefab library barrel rolls and clips halfway into the ground
  - Do: Reproduce on master first. Fix the barrel's collider/pivot/mesh offset and its rest state in the prefab library showcase.
  - Done when: The barrel rests on the ground without rolling or clipping; a test checks that the collider bounds match the mesh bounds for library prefabs.
- [ ] **GPL-21** `P1` Skateboarding control schemes
  - Do: Ship four default schemes on the INP-10 API: **EA Skate (default)** (flick-it right-stick tricks), Session (each stick a foot), Skater XL (sticks = feet, triggers turn), THPS (face buttons + direction combos). Selectable in the pause menu settings and persisted.
  - Done when: Each scheme can perform the core trick set; scheme definitions have tests.
  - Deps: INP-10
- [x] **GPL-22** `P1` MainScene Camera button cycles FPV, third person and fixed cameras
  - Do: Replace the Restart button in the MainScene HUD with a Camera button that cycles first-person, third-person and fixed cameras (also bound to an input action).
  - Done when: The button and the action cycle the three cameras in editor Play and the exported game; tests cover the cycle.
  - Deps: GPL-20
- [ ] **GPL-23** `P2` Fuller skate demo: design note
  - Do: Write `docs/design/GPL-23.md`: skatepark layout, trick set, combo/score system, session goals and HUD for the MainScene demo.
  - Done when: The human approves the note.
  - Deps: GPL-21
- [ ] **GPL-24** `P3` Game systems and character customization: design note
  - Do: Write `docs/design/GPL-24.md`: reusable inventory, combat, interaction/pickup systems, and character customization (clothes, tattoos, headwear, eyewear via attachment points, mesh swaps and material layers).
  - Done when: The human approves the note.

### In-game UI — `GUI` (`skate-gameui`)

Round 27+ goals: menu parity between the editor and the game, and a map view.

- [x] **GUI-12** `P0` Esc in the exported game shows the same menu as editor Play
  - Do: Reproduce on master first. Find the remaining differences between the exported game's Esc menu and the editor Play pause menu (GUI-09) and make them identical (items, layout, behaviour).
  - Done when: Both show the same menu; a test compares the menu definitions used in both paths.
- [ ] **GUI-13** `P2` Map view in the Feature Showcase
  - Do: Minimap widget and a full-screen map (top-down render or generated texture, player marker, POI markers), demoed in the showcase.
  - Done when: The map shows the player's live position; tests cover the world↔map transform.

### Scripting — `SCR` (`skate-scripting`)

Scripting needs to be implemented. Kotlin Script is the primary language; TypeScript and maybe Lua come later.


### Networking & platform — `NET` (`skate-online`)

Multiplayer and Steam support need to be added.


---

## Editor — `EDT` (`skate-editor`)

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
- [~] @skate-editor **EDT-51** `P1` Editor consistency audit: design note
  - Do: Write `docs/design/EDT-51.md`: an inventory of every window, dialog and menu (hamburger and settings menus included) and one standard for header icons, right-click menus, a `?` help button, empty states, and window vs. modal. List the windows that become dialogs and the dialogs that become windows, plus the missing settings (feeds EDT-57).
  - Done when: The human approves the note.
  - Deps: EDT-45
- [ ] **EDT-52** `P1` Apply the editor consistency standard
  - Do: Apply EDT-51 to every window, dialog and menu (may be split across agents).
  - Done when: Every item in the EDT-51 inventory meets the standard; a test checks windows register header icon, help and context menu.
  - Deps: EDT-51
- [ ] **EDT-53** `P1` Beginner help in every window
  - Do: The `?` in each window opens a plain-language explanation (what it is, how to use it, related windows), written for someone who never used a 3D program. Strings are localized.
  - Done when: Every window has help; a test checks that every registered window has help strings.
  - Deps: EDT-51
- [~] @skate-vulkan **EDT-54** `P1` REWRITE Render Graph window overhaul
  - Do: Design note first. Readable node layout, real pass names, inputs/outputs, per-pass GPU timings, enable/disable a pass, docked and shown by default.
  - Done when: The human approves the design and the window is usable on MainScene and the showcase; layout logic tested.
  - Deps: REN-24
- [x] **EDT-55** `P1` Script editor shown by default and refined
  - Do: Dock the script editor by default; add tabs, unsaved-state markers, an error gutter (from script compile errors), find/replace and go-to-line.
  - Done when: The script editor is visible in the default layout and the features work; tests for the editor state logic.
- [ ] **EDT-56** `P1` Loading screens for long operations
  - Do: A modal progress/loading screen for creating and opening projects, loading scenes and exporting, without blocking the UI thread.
  - Done when: Each long operation shows progress and the UI stays responsive; tests for the progress model.
  - Deps: EDT-51
- [ ] **EDT-57** `P1` Missing project and editor settings; tooltip toggle
  - Do: Add the settings listed in EDT-51 to the project and editor settings windows, including "Show tooltips" (persisted, honored by every tooltip).
  - Done when: All listed settings exist and persist; the tooltip toggle works everywhere; tests.
  - Deps: EDT-51
- [x] **EDT-58** `P1` Remember the editor camera per scene
  - Do: Save the editor camera position/rotation/mode per scene (editor-only data, not in the exported game) and restore it when the scene opens.
  - Done when: Switching scenes and restarting restores each scene's camera; tests.
- [ ] **EDT-59** `P1` REWRITE New Project wizard and templates
  - Do: Design note first. Templates (Empty, Skate game, Feature Showcase, Tetris, Marble Run, later demos), which separates the skate game from the showcase. Settings: target FPS/uncapped, physics backend (Bullet, Simple, Jolt once PHY-17 lands), graphics backend (exists), include AI agent/skill files, splash screen image. Templates come from skate-tooling.
  - Done when: The human approves the design; each template creates a project that opens and runs; tests for the template registry and settings.
  - Deps: EDT-56, AST-14
- [~] @skate-editor **EDT-60** `P2` Git version control: design note
  - Do: Write `docs/design/EDT-60.md`: a JGit-based panel (init, status, diff, commit, branch, pull/push), a project `.gitignore`, and authentication dialogs (GitHub device flow, OS credential manager, SSH keys). The editor never stores raw tokens in project files.
  - Done when: The human approves the note.

---

## Testing & tooling

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

### Quality — `QA` (`skate-qa`)

Round 27+ goals: a warning-free build.

- [x] **QA-17** `P0` Fix build warnings
  - Do: Remove the remaining Kotlin compiler and Gradle warnings (deprecations included) across the codebase, then make new warnings fail the build (`allWarningsAsErrors` or an equivalent CI check) with justified suppressions only.
  - Done when: `gradlew build` prints no warnings and CI fails on a new warning.

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
