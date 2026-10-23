# Skate Engine — Domain Backlog

Skate is a 3D game engine and editor written in Kotlin (JVM 17). It uses LWJGL (OpenGL, GLFW, OpenAL, Assimp, stb), JOML, Dear ImGui, Bullet physics (libbulletjme) and a custom ECS. It started as an engine for skateboarding games but must work for **any** 3D game. The codebase is far along, but every area needs improvement and some systems need a full rewrite.

This file is the **single source of truth** for engine work. It is written for both humans and coding agents.
Each domain has a dedicated agent (see [Agents & herdr](#agents--herdr)).

---

## How to use this file

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

1. Only work on tasks in **your domain** (the ID prefix). If you need a change in another domain, report it to `skate-lead` instead of making it.
2. Pick the highest-priority task whose `Deps` are all `[x]`. Before you start, read the code under the domain's paths.
3. Start every rewrite (`REWRITE` in the title) with a short design note in `docs/design/<task-id>.md`. Ask the lead for approval before you write code.
4. Respect the architecture guards in `src/test/kotlin/com/pafoid/skate/architecture/` (engine ↛ editor, physics via factory, UI mutations through `Command` + service layer).
5. Every task ships with unit tests (JUnit 5 + mockk). **No integration tests for now.** Name tests `Area_Behavior`.
6. Keep the build green: `cmd.exe /c gradlew.bat test` from WSL/herdr, or `.\gradlew.bat test` on Windows.
7. Only `skate-lead` edits checkbox state in this file. Domain agents report status back to the lead.

---

## Ownership map

| Prefix | Domain | Agent | Primary paths |
|--------|--------|-------|---------------|
| `REN` | Rendering (renderer, RHI, materials, lighting, shadows, post-fx, particles, fog, textures) | `skate-render` | `engine/render/**`, `src/main/resources/shaders/**` |
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

The renderer works but needs cleaner APIs, caching, dynamic shaders, better materials, better lighting and shadows, post-processing, particles, and a path to Vulkan via an RHI.

- [x] **REN-01** `P0` Audit the renderer and write a target architecture
  - Do: Document the current flow `Renderer` → `RenderGraph` → passes (`GeometryPass`, `ShadowPass`, `PickingPass`, `DebugPass`) and every direct GL call outside `render/`. List the redundant state changes and per-frame allocations.
  - Done when: `docs/design/REN-01.md` describes the current state, pain points and target layering (RHI → render graph → passes → high-level API), with an ordered migration plan.
- [x] **REN-02** `P1` Add a GPU resource cache
  - Do: Cache shaders, meshes (VAO/VBO), textures and uniform locations by key, with ref-counting and explicit disposal. Remove duplicate GPU uploads.
  - Done when: Loading the same asset twice creates one GPU resource. Disposing the scene frees all of them. Unit tests cover cache hit, miss and eviction.
  - Deps: REN-01
- [~] @skate-render **REN-03** `P1` Introduce a render state/sort layer
  - Do: Batch draw calls by shader → material → mesh. Track GL state to skip redundant binds.
  - Done when: `EngineStats` reports draw calls and state changes, and both drop on the sample scene. Sort-key generation has unit tests.
  - Deps: REN-02
- [ ] **REN-04** `P1` REWRITE Material system
  - Do: Data-driven `Material` (shader ref + typed parameter block + texture slots). Support PBR metallic/roughness as the default and serialize materials as assets.
  - Done when: Models render through materials instead of hardcoded uniforms, materials can be edited and saved, and parameter binding has unit tests.
  - Deps: REN-02
- [ ] **REN-05** `P1` Dynamic shaders and shader variants
  - Do: Add shader `#include` and `#define` permutations (skinned, shadowed, fog, etc.) to `ShaderLoader`, compile variants on demand and cache them, and hot-reload shaders in editor builds.
  - Done when: Editing a `.glsl` file under `resources/shaders` updates the running editor, and variant keys plus preprocessor output are unit-tested.
  - Deps: REN-02
- [ ] **REN-06** `P2` REWRITE Lighting
  - Do: Replace `LightingUniformsLoader` with a light buffer (UBO/SSBO) supporting N point/spot/directional lights. Add light culling per object (forward+) or a clustered approach.
  - Done when: The scene supports at least 64 dynamic lights without shader changes, and light culling math is unit-tested.
  - Deps: REN-04
- [ ] **REN-07** `P2` Shadows overhaul
  - Do: Cascaded shadow maps for directional lights, spot/point shadow maps and PCF filtering, with quality driven by `ShadowQuality`.
  - Done when: There is no shadow acne or peter-panning on the sample scene, every `ShadowQuality` level works, and cascade split math is unit-tested.
  - Deps: REN-06
- [ ] **REN-08** `P2` Post-processing stack
  - Do: HDR render target, tonemapping (ACES), gamma, bloom, FXAA and SSAO as render-graph passes that can be toggled per camera/environment.
  - Done when: Effects can be toggled at runtime and from the editor. Pass ordering and enable/disable have unit tests.
  - Deps: REN-03
- [ ] **REN-09** `P2` Fog improvements
  - Do: Move fog from `SkyDomeRenderer`/`LightingUniformsLoader` into a shared shader include. Support linear/exp/exp² and height fog, configured from `EnvironmentComponent`/`EnvironmentPreset`.
  - Done when: Fog looks the same on models, terrain and sky, and fog parameter math is unit-tested.
  - Deps: REN-05
- [ ] **REN-10** `P2` Particle system
  - Do: Add a `ParticleEmitter` component (CPU first, GPU instancing for draw) with an emission shape, lifetime, velocity/size/color over life and a texture atlas.
  - Done when: Emitters can be added in the editor and play in game, and the simulation step is unit-tested.
  - Deps: REN-03, ECS-03
- [ ] **REN-11** `P2` 2D/3D texture rendering improvements
  - Do: Clean up `Renderer2D`, `TextureRenderer3D` and sprite batching. Add mipmaps/anisotropy from `TextureQuality`, sprite atlases and billboards.
  - Done when: Sprites and billboards batch correctly, and texture quality settings take effect.
  - Deps: REN-02
- [ ] **REN-12** `P3` RHI abstraction (Vulkan-ready)
  - Do: Define backend-agnostic interfaces (`RhiDevice`, `RhiBuffer`, `RhiTexture`, `RhiPipeline`, `RhiCommandList`) and move all GL calls into an `OpenGlRhi` backend.
  - Done when: No GL call exists outside `render/rhi/opengl`. An architecture guard test enforces this.
  - Deps: REN-03, REN-04, REN-05
- [ ] **REN-13** `P3` Vulkan backend spike
  - Do: Build a minimal `VulkanRhi` that renders the sample scene's geometry pass.
  - Done when: A config flag switches backend, and the spike's findings are documented.
  - Deps: REN-12

### Asset management — `AST` (`skate-assets`)

Asset management works but needs a complete review.

- [x] **AST-01** `P0` Asset pipeline review
  - Do: Review `AssetsManager`, `Assets`, the loaders (`AssimpLoader`, `TextureLoader`, `SoundLoader`, `ShaderLoader`, `AnimationLoader`), `serialization/` and `AssetsResolver`.
  - Done when: `docs/design/AST-01.md` lists defects, threading issues and a target design (asset handles/GUIDs, async loading via `JobSystem`, ref-counting).
- [~] @skate-assets **AST-02** `P1` Stable asset IDs and handles
  - Do: Give every asset a GUID plus a `.meta` sidecar. Scenes and prefabs reference GUIDs, not paths.
  - Done when: Renaming or moving a file in the editor does not break references, and GUID/meta round-trips have unit tests.
  - Deps: AST-01
- [ ] **AST-03** `P1` Async loading and unloading
  - Do: Decode on `JobSystem` workers and upload to the GPU on the main thread. Add placeholder assets while loading, ref-counted unload and an `AssetEvent` for load complete.
  - Done when: Scene load does not block the main thread on large models. Ref-count and lifecycle have unit tests.
  - Deps: AST-02
- [ ] **AST-04** `P1` Serialization versioning
  - Do: Add a schema version to scene, prefab and project files, with migration functions.
  - Done when: Old files load through migrations, and each migration has a unit test.
  - Deps: AST-01
- [ ] **AST-05** `P2` Hot reload of assets
  - Do: Watch the file system (reuse `FileSystemScanner`) and reload changed textures, models and sounds in place.
  - Done when: Editing a texture on disk updates the editor viewport.
  - Deps: AST-03
- [ ] **AST-06** `P2` Export and asset packing improvements
  - Do: Rework `AssetsPacker` and `ProjectExporter`: pack only referenced assets, compress, write a manifest and checksums, and produce a runnable `gameJar`.
  - Done when: An exported sample project runs standalone, and packing and manifest generation have unit tests.
  - Deps: AST-02

### ECS — `ECS` (`skate-ecs`)

The ECS is in good working shape but needs a complete review. It is probably missing components, and caching is half implemented.

- [x] **ECS-01** `P0` ECS review
  - Do: Review `GameObject`, `Scene`, `SceneManager`, `SystemManager`, `GameObjectManager`, `TransformSystem` and component storage. Find all half-implemented caches (`Component.kt`, `System.kt`, `SceneManager.kt` and the systems that use them).
  - Done when: `docs/design/ECS-01.md` lists the issues and a target design (component storage, queries, system ordering, cache invalidation).
- [~] @skate-ecs **ECS-02** `P1` Finish component query caching
  - Do: Cache component queries per system with invalidation when components are added or removed. Remove the ad-hoc caches in individual systems.
  - Done when: All systems use the shared query API, and invalidation has unit tests.
  - Deps: ECS-01
- [ ] **ECS-03** `P1` Explicit system ordering and phases
  - Do: Define `PreUpdate / FixedUpdate / Update / LateUpdate / Render` phases with declared dependencies.
  - Done when: System order is deterministic, and ordering resolution has unit tests.
  - Deps: ECS-01
- [ ] **ECS-04** `P2` Component gap analysis
  - Do: List the components missing compared with industry engines (e.g. `Tag/Layer`, `LOD`, `Trigger`, `AudioListener`, `Billboard`, `Lifetime`, `Prefab instance link`) and create follow-up tasks for the owning domains.
  - Done when: The gap list is in `docs/design/ECS-04.md` and the follow-ups are reported to the lead.
  - Deps: ECS-01
- [ ] **ECS-05** `P2` Prefab instances and overrides
  - Do: Link instances to prefab assets with per-property overrides that survive prefab edits.
  - Done when: Editing a prefab updates its instances but keeps their overrides, with unit tests.
  - Deps: ECS-02, AST-02

### Physics — `PHY` (`skate-physics`)

Physics needs overall improvements, a swappable physics engine, and a joint component.

- [x] **PHY-01** `P0` Introduce `Physics3DFactory`
  - Do: `PhysicsSystem` currently constructs `BulletPhysics3D` directly. Add a `Physics3DFactory` (backend chosen by config) and route all construction through it. The architecture guard already expects this.
  - Done when: No code outside the factory constructs `BulletPhysics3D`, and the guard test passes.
- [~] @skate-physics **PHY-02** `P1` Make `IPhysics3D` backend-agnostic
  - Do: Remove Bullet types from `IPhysics3D`/`IPhysicsBody3D`/`IPhysicsConstraint` signatures and keep them inside `physics3d/bullet/`.
  - Done when: Only the Bullet backend package imports `com.jme3.bullet`. A guard test enforces this.
  - Deps: PHY-01
- [ ] **PHY-03** `P1` Joint component
  - Do: Add a `JointComponent` that supports fixed, hinge, cone-twist, slider and 6DOF joints, built on the existing `HingeConstraint3D`/`ConeTwistConstraint3D`. Make it serializable and editable.
  - Done when: Joints can be created in the editor and persist in the scene. Joint creation and limits have unit tests.
  - Deps: PHY-02
- [ ] **PHY-04** `P2` Physics correctness and features
  - Do: Fixed timestep with interpolation, collision layers/masks, triggers, raycast/shape-cast queries, physics materials (friction, restitution), CCD for fast bodies, and collision events through `PhysicsEvents`.
  - Done when: Each feature has unit tests, and the skateboard does not tunnel at high speed.
  - Deps: PHY-02
- [ ] **PHY-05** `P3` Second physics backend spike
  - Do: Evaluate Jolt (via JNI bindings) or another engine behind `IPhysics3D`.
  - Done when: The findings and a minimal prototype are documented.
  - Deps: PHY-02

### Animation — `ANI` (`skate-animation`)

The animation system needs improvements.

- [x] **ANI-01** `P0` Animation review
  - Do: Review `AnimationSystem`, `Animator`, `SkeletonComponent`, `BoneOverride`, `AnimationLoader`, `BoneNameMapper` and `SkeletonMath`.
  - Done when: `docs/design/ANI-01.md` lists defects and a target design.
- [~] @skate-animation **ANI-02** `P1` Animation blending and state machine
  - Do: Crossfades, blend trees (1D/2D), an animation state machine asset with parameters and transitions, and animation events.
  - Done when: The player transitions smoothly between push, ride and trick states. Blending and transition logic have unit tests.
  - Deps: ANI-01
- [ ] **ANI-03** `P2` Retargeting and masks
  - Do: Humanoid retargeting via `BoneNameMapper`, plus per-bone layer masks (e.g. upper body).
  - Done when: One animation plays on two different rigs, with unit tests for the mapping.
  - Deps: ANI-02
- [ ] **ANI-04** `P2` IK
  - Do: Two-bone IK for feet on the board and hands, plus look-at.
  - Done when: The feet stick to the board during tricks, and the IK solver has unit tests.
  - Deps: ANI-02
- [ ] **ANI-05** `P2` GPU skinning performance
  - Do: Upload bone palettes through a UBO/SSBO and skip the update for off-screen skinned meshes.
  - Done when: Profiler shows a reduced animation cost with many characters.
  - Deps: ANI-01, REN-02

### Audio — `AUD` (`skate-audio`)

Audio needs a more complete implementation.

- [~] @skate-audio **AUD-01** `P1` Audio review and design
  - Do: Review `AudioEngine`, `AudioSystem`, `AudioComponent` and `SoundLoader`.
  - Done when: `docs/design/AUD-01.md` describes the target design: buses/mixer, voices, streaming, 3D.
- [ ] **AUD-02** `P1` Mixer and buses
  - Do: Master/music/SFX/UI buses with volume and mute, wired to `AudioSettings`.
  - Done when: The settings change the volume live, and the mixer math has unit tests.
  - Deps: AUD-01
- [ ] **AUD-03** `P1` 3D spatial audio
  - Do: Add an `AudioListener` component and attenuation models, plus Doppler and velocity from physics.
  - Done when: Sounds pan and attenuate correctly, and attenuation has unit tests.
  - Deps: AUD-01
- [ ] **AUD-04** `P2` Streaming and voice management
  - Do: Stream long music files (OGG), cap voices with priority-based stealing, and add one-shot/looping/random-container sounds.
  - Done when: Music streams without loading the whole file into memory, and voice stealing has unit tests.
  - Deps: AUD-02
- [ ] **AUD-05** `P3` Effects
  - Do: Reverb zones and low-pass filters via OpenAL EFX.
  - Done when: Effects can be configured per zone or bus.
  - Deps: AUD-02

### Input — `INP` (`skate-input`)

Input works well but needs review and controller dead-zone support.

- [~] @skate-input **INP-01** `P1` Input review
  - Do: Review `engine/input/**`, `InputSystem` and the listeners.
  - Done when: The issues are documented in `docs/design/INP-01.md` and the quick fixes are applied.
- [ ] **INP-02** `P1` Configurable controller dead zones
  - Do: `InputSystem` has a hardcoded scalar dead zone (`InputSystem.kt` ~L135). Add per-stick and per-trigger dead zones (axial and radial, plus an outer dead zone and a response curve), configurable in `InputSettings` and the editor's `KeyBindingsWindow`.
  - Done when: Dead zones persist in the settings, and each mode has unit tests.
- [ ] **INP-03** `P2` Action maps and rebinding
  - Do: Context-based action maps (gameplay/UI/editor), runtime rebinding with conflict detection, and saved bindings per project.
  - Done when: A binding can be changed in the editor and used in game. Conflict detection has unit tests.
  - Deps: INP-01
- [ ] **INP-04** `P3` Rumble and controller hot-plug
  - Do: Handle controller connect/disconnect events and rumble/haptics where GLFW allows.
  - Done when: Unplugging and replugging a controller keeps the player's input working.

### Gameplay — `GPL` (`skate-gameplay`)

The player controller was hardcoded at first and needs a big overhaul. Some skateboarding prefab models are wrong, and many more prefabs are needed.

- [ ] **GPL-01** `P0` REWRITE Player/skate controller
  - Do: Remove the hardcoded values and logic from `PlayerController`, `SkateboardController` and `PlayerMotionSystem`. Build a data-driven character controller with a state machine (idle, push, ride, air, grind, manual, bail), with tuning in a serializable config asset.
  - Done when: All tuning values are editable in the editor, the game-specific logic lives in `game/`, and the state transitions have unit tests.
  - Deps: ECS-03, PHY-02
- [ ] **GPL-02** `P1` Generic character controller in the engine
  - Do: A reusable kinematic/dynamic character controller (slopes, steps, ground snapping) in `engine/` that games can extend.
  - Done when: A non-skate sample (TLG-03) uses it, and the ground detection math has unit tests.
  - Deps: GPL-01
- [ ] **GPL-03** `P1` Fix wrong skate prefab models
  - Do: Audit `Skater`, `Skateboard`, `Floor` and `PrefabsGenerator` against their source assets, and fix scale, pivots, orientation and materials.
  - Done when: All prefabs spawn correctly oriented and scaled. A checklist is in the PR.
- [ ] **GPL-04** `P2` Prefab library expansion
  - Do: Add skatepark prefabs (ramps, quarter/half pipes, rails, ledges, stairs, boxes, bowls) and generic prefabs (lights, props, triggers, spawn points). See `docs/obstacles.md`.
  - Done when: The prefabs appear in the editor's prefab picker with thumbnails.
  - Deps: GPL-03
- [ ] **GPL-05** `P2` Camera system
  - Do: Make `GameCamera` data-driven (follow, orbit, collision avoidance, spring arm, shake).
  - Done when: Camera parameters can be tuned in the editor, and spring-arm collision has unit tests.
  - Deps: GPL-01

### In-game UI — `GUI` (`skate-gameui`)

In-game UI components need to be added (separate from the ImGui editor).

- [~] @skate-gameui **GUI-01** `P1` Runtime UI design
  - Do: Design a retained-mode UI for games: canvas, anchors and layout, and widgets (text, image, button, slider, progress bar, panel, list), with input focus and gamepad navigation, rendered through `Renderer2D` and `StringManager`.
  - Done when: `docs/design/GUI-01.md` is approved by the lead.
- [ ] **GUI-02** `P1` Core widgets and layout
  - Do: Implement GUI-01 in `engine/ui/` as ECS components and a UI system.
  - Done when: The sample game shows a HUD (score, trick name, timer) and a pause menu. Layout has unit tests.
  - Deps: GUI-01, REN-11
- [ ] **GUI-03** `P2` UI authoring in the editor
  - Do: Edit UI canvases in the editor viewport; coordinate the editor side with `skate-editor`.
  - Done when: UI can be built and saved without code.
  - Deps: GUI-02
- [ ] **GUI-04** `P2` Fonts and localization
  - Do: SDF font rendering, plus localized strings and plurals via `StringManager`.
  - Done when: Text stays crisp at all sizes, and switching locale updates the UI.
  - Deps: GUI-02

### Scripting — `SCR` (`skate-scripting`)

Scripting needs to be implemented. Kotlin Script is the primary language; TypeScript and maybe Lua come later.

- [~] @skate-scripting **SCR-01** `P1` Scripting architecture
  - Do: Design a language-agnostic script API (lifecycle `onCreate/onUpdate/onFixedUpdate/onDestroy`, component access, events, input, physics queries) behind a `ScriptHost` interface. The existing `NativeScriptComponent`/`ScriptingSystem` is the starting point.
  - Done when: `docs/design/SCR-01.md` is approved by the lead.
- [ ] **SCR-02** `P1` Kotlin Script (`.kts`) host
  - Do: Embed the Kotlin scripting host, compile scripts with caching, and expose the API from SCR-01.
  - Done when: A `.kts` script attached to an entity runs in the editor's play mode and the exported game. API bindings have unit tests.
  - Deps: SCR-01
- [ ] **SCR-03** `P2` Script hot reload and error reporting
  - Do: Recompile changed scripts, keep state where possible, and show errors in `ConsoleWindow` with file and line.
  - Done when: Editing a script during play mode applies without restarting.
  - Deps: SCR-02
- [ ] **SCR-04** `P3` TypeScript host
  - Do: Embed a JS engine (e.g. GraalJS or Javet), transpile TS, and use the same `ScriptHost` API.
  - Deps: SCR-02
- [ ] **SCR-05** `P3` Lua host
  - Do: Embed LuaJ (or similar) using the same `ScriptHost` API.
  - Deps: SCR-02

### Networking & platform — `NET` (`skate-online`)

Multiplayer and Steam support need to be added.

- [ ] **NET-01** `P2` Multiplayer architecture
  - Do: Choose a model (client-server with prediction and reconciliation is recommended), transport (UDP, e.g. via Netty or KryoNet), replication of ECS components, and RPCs.
  - Done when: `docs/design/NET-01.md` is approved by the lead.
  - Deps: ECS-03
- [ ] **NET-02** `P2` Transport and replication MVP
  - Do: Replicate transforms and player state for 2+ clients with interpolation.
  - Done when: Two local clients see each other skate. Serialization and delta compression have unit tests.
  - Deps: NET-01
- [ ] **NET-03** `P3` Client-side prediction and lag compensation
  - Deps: NET-02
- [ ] **NET-04** `P2` Steam integration
  - Do: Integrate Steamworks via steamworks4j behind a `PlatformServices` interface (achievements, stats, leaderboards, rich presence, lobbies), with a no-op fallback when Steam is absent.
  - Done when: The game runs with and without Steam. The fallback and the interface have unit tests.
- [ ] **NET-05** `P3` Steam networking transport
  - Do: Implement the NET-02 transport over Steam networking sockets and lobbies.
  - Deps: NET-02, NET-04

---

## Editor — `EDT` (`skate-editor`)

All editor mutations must go through `Command` classes and the service layer (see the architecture guards).

- [x] **EDT-01** `P0` Persist window layout across sessions
  - Do: Load and save `imgui.ini` properly (see the TODO in `app/EditorScreen.kt`), restore docked windows and viewports, and add a "Reset layout" menu item.
  - Done when: Restarting the editor restores the exact layout. Load/save paths have unit tests.
- [x] **EDT-11** `P0` Fix crash when reorganizing ImGui windows
  - Do: Dear ImGui assertion `id != 0` in `ImGui.beginPopupContextItem()` at `SceneHierarchyWindow.doTreeNode` (SceneHierarchyWindow.kt:323, called from `WindowManager.update`) when docking/reordering windows. Give the popup an explicit non-empty string ID (e.g. `beginPopupContextItem("ctx##${obj.uId}")`), audit all other `beginPopupContext*`/`beginPopup` calls in `editor/` for the same bug, and make sure item IDs are unique and stable.
  - Done when: Dragging, docking and reordering windows no longer asserts; the ID-building logic has a unit test where feasible.
- [x] **EDT-12** `P0` Sensible default window layout
  - Do: Build a default dock layout used on first run and by "Reset Layout" (e.g. Scene Hierarchy left, Viewport centre, Properties right, Asset Browser/Console bottom, toolbar on top) via DockBuilder in `ImGuiLayer`, applied when no saved `imgui.ini` exists. Keep EDT-01 persistence working.
  - Done when: A fresh editor start (no imgui.ini) and Reset Layout both show that layout; the layout definition has unit tests where feasible.
- [~] @skate-editor **EDT-02** `P1` ImGui and viewport configuration
  - Do: Clean up `ImGuiLayer`/`WindowManager`/`WindowRegistry`. Fix the viewport's resizing, DPI scaling and multi-viewport handling, and the game view vs. scene view split.
  - Done when: The viewport renders at the correct resolution and DPI on resize and when moved between monitors.
  - Deps: EDT-01
- [ ] **EDT-03** `P1` Modernize gizmos
  - Do: Rework the translate/rotate/scale gizmos (`editor/gizmos`): screen-space constant size, plane handles, local/world toggle, snapping, multi-selection pivot, hover highlight and correct picking.
  - Done when: All gizmos work under any camera angle, and gizmo math has unit tests.
- [ ] **EDT-04** `P1` Visual representation of cameras and lights
  - Do: Billboard icons plus wire gizmos (camera frustum, light range/cone, direction arrow), all selectable.
  - Done when: Every camera and light is visible and clickable in the scene view.
  - Deps: EDT-03
- [ ] **EDT-05** `P2` Tooltips everywhere
  - Do: Add tooltips (via `StringManager` keys) to every toolbar button, property and menu item.
  - Done when: No interactive widget lacks a tooltip. A test checks that all tooltip keys exist in the string files.
- [ ] **EDT-06** `P2` Complete translations
  - Do: Find hardcoded UI strings and missing keys between `assets/strings/editor_strings.properties` and the locale files.
  - Done when: A unit test fails on missing keys, and `fr` is complete.
- [ ] **EDT-07** `P2` Theming support
  - Do: Make `ImGuiStyleManager` load themes (colors, sizes, fonts) from files, ship light/dark/high-contrast themes, and add a theme picker in `EditorSettingsWindow`.
  - Done when: Switching theme applies live and persists. Theme parsing has unit tests.
- [ ] **EDT-08** `P2` Shell command panel
  - Do: Add a dockable terminal-style panel that runs shell commands (Gradle tasks, the TLG-01 CLI) asynchronously, streams output, and keeps history.
  - Done when: Commands run without blocking the UI and can be cancelled.
- [ ] **EDT-09** `P2` Export UX
  - Do: Add an editor-side export wizard on top of AST-06 (target, included scenes, compression, output folder) with progress reporting.
  - Done when: A sample project can be exported from the UI.
  - Deps: AST-06
- [ ] **EDT-10** `P3` General editor UX pass
  - Do: Keyboard shortcuts audit, consistent icons, empty states, error dialogs and undo coverage for every mutation.
  - Done when: The checklist is in `docs/design/EDT-10.md` and its items are resolved.

---

## Testing & tooling

### Tooling & samples — `TLG` (`skate-tooling`)

- [~] @skate-tooling **TLG-01** `P1` Skate CLI
  - Do: Add a headless CLI (`tools/cli`, Gradle `application`) for: create project, validate project/assets, pack/export, run scene headless and print engine stats.
  - Done when: `skate --help` lists the commands, which reuse engine services (no duplicate logic). Argument parsing has unit tests.
- [ ] **TLG-02** `P2` MCP server
  - Do: Expose the CLI capabilities plus editor introspection (scene tree, components, logs) over MCP so agents can drive the engine.
  - Done when: An MCP client can list scenes and entities and trigger an export.
  - Deps: TLG-01
- [ ] **TLG-03** `P2` Sample project: Tetris
  - Do: A 2D/3D Tetris that exercises input, in-game UI, audio, scenes and scripting.
  - Deps: GUI-02, SCR-02
- [ ] **TLG-04** `P2` Sample project: Marble Run
  - Do: Physics-heavy sample (rolling bodies, joints, triggers, camera follow).
  - Deps: PHY-03, PHY-04
- [ ] **TLG-05** `P1` Sample project: Feature showcase
  - Do: One scene per engine feature (lights, shadows, materials, post-fx, particles, animation, audio, physics, UI) that serves as a manual regression suite.
  - Done when: Each scene is documented in `docs/samples.md` with what to look for.

### Quality — `QA` (`skate-qa`)

A lot of features are broken. Many unit tests are missing (**no integration tests for now**), and many industry standards were not followed.

- [x] **QA-01** `P0` Broken-feature triage
  - Do: Run the editor and sample game, then list every broken feature with repro steps, the suspected file and the owning domain prefix.
  - Done when: `docs/bugs.md` exists and each bug is reported to the lead for assignment.
- [x] **QA-07** `P0` Fix the test source set compilation
  - Do: `gradlew.bat test` does not compile (about 25 files, e.g. `PhysicsComponentTest`, `PointLightComponentsTest`, `PlayerControllerTest`, `StringManagerTest`, `OpenSceneFileCommandTest`). Fix or update them to the current APIs so the whole suite compiles and runs; unblocks PHY-01 and EDT-01 verification.
  - Done when: `gradlew.bat test` compiles and runs; remaining failures are listed in `docs/bugs.md`.
- [x] **QA-02** `P0` Unit-test coverage baseline
  - Do: Add JaCoCo to Gradle and report coverage per package.
  - Done when: The coverage report is generated by `gradlew.bat test jacocoTestReport`, and the lowest-covered packages are listed in `docs/bugs.md`.
- [~] @skate-qa **QA-03** `P1` Unit tests for pure logic
  - Do: Prioritize math (`MathExtensions`, `SkeletonMath`, `Ray`, `Interpolator`), serialization, commands/undo, `EventSystem`, `JobSystem` and `StringManager`. Use mockk for GL/AL boundaries; no integration tests.
  - Done when: Every listed area has tests in `src/test/...` following `Area_Behavior` naming.
  - Deps: QA-02
- [ ] **QA-08** `P1` Fix failing and remove obsolete unit tests
  - Do: `gradlew.bat test` currently has 20 failures (e.g. `UiMutationPipelineGuardTest`, `DeleteFileCommandTest`, `SceneTargetingCommandsTest`, `ViewportRendererScreenshotTest`, `EngineFixedTimestepTest`, `SkateboardModelInspectionTest`, `AudioSystemTest`, `EnvironmentSystemTest`, `InputSystemTest`, `RagdollSystemTest`, `StringManagerTest`). Fix each against the current API, or delete tests that cover removed or obsolete behavior (and say why in the commit).
  - Done when: `gradlew.bat test` is fully green and `docs/bugs.md` has no test-failure entries left.
  - Deps: QA-07
- [ ] **QA-04** `P1` Industry-standards audit
  - Do: Check resource lifetimes (`AutoCloseable`/dispose everywhere), logging via `LoggerService` (no `println`), error handling, thread-safety of shared state, frame-allocation hotspots, units/coordinate-system conventions, and config/versioning.
  - Done when: The findings are in `docs/design/QA-04.md`, with per-domain follow-ups reported to the lead.
- [ ] **QA-05** `P2` More architecture guards
  - Do: Add guard tests for the rules that other tasks introduce (no GL outside RHI, no Bullet outside its backend, no `println`, etc.).
  - Deps: QA-04
- [ ] **QA-06** `P2` CI
  - Do: Add a GitHub Actions workflow on `windows-latest` that runs `gradlew.bat build test`.
  - Done when: CI is green on `main`.

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
