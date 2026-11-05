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
- [x] **REN-03** `P1` Introduce a render state/sort layer
  - Do: Batch draw calls by shader → material → mesh. Track GL state to skip redundant binds.
  - Done when: `EngineStats` reports draw calls and state changes, and both drop on the sample scene. Sort-key generation has unit tests.
  - Deps: REN-02
- [x] **REN-04** `P1` REWRITE Material system
  - Do: Data-driven `Material` (shader ref + typed parameter block + texture slots). Support PBR metallic/roughness as the default and serialize materials as assets.
  - Done when: Models render through materials instead of hardcoded uniforms, materials can be edited and saved, and parameter binding has unit tests.
  - Deps: REN-02
- [x] **REN-05** `P1` Dynamic shaders and shader variants
  - Do: Add shader `#include` and `#define` permutations (skinned, shadowed, fog, etc.) to `ShaderLoader`, compile variants on demand and cache them, and hot-reload shaders in editor builds.
  - Done when: Editing a `.glsl` file under `resources/shaders` updates the running editor, and variant keys plus preprocessor output are unit-tested.
  - Deps: REN-02
- [x] **REN-06** `P2` REWRITE Lighting
  - Do: Replace `LightingUniformsLoader` with a light buffer (UBO/SSBO) supporting N point/spot/directional lights. Add light culling per object (forward+) or a clustered approach.
  - Done when: The scene supports at least 64 dynamic lights without shader changes, and light culling math is unit-tested.
  - Deps: REN-04
- [x] **REN-07** `P2` Shadows overhaul
  - Do: Cascaded shadow maps for directional lights, spot/point shadow maps and PCF filtering, with quality driven by `ShadowQuality`.
  - Done when: There is no shadow acne or peter-panning on the sample scene, every `ShadowQuality` level works, and cascade split math is unit-tested.
  - Deps: REN-06
- [x] **REN-08** `P2` Post-processing stack
  - Do: HDR render target, tonemapping (ACES), gamma, bloom, FXAA and SSAO as render-graph passes that can be toggled per camera/environment.
  - Done when: Effects can be toggled at runtime and from the editor. Pass ordering and enable/disable have unit tests.
  - Deps: REN-03
- [x] **REN-09** `P2` Fog improvements
  - Do: Move fog from `SkyDomeRenderer`/`LightingUniformsLoader` into a shared shader include. Support linear/exp/exp² and height fog, configured from `EnvironmentComponent`/`EnvironmentPreset`.
  - Done when: Fog looks the same on models, terrain and sky, and fog parameter math is unit-tested.
  - Deps: REN-05
- [x] **REN-10** `P2` Particle system
  - Do: Add a `ParticleEmitter` component (CPU first, GPU instancing for draw) with an emission shape, lifetime, velocity/size/color over life and a texture atlas.
  - Done when: Emitters can be added in the editor and play in game, and the simulation step is unit-tested.
  - Deps: REN-03, ECS-03
- [x] **REN-11** `P2` 2D/3D texture rendering improvements
  - Do: Clean up `Renderer2D`, `TextureRenderer3D` and sprite batching. Add mipmaps/anisotropy from `TextureQuality`, sprite atlases and billboards.
  - Done when: Sprites and billboards batch correctly, and texture quality settings take effect.
  - Deps: REN-02
- [~] @skate-render **REN-12** `P3` RHI abstraction (Vulkan-ready)
  - Do: Define backend-agnostic interfaces (`RhiDevice`, `RhiBuffer`, `RhiTexture`, `RhiPipeline`, `RhiCommandList`) and move all GL calls into an `OpenGlRhi` backend.
  - Done when: No GL call exists outside `render/rhi/opengl`. An architecture guard test enforces this.
  - Design: `docs/design/REN-12.md` approved by the lead (round 15) with the §7 proposals as written: ImGuiLayer is the one allowed GL user; named uniforms via `ShaderParams`; Vulkan semantics (pass begin/end, depth range, Y-flip) owned by the backend; sync picking readback; main-thread only. Migrate in the §5 steps, one or two per round, starting with steps 0-1.
  - Deps: REN-03, REN-04, REN-05
- [ ] **REN-13** `P3` Vulkan backend spike
  - Do: Build a minimal `VulkanRhi` that renders the sample scene's geometry pass.
  - Done when: A config flag switches backend, and the spike's findings are documented.
  - Deps: REN-12
- [x] **REN-14** `P1` Renderer2D primitives for runtime UI
  - Do: Add scissor push/pop, 9-slice quads and glyph quad submission to `Renderer2D` as required by `docs/design/GUI-01.md`.
  - Done when: Unit tests cover the new calls and a UI sample draws a clipped 9-slice panel with text.
  - Deps: GUI-01
- [x] **REN-15** `P1` `GpuResource : AutoCloseable`
  - Do: Introduce a `GpuResource : AutoCloseable` base (id, idempotent `close()`) for textures, buffers, VAOs, FBOs; make `CubeMap`, `DebugRenderer`, `PickingRenderer` own and free theirs (QA-04 findings).
  - Done when: No GL object is created without an owner that deletes it; tests with mocked GL.
- [x] **REN-16** `P2` Review SDF font branch of `shader_2d_batch.glsl`
  - Do: Review the SDF branch added by GUI-04 for correctness, batching and edge/outline quality.
  - Done when: Findings fixed or documented; test or screenshot check recorded.
  - Deps: GUI-04
- [x] **REN-17** `P1` PostProcessPass feedback loop and DebugPass target review
  - Do: From PHY-10: `PostProcessPass` samples `frameBuffer.getTextureId()` while that FBO is bound as the render target (undefined read/write feedback); ping-pong between two targets or use a separate output target. Review the PHY-10 `bindTarget` hook added to `DebugPass`/`RenderResourcesFactory` and fold it into the pass/target design.
  - Done when: No pass reads a texture attached to its bound target; post effects and debug lines show in the viewport; test for target selection.
  - Deps: REN-08, PHY-10
- [x] **REN-18** `P0` Debug overlays draw over the scene; skater looks transparent
  - Do: Human-reported on PR #12 after PHY-10 made debug lines visible again: the skater looks transparent and grid lines and camera/light indicators draw wrongly (order/depth). Find the root cause in the DebugPass/PostProcessPass target and state handling and fix it.
  - Done when: Opaque models are opaque, grid and collider lines depth-test against the scene, icons draw as intended; regression test where feasible.
  - Deps: PHY-10
- [x] **REN-19** `P3` Leftovers from REN-18
  - Do: Far grid lines near the floor edge still z-fight in the default frame; with post-processing fully off, transparent materials can show the viewport background through them.
  - Done when: No grid flicker at default camera distance and transparent materials composite correctly with post off; test where feasible.
  - Deps: REN-18
- [x] **REN-20** `P1` Shadows and picking cover the whole hierarchy
  - Do: From EDT-31: `ShadowPass` only renders top-level objects, so child models (e.g. the skateboard's visual child) cast no shadows; also review EDT-31's `PickingPass` change (whole-hierarchy traversal, no skip while selected). Do it on top of REN-07.
  - Done when: Child models cast shadows; picking pass reviewed; traversal test.
  - Deps: REN-07, EDT-31
- [x] **REN-21** `P1` 2D/UI render follow-ups from GUI-07 and EDT-31
  - Do: Fix `picking.glsl` reading `aTexId`/`aEntityId` at locations 8/9 while `RenderBatch2D` feeds 3/4 (2D sprite picking reads 0), the same mismatch GUI-07 fixed in `shader_2d_batch.glsl`. Draw runtime UI after post-processing and the debug pass so bloom/FXAA and grid lines don't affect it. Review EDT-31's `SelectionHighlight`/`GeometryPass` change (highlight inherited from ancestors).
  - Done when: 2D sprites are pickable, UI is unaffected by post effects and debug lines, attribute layout guarded by a test.
  - Deps: GUI-07, EDT-31, REN-18
- [x] **REN-22** `P0` Frame rate dropped to ~40 FPS in round 11
  - Do: Human-reported on PR #13: FPS fell drastically (to ~40) in the editor. Profile per pass and per system (CPU and GPU) on the default project, bisect across the round-11 merges (REN-07 shadows, REN-21 UiPass, PHY-11 transform sync, ANI-09 procedural poses, EDT-30, ECS-11, REN-10), find the cause(s) and fix them; report causes outside render as NEEDS.
  - Done when: The default project runs at the pre-round-11 frame rate (or better) at MEDIUM shadows; per-pass timings visible in the Render Graph window; a regression guard where feasible.
  - Deps: REN-07

### Asset management — `AST` (`skate-assets`)

Asset management works but needs a complete review.

- [x] **AST-01** `P0` Asset pipeline review
  - Do: Review `AssetsManager`, `Assets`, the loaders (`AssimpLoader`, `TextureLoader`, `SoundLoader`, `ShaderLoader`, `AnimationLoader`), `serialization/` and `AssetsResolver`.
  - Done when: `docs/design/AST-01.md` lists defects, threading issues and a target design (asset handles/GUIDs, async loading via `JobSystem`, ref-counting).
- [x] **AST-02** `P1` Stable asset IDs and handles
  - Do: Give every asset a GUID plus a `.meta` sidecar. Scenes and prefabs reference GUIDs, not paths.
  - Done when: Renaming or moving a file in the editor does not break references, and GUID/meta round-trips have unit tests.
  - Deps: AST-01
- [x] **AST-03** `P1` Async loading and unloading
  - Do: Decode on `JobSystem` workers and upload to the GPU on the main thread. Add placeholder assets while loading, ref-counted unload and an `AssetEvent` for load complete.
  - Done when: Scene load does not block the main thread on large models. Ref-count and lifecycle have unit tests.
  - Deps: AST-02
- [x] **AST-04** `P1` Serialization versioning
  - Do: Add a schema version to scene, prefab and project files, with migration functions.
  - Done when: Old files load through migrations, and each migration has a unit test.
  - Deps: AST-01
- [x] **AST-05** `P2` Hot reload of assets
  - Do: Watch the file system (reuse `FileSystemScanner`) and reload changed textures, models and sounds in place.
  - Done when: Editing a texture on disk updates the editor viewport.
  - Deps: AST-03
- [x] **AST-06** `P2` Export and asset packing improvements
  - Do: Rework `AssetsPacker` and `ProjectExporter`: pack only referenced assets, compress, write a manifest and checksums, and produce a runnable `gameJar`.
  - Done when: An exported sample project runs standalone, and packing and manifest generation have unit tests.
  - Deps: AST-02
- [x] **AST-07** `P1` Fix `PrefabsGenerator` scene mutation
  - Do: `PrefabsGenerator.kt` uses `scene.children.add/addAll`, which bypasses the ECS query caches. Use the scene API instead.
  - Done when: Generated prefabs invalidate queries correctly, covered by a test.
  - Deps: ECS-02
- [x] **AST-08** `P2` Loader ownership, results and logging
  - Do: From QA-04: `AssetsManager` owns texture/mesh/sound lifetimes and deletes on unload; loaders return a result type instead of `Boolean`/`null`; replace `println` in `AssimpExtensions`, `ShaderLoader`, `SoundLoader` with `LoggerService`.
  - Done when: Unloading frees native resources; no `println` in assets.
  - Deps: AST-03
- [x] **AST-09** `P2` Persist prefab templates as `.prefab` files
  - Do: Serialize `PrefabAsset.template` to a `.prefab` file via the asset serializer and load it back, so ECS-05 prefab instances survive project reload.
  - Done when: Round-trip test for prefab assets with instances and overrides.
  - Deps: ECS-05
- [x] **AST-07** `P1` Ship game strings in exported builds
  - Do: From EDT-37: bundle `assets/strings/game/` into exported builds (`ProjectExporter`/`AssetsPacker`) and load it from the pack in `GameMain`; today the exported game reads a relative path that isn't shipped.
  - Done when: An exported sample shows translated UI text (no raw keys) in EN and FR; packing and loading tested.
  - Deps: EDT-37
- [~] @skate-assets **AST-08** `P1` Export materials with their textures
  - Do: From EDT-17: add `skmat` to `ReferenceCollector.scanExtensions` so exports include the textures a `.skmat` uses, and make packed `.skmat` files readable at runtime through `AssetsResolver.resolveData`.
  - Done when: An exported project using a saved `.skmat` renders it correctly; packing and runtime loading tested.
  - Deps: EDT-17, EDT-09

### ECS — `ECS` (`skate-ecs`)

The ECS is in good working shape but needs a complete review. It is probably missing components, and caching is half implemented.

- [x] **ECS-01** `P0` ECS review
  - Do: Review `GameObject`, `Scene`, `SceneManager`, `SystemManager`, `GameObjectManager`, `TransformSystem` and component storage. Find all half-implemented caches (`Component.kt`, `System.kt`, `SceneManager.kt` and the systems that use them).
  - Done when: `docs/design/ECS-01.md` lists the issues and a target design (component storage, queries, system ordering, cache invalidation).
- [x] **ECS-02** `P1` Finish component query caching
  - Do: Cache component queries per system with invalidation when components are added or removed. Remove the ad-hoc caches in individual systems.
  - Done when: All systems use the shared query API, and invalidation has unit tests.
  - Deps: ECS-01
- [x] **ECS-03** `P1` Explicit system ordering and phases
  - Do: Define `PreUpdate / FixedUpdate / Update / LateUpdate / Render` phases with declared dependencies.
  - Done when: System order is deterministic, and ordering resolution has unit tests.
  - Deps: ECS-01
- [x] **ECS-04** `P2` Component gap analysis
  - Do: List the components missing compared with industry engines (e.g. `Tag/Layer`, `LOD`, `Trigger`, `AudioListener`, `Billboard`, `Lifetime`, `Prefab instance link`) and create follow-up tasks for the owning domains.
  - Done when: The gap list is in `docs/design/ECS-04.md` and the follow-ups are reported to the lead.
  - Deps: ECS-01
- [x] **ECS-05** `P2` Prefab instances and overrides
  - Do: Link instances to prefab assets with per-property overrides that survive prefab edits.
  - Done when: Editing a prefab updates its instances but keeps their overrides, with unit tests.
  - Deps: ECS-02, AST-02
- [x] **ECS-06** `P1` Remove engine dependency on `game.skateboard.TrickType`
  - Do: `InputEvents.kt` imports `game.skateboard.TrickType`. Make the event generic (or move the type into the engine) and update users.
  - Done when: An architecture guard shows `engine/` does not import `game/`.
- [x] **ECS-07** `P1` Multi-selection in `Scene`
  - Do: Add `selectedGameObjects` to `Scene` (keeping `selectedGameObject` as the primary) and update selection commands; needed by EDT-03 multi-target gizmos.
  - Done when: Gizmos transform all selected objects as a group; tests for selection add/remove/clear.
  - Deps: ECS-02
- [x] **ECS-08** `P1` Engine lifecycle, error logging and thread-safety
  - Do: From QA-04: `Engine.destroy()` always releases what was created and cancels `JobSystem`; route handler/job exceptions through `LoggerService` with the exception; assert main-thread-only scene mutation; make `EventSystem` and `LoggerService` thread-safe; replace `println` in `Window`, `JobSystem`, `EventSystem`, `StringManager`.
  - Done when: Tests for destroy-after-failed-start and exception logging; no `println` in engine/core.
- [x] **ECS-09** `P1` Units and coordinate-system conventions
  - Do: Write the convention (Y-up, right-handed, metres, degrees at component level, radians internally) in `docs/design/conventions.md` and add unit suffixes/KDoc to component fields.
  - Done when: Doc exists and public component fields state their units.
- [x] **ECS-10** `P1` Tag and Layer on GameObject
  - Do: From ECS-04: add `tag` and `layer` to `GameObject`, `Scene.findByTag/findByLayer`, and a layer-mask parameter on `System.query`/`ComponentQuery`. Follow-ups: EDT inspector field, REN camera culling mask, PHY layer-to-collision-group mapping.
  - Done when: Tag/layer lookups and layer-mask queries are unit-tested.
  - Deps: ECS-04
- [x] **ECS-11** `P1` Find child objects by id
  - Do: From EDT-31: `GameObjectManager.getGameObject(id)` only searches top-level objects, so hierarchy drag-and-drop and search providers can't find children. Search the whole hierarchy (indexed by id).
  - Done when: Lookups find objects at any depth; unit-tested.
- [x] **ECS-12** `P2` Review EDT-33 and REN-09 ECS changes
  - Do: Review EDT-33's top-level rule (parent is the Scene) and `GameObject.isTopLevel/ancestors()/linkChildren()`, `GameObjectManager.addGameObject(parent, index)`/`removeGameObject`, and REN-09's `EnvironmentComponent` fog fields and `EnvironmentSystem` fog cycle wiring. Fix issues, migrate remaining `parent == null` callers.
  - Done when: Review notes in the commit message; remaining callers use the helpers; tests green.
  - Deps: EDT-33, REN-09
- [x] **ECS-13** `P2` Review GUI-09 runtime pause hooks
  - Do: From GUI-09: review `System.runsWhilePaused`, `SystemManager.paused`, `Scene.isPaused` and the `AudioSystem` hold/resume; check every system's behaviour while paused (physics, animation, particles, camera control, UI input) and that Stop/restart clears pause.
  - Done when: Review fixes committed; pause semantics documented; tests for paused systems.
  - Deps: GUI-09

### Physics — `PHY` (`skate-physics`)

Physics needs overall improvements, a swappable physics engine, and a joint component.

- [x] **PHY-01** `P0` Introduce `Physics3DFactory`
  - Do: `PhysicsSystem` currently constructs `BulletPhysics3D` directly. Add a `Physics3DFactory` (backend chosen by config) and route all construction through it. The architecture guard already expects this.
  - Done when: No code outside the factory constructs `BulletPhysics3D`, and the guard test passes.
- [x] **PHY-02** `P1` Make `IPhysics3D` backend-agnostic
  - Do: Remove Bullet types from `IPhysics3D`/`IPhysicsBody3D`/`IPhysicsConstraint` signatures and keep them inside `physics3d/bullet/`.
  - Done when: Only the Bullet backend package imports `com.jme3.bullet`. A guard test enforces this.
  - Deps: PHY-01
- [x] **PHY-03** `P1` Joint component
  - Do: Add a `JointComponent` that supports fixed, hinge, cone-twist, slider and 6DOF joints, built on the existing `HingeConstraint3D`/`ConeTwistConstraint3D`. Make it serializable and editable.
  - Done when: Joints can be created in the editor and persist in the scene. Joint creation and limits have unit tests.
  - Deps: PHY-02
- [x] **PHY-04** `P2` Physics correctness and features
  - Do: Fixed timestep with interpolation, collision layers/masks, triggers, raycast/shape-cast queries, physics materials (friction, restitution), CCD for fast bodies, and collision events through `PhysicsEvents`.
  - Done when: Each feature has unit tests, and the skateboard does not tunnel at high speed.
  - Deps: PHY-02
- [x] **PHY-05** `P3` Second physics backend spike
  - Do: Evaluate Jolt (via JNI bindings) or another engine behind `IPhysics3D`.
  - Done when: The findings and a minimal prototype are documented.
  - Deps: PHY-02
- [x] **PHY-06** `P1` Run `PhysicsSystem` in the fixed-update phase
  - Do: Override `fixedUpdate` in `PhysicsSystem` and register it in `FIXED_UPDATE` per ECS-03; remove the per-frame stepping workaround.
  - Done when: Physics steps at a fixed rate independent of frame time, covered by a test.
  - Deps: ECS-03
- [x] **PHY-07** `P2` Remove per-frame allocations in physics/ragdoll sync
  - Do: Hoist temporaries to fields in ragdoll/physics sync and audit degree/radian call sites (QA-04).
  - Done when: No allocations in the hot path (reviewed), degree/radian audit documented.
- [x] **PHY-09** `P2` Raycast vehicle
  - Do: `VehicleComponent` (+ `WheelConfig`) on a `RigidBody3D` chassis, solved by a backend-agnostic `RaycastVehicleSolver` (raycast suspension, engine/brake, lateral grip) from `PhysicsSystem.fixedUpdate`; see `docs/design/PHY-09.md`.
  - Done when: Suspension, drive and grip are unit-tested and the component is serializable.
  - Deps: PHY-02, PHY-06
- [x] **PHY-10** `P0` Fix physics debug view not drawing collider boxes
  - Do: Human-reported on PR #12: the physics debug toggle no longer draws rigid body colliders in the editor. Find the root cause (round 10 or earlier render changes) and fix it.
  - Done when: Collider boxes draw again with debug on in editor and play; regression test.
  - Deps: PHY-09
- [x] **PHY-11** `P1` Sync physics bodies when a transform is edited in the editor
  - Do: Human-reported: editing a GameObject's position/rotation/scale in the inspector (or with gizmos/undo) does not update its `RigidBody3D`/collider in the physics world, so the body stays at its old pose. When a transform changes outside the simulation (edit mode, inspector/command edits, undo/redo, scene load), teleport the body to the new pose (reset velocities, rebuild the collider for scale changes) and keep the interpolation state consistent. Coordinate the editor-side change notification with `skate-editor`.
  - Done when: Moving, rotating or scaling an object in the editor moves its collider (visible with physics debug on), and Play starts from the edited pose; tests for teleport on transform change, scale rebuild and undo.
  - Deps: PHY-10
- [x] **PHY-12** `P0` Duplicate bodies and stuck skater on first run
  - Do: Human-reported on PR #12: the first run of a new default skate project shows duplicate collider boxes and the skater is stuck in the floor; it works after reopening. Register bodies exactly once in every path (new project, open, play/stop, scene switch).
  - Done when: First run works like a reopened project; body counts per path are unit-tested.
  - Deps: PHY-10
- [x] **PHY-13** `P2` Compound-hull and static mesh colliders
  - Do: From GPL-04: concave obstacles carry one `CustomCollider3D` per hull (the bowl has ~80, the half pipe 21). Add a single compound-hull collider component and a static triangle-mesh collider for static geometry, serialized, in both backends; switch the GPL-04 prefabs to them.
  - Done when: Bowl and half pipe each carry one collider component with the same collision; serialization and shape building unit-tested.
  - Deps: GPL-04
- [x] **PHY-14** `P0` Slider joint limits inverted (BUG-019)
  - Do: From QA-13: in `BulletPhysics3D`, negate and swap slider limits so `[min, max]` means body displacement along axisA; add a backend unit test.
  - Done when: `FeatureShowcaseSmokeTest.Showcase_Joints_SliderBlockDropsToItsLowerLimit` passes; backend test covers limits.
  - Deps: PHY-03

### Animation — `ANI` (`skate-animation`)

The animation system needs improvements.

- [x] **ANI-01** `P0` Animation review
  - Do: Review `AnimationSystem`, `Animator`, `SkeletonComponent`, `BoneOverride`, `AnimationLoader`, `BoneNameMapper` and `SkeletonMath`.
  - Done when: `docs/design/ANI-01.md` lists defects and a target design.
- [x] **ANI-02** `P1` Animation blending and state machine
  - Do: Crossfades, blend trees (1D/2D), an animation state machine asset with parameters and transitions, and animation events.
  - Done when: The player transitions smoothly between push, ride and trick states. Blending and transition logic have unit tests.
  - Deps: ANI-01
- [x] **ANI-03** `P2` Retargeting and masks
  - Do: Humanoid retargeting via `BoneNameMapper`, plus per-bone layer masks (e.g. upper body).
  - Done when: One animation plays on two different rigs, with unit tests for the mapping.
  - Deps: ANI-02
- [x] **ANI-04** `P2` IK
  - Do: Two-bone IK for feet on the board and hands, plus look-at.
  - Done when: The feet stick to the board during tricks, and the IK solver has unit tests.
  - Deps: ANI-02
- [x] **ANI-05** `P2` GPU skinning performance
  - Do: Upload bone palettes through a UBO/SSBO and skip the update for off-screen skinned meshes.
  - Done when: Profiler shows a reduced animation cost with many characters.
  - Deps: ANI-01, REN-02
- [x] **ANI-08** `P1` Persist Animator controller reference
  - Do: Save the ANI-02 controller asset reference on `Animator` and resolve it when a scene loads.
  - Done when: An animator's controller survives save/load, covered by a test.
  - Deps: ANI-02
- [x] **ANI-09** `P1` Skateboarding animation clips and controller
  - Do: From GPL-15: no skateboarding clips are bundled, so riding falls back to idle/walking. Source or author riding idle, push and ollie clips for the skater (the human may need to provide assets) and a controller asset wiring them into the ANI-02 state machine for `SkateBehavior`.
  - Done when: The skater uses a riding pose/push/ollie in the default project; the controller loads in a test.
  - Deps: GPL-15
- [x] **ANI-10** `P1` Use the real skateboard riding clip
  - Do: The human provided `assets/characters/animations/skate riding.fbx` (first real skateboarding clip). Import it for the skater (retarget if needed), put it in the ANI-09 controller's riding slot(s) instead of the procedural pose, and keep the procedural fallback for slots still missing clips.
  - Done when: Riding in the default project uses the clip (aligned on the deck, facing the nose, looping cleanly); the clip loads and maps to its slot in a test.
  - Deps: ANI-09

### Audio — `AUD` (`skate-audio`)

Audio needs a more complete implementation.

- [x] **AUD-01** `P1` Audio review and design
  - Do: Review `AudioEngine`, `AudioSystem`, `AudioComponent` and `SoundLoader`.
  - Done when: `docs/design/AUD-01.md` describes the target design: buses/mixer, voices, streaming, 3D.
- [x] **AUD-02** `P1` Mixer and buses
  - Do: Master/music/SFX/UI buses with volume and mute, wired to `AudioSettings`.
  - Done when: The settings change the volume live, and the mixer math has unit tests.
  - Deps: AUD-01
- [x] **AUD-03** `P1` 3D spatial audio
  - Do: Add an `AudioListener` component and attenuation models, plus Doppler and velocity from physics.
  - Done when: Sounds pan and attenuate correctly, and attenuation has unit tests.
  - Deps: AUD-01
- [x] **AUD-04** `P2` Streaming and voice management
  - Do: Stream long music files (OGG), cap voices with priority-based stealing, and add one-shot/looping/random-container sounds.
  - Done when: Music streams without loading the whole file into memory, and voice stealing has unit tests.
  - Deps: AUD-02
- [x] **AUD-05** `P3` Effects
  - Do: Reverb zones and low-pass filters via OpenAL EFX.
  - Done when: Effects can be configured per zone or bus.
  - Deps: AUD-02
- [x] **AUD-06** `P2` Closeable audio handles
  - Do: Make `AudioEngine` and `SoundSource` `AutoCloseable` (QA-04).
  - Done when: Handles are closed deterministically; tests.
  - Deps: AUD-02
- [x] **AUD-08** `P2` Mixer snapshot for editor undo and persistence
  - Do: Data side of EDT-25: `Mixer.snapshot()` and `AudioEngine.currentConfig` so the editor can undo and persist bus settings.
  - Done when: Snapshot/restore round-trip is unit-tested.
  - Deps: AUD-02
- [x] **AUD-09** `P0` Showcase audio is silent
  - Do: Human-reported after PR #14: no sound when approaching the FeatureShowcase Audio sphere in Play. Lead's lead (unverified): `AudioComponent.playOnAwake` defaults to false, the showcase never sets it, and nothing calls `play()`; also check the tone path (`projectAssetsDir` + `tone.wav`, Windows separator), WAV loading, OpenAL init/device, bus/master volumes and the 3D rolloff/maxDistance. Fix the root cause in audio and set the showcase demo up correctly.
  - Done when: In a new project, Play on FeatureShowcase plays the ambient tone and the 3D emitter gets louder/pans as the camera approaches; Stop silences them; a test through the real path (generate, save, load from disk, Play) asserts both sources are playing with the expected gains.
  - Deps: TLG-09
- [x] **AUD-10** `P1` MP3 playback and music in the default skate scene
  - Do: Human request: use `assets/sounds/music/aquarela do brasil.mp3` (local asset, kept out of git) as looping background music in the default skate `MainScene`. The loader only decodes OGG (stb_vorbis) and WAV, and only OGG streams. Add MP3 decoding (a small, permissively licensed pure-Java decoder) with streaming for long tracks, route music through the music bus, and make a missing file a logged warning, not an error.
  - Done when: (Changed by the human on PR #16) MainScene has no music; the FeatureShowcase Audio area's 3D emitter plays the track looping in Play instead of the generated tone (streamed), the tone and its 2D ambient layer are removed; a missing file is tolerated; MP3 decode/stream tested; the showcase smoke test asserts it.
  - Deps: AUD-09
- [x] **AUD-11** `P0` Music pitch drops when the camera glides (Doppler)
  - Do: Human-reported on PR #16: the showcase song slows down when clicking demo-area buttons. The listener velocity is derived from camera motion, so the fast focus glide pitch-shifts sources via `SpatialAudio.dopplerPitch`. Music must never be Doppler-shifted (dopplerFactor 0 for music/Music bus sources), and camera-derived listener velocity should not produce Doppler from camera cuts/glides (e.g. only from a physics body, or clamped/smoothed).
  - Done when: The song's pitch stays constant while gliding/flying in the showcase; Doppler still works for SFX moving with physics; tests.
  - Deps: AUD-10

### Input — `INP` (`skate-input`)

Input works well but needs review and controller dead-zone support.

- [x] **INP-01** `P1` Input review
  - Do: Review `engine/input/**`, `InputSystem` and the listeners.
  - Done when: The issues are documented in `docs/design/INP-01.md` and the quick fixes are applied.
- [x] **INP-02** `P1` Configurable controller dead zones
  - Do: `InputSystem` has a hardcoded scalar dead zone (`InputSystem.kt` ~L135). Add per-stick and per-trigger dead zones (axial and radial, plus an outer dead zone and a response curve), configurable in `InputSettings` and the editor's `KeyBindingsWindow`.
  - Done when: Dead zones persist in the settings, and each mode has unit tests.
- [x] **INP-03** `P2` Action maps and rebinding
  - Do: Context-based action maps (gameplay/UI/editor), runtime rebinding with conflict detection, and saved bindings per project.
  - Done when: A binding can be changed in the editor and used in game. Conflict detection has unit tests.
  - Deps: INP-01
- [x] **INP-04** `P3` Rumble and controller hot-plug
  - Do: Handle controller connect/disconnect events and rumble/haptics where GLFW allows.
  - Done when: Unplugging and replugging a controller keeps the player's input working.
- [x] **INP-05** `P1` Engine-level key/action read API
  - Do: Provide an engine API to read keys/actions that does not depend on the editor, as required by `docs/design/SCR-01.md`.
  - Done when: Scripts and runtime code read input without editor imports, covered by tests.
  - Deps: INP-01
- [x] **INP-06** `P2` Lift gamepad button cap to 32 (ButtonStates)
- [x] **INP-08** `P2` Cursor capture mode API
  - Do: `CursorMode` (normal/hidden/captured) with `set/getCursorMode` on the input provider; no cursor jump on mode change.
  - Done when: Mode changes are unit-tested without a window.
  - Deps: INP-05
- [x] **INP-09** `P2` Review the EDT-30 pointer contract
  - Do: From EDT-30: review the `MouseListener`/`InputProvider` pointer source (`PointerSource`, view rect, target size) and the new contract that `RawInput.mouseX/Y` report render-target pixels; align input docs and remaining callers.
  - Done when: Contract documented in the input design note, remaining callers consistent, mapping tests cover game and editor sources.
  - Deps: EDT-30

### Gameplay — `GPL` (`skate-gameplay`)

The player controller was hardcoded at first and needs a big overhaul. Some skateboarding prefab models are wrong, and many more prefabs are needed.

- [x] **GPL-01** `P0` REWRITE Player/skate controller
  - Do: Remove the hardcoded values and logic from `PlayerController`, `SkateboardController` and `PlayerMotionSystem`. Build a data-driven character controller with a state machine (idle, push, ride, air, grind, manual, bail), with tuning in a serializable config asset.
  - Done when: All tuning values are editable in the editor, the game-specific logic lives in `game/`, and the state transitions have unit tests.
  - Deps: ECS-03, PHY-02
- [x] **GPL-02** `P1` Generic character controller in the engine
  - Do: A reusable kinematic/dynamic character controller (slopes, steps, ground snapping) in `engine/` that games can extend.
  - Done when: A non-skate sample (TLG-03) uses it, and the ground detection math has unit tests.
  - Deps: GPL-01
- [x] **GPL-03** `P1` Fix wrong skate prefab models
  - Do: Audit `Skater`, `Skateboard`, `Floor` and `PrefabsGenerator` against their source assets, and fix scale, pivots, orientation and materials.
  - Done when: All prefabs spawn correctly oriented and scaled. A checklist is in the PR.
- [x] **GPL-04** `P2` Prefab library expansion
  - Do: Add skatepark prefabs (ramps, quarter/half pipes, rails, ledges, stairs, boxes, bowls) and generic prefabs (lights, props, triggers, spawn points). See `docs/obstacles.md`.
  - Done when: The prefabs appear in the editor's prefab picker with thumbnails.
  - Deps: GPL-03
- [x] **GPL-05** `P2` Camera system
  - Do: Make `GameCamera` data-driven (follow, orbit, collision avoidance, spring arm, shake).
  - Done when: Camera parameters can be tuned in the editor, and spring-arm collision has unit tests.
  - Deps: GPL-01
- [x] **GPL-06** `P2` Skate push/ride/trick animation controller
  - Do: Author the push/ride/trick controller on top of the ANI-02 state machine and blend trees.
  - Done when: The player transitions between push, ride and trick states with blending in the sample game.
  - Deps: ANI-02, GPL-01
- [x] **GPL-07** `P2` Fix `CameraInterpolator.lerpToPreset` (BUG-014)
  - Do: `handleLerp` is private and never called, so `lerpToPreset` does nothing. Wire it into the camera update and re-enable the disabled `CameraTest` lerp case.
  - Done when: `CameraTest` lerp passes and BUG-014 is removed from `docs/bugs.md`.
- [x] **GPL-08** `P2` `GameMain` logging and paths
  - Do: Replace `println` and the hard-coded path in `GameMain` (QA-04, BUG-011).
  - Done when: No `println` or hard-coded paths in `game/`.
- [x] **GPL-09** `P1` Walk/skate mount toggle
  - Do: The human could walk but there is no way to switch between walking and skating. Add a `toggle_mount` input action (INP-03 action map; default gamepad button and keyboard key, rebindable) that mounts/dismounts the board: dismounted = generic locomotion (GPL-02), mounted = `SkateBehavior`; require the board to be within a configurable range when mounting, place the board under the character/at the character's feet, keep skateboarding-specific logic in `game/` and the generic mount/dismount interface in the engine so other games can use it for vehicles/mounts; add the action to the standalone controller config; tell the human which button/key is used.
  - Done when: In the sample game the player can toggle between walking and skating with gamepad and keyboard; unit tests for the state transitions and range check.
  - Deps: GPL-01, INP-03
- [x] **GPL-10** `P2` Wire foot-target IK to the skate/character controllers
  - Do: Implement docs/design/ANI-04-ik-wiring.md on the gameplay side (foot targets from ground raycasts).
  - Done when: Feet plant on slopes in the sample; tests for target computation.
  - Deps: ANI-04
- [x] **GPL-11** `P1` Make the walk/skate toggle actually switch modes
  - Do: Human-reported: pressing E / gamepad Y (GPL-09) changes `mounted` but nothing visible happens. Wire the toggle so mounting switches the active locomotion (character controller off, skateboard physics/controller on, board attached to the player, camera preset change) and dismounting reverses it, and the player can walk away from the board. Keep it engine-generic (`Mountable`).
  - Done when: In the sample, the player walks to the board, presses the toggle and skates, presses it again and walks; tests cover the state transitions.
  - Deps: GPL-09
- [x] **GPL-12** `P1` Align skateboard visual and rigid body
  - Do: Human-reported: the skateboard model and its rigid body/collider are misaligned (offset, rotation, scale, centre of mass), causing many gameplay issues. Fix the prefab, collider shape and offsets, and the controller's use of them; add a debug check (collider vs mesh bounds) test. Coordinate with GPL-03 (same prefab files) — do GPL-03 and GPL-12 together.
  - Done when: Board mesh, collider and rigid body coincide in the sample; ride, mount (GPL-11) and collisions behave; bounds-alignment unit test.
  - Deps: GPL-03
- [x] **GPL-13** `P1` Ride the board (stance on deck) and fix board scale
  - Do: Human-reported after GPL-11: mounting leaves the board standing between the player's legs; the player must RIDE on top of it. Define a deck anchor/stance (feet on the deck, board under the player, facing along the board, centre of mass and collider matched), with the character parented/posed on the board while mounted (engine-generic `Mountable` seat/stance data, not skateboard-only). Also fix scale: the skateboard model is huge relative to the player; normalise model scale in the prefab/importer so board length fits the character, and make sure the collider and its physics-debug wireframe match the mesh (the human now sees extra wireframes and the board's own wireframe is missing; check for duplicated or mis-scaled collider/child bodies introduced by GPL-03/12).
  - Done when: Mounted player visibly stands on the deck, board scale is proportionate, physics debug shows one collider matching the board; tests for stance placement and scale.
  - Deps: GPL-11, GPL-12
- [x] **GPL-14** `P1` Fix skateboard orientation
  - Do: Human-reported after PR #9: the skateboard is still wrongly oriented (model axes vs forward direction, deck up, nose/tail). Fix it in the prefab/importer/visual child so mesh, collider, stance and movement direction agree (GPL-13).
  - Done when: Board faces the travel direction with deck up in the sample, physics debug matches; test for orientation.
  - Deps: GPL-13
- [x] **GPL-15** `P0` Riding: alignment, pose and floating
  - Do: Human-reported on PR #12: when riding, the skater is misaligned with the board, has no riding pose/animation, and skater and board float. Snap the skater to the deck pose, play a riding animation via the ANI-02 state machine, and fix the floating root cause.
  - Done when: Riding in the default project looks grounded and aligned with a riding pose; mount pose math and physics state switch unit-tested; `docs/testing/GPL-15.md`.
  - Deps: GPL-14
- [x] **GPL-16** `P1` Game-ready skateboard mesh
  - Do: From REN-22: the skateboard prefab's model has 2.57M indices (~858K triangles, 55 parts); its shadow costs ~1.5 ms and picking ~1 ms per frame. Replace it with a decimated/game-ready mesh under ~20K triangles, keeping the GPL-14 orientation and collider fit.
  - Done when: The board renders the same at a glance with under 20K triangles; the frame time while orbiting is below the pre-round-11 baseline.
  - Deps: REN-22
- [x] **GPL-17** `P3` Deterministic skater prediction step
  - Do: From NET-03: a kinematic skater step (`PredictionStep`) that client and server run with the same result, reading only replicated state (no Bullet), so NET-03 prediction can be wired into the skate game, with a Feature Showcase demo.
  - Done when: Client and server produce identical skater states from the same inputs in a test; a showcase demo shows prediction under simulated latency.
  - Deps: NET-03

### In-game UI — `GUI` (`skate-gameui`)

In-game UI components need to be added (separate from the ImGui editor).

- [x] **GUI-01** `P1` Runtime UI design
  - Do: Design a retained-mode UI for games: canvas, anchors and layout, and widgets (text, image, button, slider, progress bar, panel, list), with input focus and gamepad navigation, rendered through `Renderer2D` and `StringManager`.
  - Done when: `docs/design/GUI-01.md` is approved by the lead.
- [x] **GUI-02** `P1` Core widgets and layout
  - Do: Implement GUI-01 in `engine/ui/` as ECS components and a UI system.
  - Done when: The sample game shows a HUD (score, trick name, timer) and a pause menu. Layout has unit tests.
  - Deps: GUI-01, REN-11
- [x] **GUI-03** `P2` UI authoring in the editor
  - Do: Edit UI canvases in the editor viewport; coordinate the editor side with `skate-editor`.
  - Done when: UI can be built and saved without code.
  - Deps: GUI-02
- [x] **GUI-04** `P2` Fonts and localization
  - Do: SDF font rendering, plus localized strings and plurals via `StringManager`.
  - Done when: Text stays crisp at all sizes, and switching locale updates the UI.
  - Deps: GUI-02
- [x] **GUI-05** `P0` UI components added by hand do not show
  - Do: Human-reported on PR #12: adding `UICanvasComponent`/`UIButtonComponent` to an empty GameObject via Add Component shows nothing in the editor or in game. Make that path work (auto-add required components or show a clear hint) and keep the create-UI menu path working.
  - Done when: A hand-built canvas + button renders in editor and game, is editable with the rect gizmo and survives save/reload; tests; `docs/testing/GUI-03.md` updated.
  - Deps: GUI-03
- [x] **GUI-06** `P0` Button hover state not visible
  - Do: Human-reported on PR #12: hovering a button does not change its colour. Check hit-testing and coordinate mapping (editor viewport and game), input in edit/play modes, and the colour states.
  - Done when: Hover/press colours show in Play and in the game; behaviour documented in `docs/testing/GUI-03.md`; tests.
  - Deps: GUI-05
- [x] **GUI-07** `P0` Button backgrounds don't render properly
  - Do: Human-reported on PR #12 after GUI-06/REN-18: hover/press work but button backgrounds render incorrectly. Find the root cause (UI colours or the REN-18 render state/alpha changes) and fix it.
  - Done when: Button backgrounds render solid in all states in editor and game; regression test.
  - Deps: GUI-06, REN-18
- [ ] **GUI-08** `P3` Review AST-07 string loading changes
  - Do: From AST-07: review `engine/core/StringManager.kt`, `StringTables.kt`, the new `StringSource.kt` and `GameMain.loadGameStrings()` (pack-based game strings), and align with EDT-37's per-area tables.
  - Done when: Review fixes committed; runtime UI strings load from files and packs alike; tests.
  - Deps: AST-07
- [x] **GUI-09** `P1` Pause menu in editor Play
  - Do: Human request on PR #16: Esc opens the pause menu in editor Play too, not only in the exported game. Move the pause menu (`game/ui/GameUi.kt`, opened from `GameMain`) into runtime code that both `GameMain` and editor Play use (Resume, Restart, Language, Quit; in the editor, Quit/Restart map to Stop/restart-play). Route Esc to the game while playing (the editor's Esc = deselect only when not playing); coordinate the editor input side with `skate-editor`. Pausing freezes simulation, audio and animation; Resume continues.
  - Done when: Esc toggles the pause menu in editor Play and in the exported game, with identical behaviour; Esc still deselects while editing; tests through the real path (load from disk, Play, Esc).
  - Deps: AST-07
- [x] **GUI-10** `P0` Game language must not change the editor language
  - Do: Human-reported on PR #17: switching language in the game's pause menu (editor Play) also switches the editor UI. Give the runtime/game its own locale (game string tables, saved in game settings) separate from the editor's locale; editor Play starts with the game locale; Stop leaves the editor language untouched.
  - Done when: Changing the game language in editor Play changes only runtime UI text; the editor stays in its language; exported game unaffected; tests.
  - Deps: GUI-09

### Scripting — `SCR` (`skate-scripting`)

Scripting needs to be implemented. Kotlin Script is the primary language; TypeScript and maybe Lua come later.

- [x] **SCR-01** `P1` Scripting architecture
  - Do: Design a language-agnostic script API (lifecycle `onCreate/onUpdate/onFixedUpdate/onDestroy`, component access, events, input, physics queries) behind a `ScriptHost` interface. The existing `NativeScriptComponent`/`ScriptingSystem` is the starting point.
  - Done when: `docs/design/SCR-01.md` is approved by the lead.
- [x] **SCR-02** `P1` Kotlin Script (`.kts`) host
  - Do: Embed the Kotlin scripting host, compile scripts with caching, and expose the API from SCR-01.
  - Done when: A `.kts` script attached to an entity runs in the editor's play mode and the exported game. API bindings have unit tests.
  - Deps: SCR-01
- [x] **SCR-03** `P2` Script hot reload and error reporting
  - Do: Recompile changed scripts, keep state where possible, and show errors in `ConsoleWindow` with file and line.
  - Done when: Editing a script during play mode applies without restarting.
  - Deps: SCR-02
- [x] **SCR-04** `P3` TypeScript host
  - Do: Embed a JS engine (e.g. GraalJS or Javet), transpile TS, and use the same `ScriptHost` API.
  - Deps: SCR-02
- [x] **SCR-05** `P3` Lua host
  - Do: Embed LuaJ (or similar) using the same `ScriptHost` API.
  - Deps: SCR-02
- [x] **SCR-06** `P1` Script code-completion service
  - Do: Language-agnostic `CompletionProvider` in the script API (engine side, no ImGui): given script text and cursor offset, return completions (members/methods of the script API, entity/component properties, keywords, snippets) with signature and doc text; implement for Kotlin Script using the host's REPL/IDE-style completion where available, otherwise an index of the public script API built by reflection and the Kotlin symbol table; design so TypeScript/Lua hosts can plug in. Fast and cancellable, never on the render thread.
  - Done when: Unit tests for member, top-level and partial-identifier completion and for error-tolerant text; documented extension point for other hosts.
  - Deps: SCR-03

### Networking & platform — `NET` (`skate-online`)

Multiplayer and Steam support need to be added.

- [x] **NET-01** `P2` Multiplayer architecture
  - Approved by the human: Netty UDP transport instead of the own UDP layer; 60 Hz tick / 20 Hz snapshots / 100 ms interpolation; predict the local player only; listen-server is first-class (loopback); 16 players for v1. Update docs/design/NET-01.md accordingly.
  - Do: Choose a model (client-server with prediction and reconciliation is recommended), transport (UDP, e.g. via Netty or KryoNet), replication of ECS components, and RPCs.
  - Done when: `docs/design/NET-01.md` is approved by the lead.
  - Deps: ECS-03
- [x] **NET-02** `P2` Transport and replication MVP
  - Do: Replicate transforms and player state for 2+ clients with interpolation.
  - Done when: Two local clients see each other skate. Serialization and delta compression have unit tests.
  - Deps: NET-01
- [x] **NET-03** `P3` Client-side prediction and lag compensation
  - Deps: NET-02
- [x] **NET-04** `P2` Steam integration
  - Do: Integrate Steamworks via steamworks4j behind a `PlatformServices` interface (achievements, stats, leaderboards, rich presence, lobbies), with a no-op fallback when Steam is absent.
  - Done when: The game runs with and without Steam. The fallback and the interface have unit tests.
- [x] **NET-05** `P3` Steam networking transport
  - Do: Implement the NET-02 transport over Steam networking sockets and lobbies.
  - Deps: NET-02, NET-04
- [x] **NET-06** `P2` Update NET-01 design for Netty
  - Do: Revise `docs/design/NET-01.md` §3/§12 to use Netty (UDP, reliability layer on top as needed) and record the approved decisions. Keep the transport behind the `Transport` interface.
  - Done when: Design note updated and committed; NET-02 can start.
  - Deps: NET-01

---
- [~] @skate-online **NET-06** `P2` Review GPL-17 prediction wiring
  - Do: From GPL-17: review `engine/net/PredictionModels.kt`, `engine/net/LoopbackPrediction.kt` and their registration in `Serializer`/`Engine.kt`; update `docs/testing/NET-03.md` (prediction is now wired into the game via the Netcode showcase demo).
  - Done when: Review fixes committed; docs updated; tests green.
  - Deps: GPL-17

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
- [x] **EDT-02** `P1` ImGui and viewport configuration
  - Do: Clean up `ImGuiLayer`/`WindowManager`/`WindowRegistry`. Fix the viewport's resizing, DPI scaling and multi-viewport handling, and the game view vs. scene view split.
  - Done when: The viewport renders at the correct resolution and DPI on resize and when moved between monitors.
  - Deps: EDT-01
- [x] **EDT-03** `P1` Modernize gizmos
  - Do: Rework the translate/rotate/scale gizmos (`editor/gizmos`): screen-space constant size, plane handles, local/world toggle, snapping, multi-selection pivot, hover highlight and correct picking.
  - Done when: All gizmos work under any camera angle, and gizmo math has unit tests.
- [x] **EDT-04** `P1` Visual representation of cameras and lights
  - Do: Billboard icons plus wire gizmos (camera frustum, light range/cone, direction arrow), all selectable.
  - Done when: Every camera and light is visible and clickable in the scene view.
  - Deps: EDT-03
- [ ] **EDT-05** `P2` Tooltips everywhere
  - Do: Add tooltips (via `StringManager` keys) to every toolbar button, property and menu item.
  - Done when: No interactive widget lacks a tooltip. A test checks that all tooltip keys exist in the string files.
- [x] **EDT-06** `P2` Complete translations
  - Do: Find hardcoded UI strings and missing keys between `assets/strings/editor_strings.properties` and the locale files.
  - Done when: A unit test fails on missing keys, and `fr` is complete.
- [x] **EDT-07** `P2` Theming support
  - Do: Make `ImGuiStyleManager` load themes (colors, sizes, fonts) from files, ship light/dark/high-contrast themes, and add a theme picker in `EditorSettingsWindow`.
  - Done when: Switching theme applies live and persists. Theme parsing has unit tests.
- [x] **EDT-08** `P2` Shell command panel
  - Do: Add a dockable terminal-style panel that runs shell commands (Gradle tasks, the TLG-01 CLI) asynchronously, streams output, and keeps history.
  - Done when: Commands run without blocking the UI and can be cancelled.
- [x] **EDT-09** `P2` Export UX
  - Do: Add an editor-side export wizard on top of AST-06 (target, included scenes, compression, output folder) with progress reporting.
  - Done when: A sample project can be exported from the UI.
  - Deps: AST-06
- [ ] **EDT-10** `P3` General editor UX pass
  - Do: Keyboard shortcuts audit, consistent icons, empty states, error dialogs and undo coverage for every mutation.
  - Done when: The checklist is in `docs/design/EDT-10.md` and its items are resolved.

---
- [x] **EDT-13** `P1` Route project-window file operations through asset commands
  - Do: Route `RenameFileCommand`, `DeleteFileCommand` and project-window moves through the asset-ID-aware API so `.meta` files stay in sync (unblocks AST-02).
  - Done when: Renaming, deleting or moving a file in the editor keeps its `.meta` and GUID; tests under `editor/commands`.
  - Deps: AST-02
- [x] **EDT-15** `P1` Joint component inspector
  - Do: Add editor UI to create, edit and remove the PHY-03 joint component (type, connected body, anchors, limits, motor), through commands in `editor/commands`.
  - Done when: Joints can be created and edited in the editor with undo/redo; command tests under `editor/commands`.
  - Deps: PHY-03
- [x] **EDT-14** `P2` Audio bus and mute UI
  - Do: Call `engine.audioEngine.applyConfig(settings.audio.toConfig())` on startup. Add UI bus volume, mute toggles and the new `AudioComponent` inspector fields from `docs/design/AUD-01.md`.
  - Done when: Bus volumes and mutes can be edited in the editor and persist.
  - Deps: AUD-02
- [x] **EDT-16** `P2` Animator inspector: controller and mask selection
  - Do: Expose the ANI-02 controller asset and ANI-03 bone mask selection in the Animator inspector via commands.
  - Done when: Controller and mask can be assigned in the editor with undo/redo.
  - Deps: ANI-03
- [x] **EDT-17** `P2` Material inspector and save-as-asset
  - Do: Add a material inspector and a "save material as asset" action backed by the REN-04 `.skmat` assets, via commands.
  - Done when: A material can be edited and saved as `.skmat` from the editor.
  - Deps: REN-04
- [x] **EDT-18** `P2` Editor logging and user-data paths
  - Do: From QA-04: replace `println` in `SearchHistory`, `SearchEngine`, `TranslateGizmo`, `Component.kt`; log in `captureScreenshot`; move the wizard default path and `search_history.json` to the user-data directory.
  - Done when: No `println` in editor; user data stored outside the working directory.
- [x] **EDT-19** `P1` Input Testing window rework
  - Do: Rework `InputTestingWindow` into the single place to inspect and tune input devices. Today dead zones are hard-coded (`0.15`/`0.1`), only joystick 1 is shown and gamepad settings are split with the Key Bindings window. Show all connected devices (keyboard, mouse, gamepads) live; per stick: raw vs processed values, dead-zone shape (axial/radial), inner/outer dead zone, response curve and sensitivity, all editable via commands and persisted through the INP-02 settings; trigger thresholds; button/axis map of the active gamepad layout; recent-event log with clear/pause; copy-to-clipboard of device info; clean ImGui layout with tooltips and translated strings.
  - Done when: Dead zones and sensitivity are edited here and take effect immediately in-game; no hard-coded dead zones remain; window documented in `docs/`; tests for the settings commands.
  - Deps: INP-02
- [x] **EDT-20** `P1` Key Bindings window rework
  - Do: Rework `KeyBindingsWindow` on top of the INP-03 action maps: list actions by category with search/filter; per-action keyboard, mouse and gamepad bindings with a click-to-rebind capture (cancel/clear); conflict detection with swap/unbind choices; reset per action/category/all; named profiles with import/export; gamepad-specific settings that currently live here move to the Input Testing window (EDT-19) so each setting has exactly one home; all changes via commands with undo/redo; translated and with tooltips.
  - Done when: Any action can be rebound for keyboard and gamepad, conflicts are detected, bindings persist; command tests under `editor/commands`.
  - Deps: INP-03, EDT-19
- [x] **EDT-21** `P1` View/Windows menu update
  - Do: Update `ViewMenuBuilder` so View > Windows lists every window: group entries by category (Scene, Assets, Debug, Input, Scripting, Audio, Settings) with separators or submenus, show each window's shortcut and open state, add the new Key Bindings and Script Editor entries (EDT-20/22); Input Testing is a dialog (EDT-24) and is NOT listed here with translated names (`menu.view.windows.*`), add 'Open all'/'Close all' and make `WindowRegistry` carry category metadata. Layout persistence (EDT-01) must keep working.
  - Done when: All windows appear in the menu under the right category; opening/closing persists across sessions; menu test or registry test covers category grouping.
  - Deps: EDT-20, EDT-22, EDT-24
- [x] **EDT-22** `P1` Script editor window
  - Do: Add a Script Editor window for `.kts` scripts (SCR-02/03): open a script from the project window or an attached `ScriptComponent`, edit text with line numbers and basic syntax highlighting, save (Ctrl+S) which triggers hot reload, show compile/runtime errors inline and in a list that jumps to the line, create-new-script from a template, and register the window in `WindowRegistry` (appearing in View/Windows via EDT-21). Edits go through commands where they mutate the project.
  - Done when: A script can be created, edited, saved and hot-reloaded from the editor and errors are shown with line numbers; tests for the non-UI logic.
  - Deps: SCR-03
- [x] **EDT-23** `P1` Fix translate gizmo behaviour and look
  - Do: The human reports the translation gizmo (EDT-03) looks 'a little weird' in the viewport. Review `GizmoMath`/`TranslateGizmo` against the viewport (axis arrows, screen-constant size, plane handles, hover/active colours, depth/occlusion, perspective and orthographic cameras, local vs world, multi-selection pivot with ECS-07) and fix anything off; compare with the conventions of common editors (X red, Y green, Z blue, handles drawn on top, no distortion near the screen edges).
  - Done when: Gizmo renders and drags correctly in perspective and ortho, local and world space, with multi-selection; screenshot or unit tests for `GizmoMath`; findings listed in the report.
  - Deps: EDT-03, ECS-07
- [x] **EDT-24** `P1` Input Testing as a closable dialog
  - Do: Convert `InputTestingWindow` (EDT-19) from a dockable window into a modal-less dialog: closable with the title-bar X, a Close button and Esc; opened from a new Input > 'Input Testing...' menu item (and a shortcut), not shown in View > Windows and excluded from docked layout persistence (EDT-01) except for remembering its size/position. Reuse or extend the existing dialog pattern (`ProjectSwitcherDialog`, `ProjectWizardWindow`) into a small reusable `EditorDialog` base so other tool dialogs can use it. Keep every EDT-19 feature (devices, editable dead zones/curves, event log). Update `ViewMenuBuilder`/`WindowRegistry` so the entry is removed there and translations are added; migrate any saved layout that references the old window.
  - Done when: Dialog opens from the menu, closes via X/Esc/Close button, is not in View > Windows, settings still persist, no stale layout entry; tests for the registry/menu change and the dialog state.
  - Deps: EDT-19
- [x] **EDT-25** `P2` ReverbZone inspector and audio bus effect controls
  - Do: Inspector for `ReverbZone` and bus low-pass/reverb-send controls in the audio mixer UI, via commands.
  - Done when: Settings editable with undo and persisted.
  - Deps: AUD-05
- [x] **EDT-26** `P1` Script editor code completion UI
  - Do: In the EDT-22 Script Editor, add a completion popup (Ctrl+Space and automatic after `.`/identifier characters) fed by SCR-06 off the UI thread: list with kind icons, signature and doc tooltip, Up/Down/Enter/Tab/Esc handling, filter as you type, snippet insertion, plus parameter hints and bracket auto-close. Add editor settings to toggle auto-popup and its delay.
  - Done when: Typing in a script shows context-appropriate completions and accepts them; UI-independent logic (filtering, insertion, cursor handling) unit-tested; translations added.
  - Deps: EDT-22, SCR-06
- [x] **EDT-27** `P1` Scene reset must restore everything
  - Do: Human-reported: resetting the scene in the editor does not reset everything. Audit the reset path (scene reload/restore snapshot, physics bodies and velocities, scripts, audio, animation state, ragdolls, mounted state, selection, undo history) and make it restore the exact pre-play state.
  - Done when: Play, mutate, reset returns every object/component to its saved state; tests for the non-UI logic.
- [x] **EDT-28** `P1` Hierarchy empty after creating a new project
  - Do: Human-reported: after creating a new project in the editor, the Scene Hierarchy window does not show the current scene's game objects. Find why the hierarchy is not bound to the new project's active scene (stale scene reference, event not fired, lazy load) and fix it.
  - Done when: New project shows its scene's objects in the hierarchy immediately; regression test.
- [x] **EDT-29** `P2` Wire shadow quality setting to the engine
  - Do: From REN-07: wherever `GraphicsSettings` is applied, set `ShadowSettings.quality = ShadowQualityLevel.fromName(graphicsSettings.shadowQuality.name)`; add a LOW/MEDIUM/HIGH selector for `ShadowPass` in `RenderGraphWindow`.
  - Done when: Changing shadow quality in settings or the render graph window switches shadows at runtime; test for the mapping.
  - Deps: REN-07
- [x] **EDT-30** `P1` Unify editor mouse coordinates
  - Do: From GUI-06: `MouseListener` mixes GLFW window coordinates with ImGui desktop coordinates, so code comparing `getMouseX()` with `gameViewportPos` (e.g. `InputProvider` lines 91-92) is off when the window is not at the desktop origin or with multi-viewports. Give the editor one viewport-relative pointer source and retire the GUI-06 `Engine.uiPointer` workaround onto it.
  - Done when: Picking, gizmos and UI hover hit the cursor exactly with the editor window anywhere on screen; mapping unit-tested.
  - Deps: GUI-06
- [x] **EDT-31** `P0` Clicking the skateboard selects the floor
  - Do: Human-reported on PR #12: picking misses the skateboard (model on a child object) and selects the floor behind it. Make picking reliable for child models and document whether the child or the prefab root is selected.
  - Done when: Clicking any visible model selects it (per the documented convention); id-to-object resolution unit-tested.
  - Deps: GPL-13
- [x] **EDT-32** `P1` Redo after a gizmo drag does nothing
  - Do: From PHY-11: `TransformCommand` stores a shallow `newTransform.copy()` and `Gizmo.commitSnapshot` passes the live `Transform`, so old/new share vectors with the object and redo has nothing to reapply. Snapshot both deeply (`Transform().apply { copyFrom(x) }`).
  - Done when: Undo/redo of gizmo moves, inspector edits and multi-selection transforms round-trip; tests.
- [x] **EDT-33** `P1` One rule for top-level objects in the hierarchy
  - Do: From ECS-11 (also bit TLG-09: lookups climbing parent links fail on scenes loaded from disk): `SceneHierarchyWindow` mixes `obj.parent == null` and `obj.parent == scene` to detect top-level objects, while `addGameObject` does not set `parent`. Decide with skate-ecs (prefer `parent == scene`, set by `addGameObject`) and make every caller consistent.
  - Done when: Top-level detection uses one helper everywhere; drag/drop and reparenting tests pass.
  - Deps: ECS-11
- [x] **EDT-34** `P1` Frame-rate independent editor camera
  - Do: Human-reported on PR #13: editor camera movement (fly, orbit, pan, zoom, focus lerp) is tied to the frame rate. Scale every camera motion by real frame delta time, with clamping for hitches.
  - Done when: Camera speed is the same at 30, 60 and 144 FPS; unit-tested with simulated frame times.
- [ ] **EDT-35** `P3` Drop the ignoreImGui workarounds for fly-cam look and UI pointer
  - Do: From INP-09: in `Engine.kt`, replace the two `ignoreImGui = true` workarounds with `reader.isMouseButtonDown(1)` for fly-cam look and the default `ReaderUiInputSource` pointer, so look keeps working when the cursor leaves the view mid-drag.
  - Done when: Fly-cam look and UI pointer behave as now plus mid-drag exit keeps working; tests.
  - Deps: INP-09
- [x] **EDT-36** `P2` Edit String fields in the generic inspector
  - Do: From GPL-05: the generic Properties inspector doesn't show/edit `String` fields, so e.g. a follow camera's target can't be changed. Add text fields (and an object picker where the string names a GameObject) via commands.
  - Done when: String fields are editable with undo; test.
- [x] **EDT-37** `P2` Split editor string tables to stop merge conflicts
  - Do: Every round, parallel agents conflict in `assets/strings/editor_strings(.properties/_fr)`. Split them into per-area files (e.g. `editor_strings_<area>[_fr].properties`) loaded and merged by `StringManager`, with a duplicate-key check test; same for game strings if needed.
  - Done when: Strings load from several files, duplicates fail a test, and existing keys are moved.
- [x] **EDT-38** `P0` Stable ImGui IDs across editor languages
  - Do: Human-reported on PR #17: changing the editor language breaks ImGui, likely because window/widget IDs come from translated labels. Use `translated###stable_id` (or `PushID`) for every window, menu, tab, tree node, popup, table and widget whose label is translated; keep docking layout/`imgui.ini`, open state and selection across a language switch at runtime.
  - Done when: Switching language at runtime keeps docking layout, window open/collapsed state, tree expansion and popups working; a guard test fails on ImGui calls built from translated labels without a stable ID.
  - Deps: EDT-37
- [ ] **EDT-39** `P3` Review GUI-10 additions to `app/Editor.kt`
  - Do: From GUI-10: review the game string tables loading into `engine.gameStrings` and `ProjectEvent.Opened` applying the project's `game.settings.json`.
  - Done when: Review fixes committed or noted; tests green.
  - Deps: GUI-10
- [ ] **EDT-40** `P1` Review round-16 editor work by other agents
  - Do: Review the editor-side code written outside skate-editor in round 16: EDT-07 themes (gameui), EDT-08 shell panel (tooling), EDT-09 export wizard incl. `ExportWizardWindow`, `EditorMenuBar`/`WindowRegistry` additions, `ProjectEvents`, `ProjectActionHandler` (assets), EDT-14 audio inspector/mixer (audio), EDT-16 animator inspector (animation), EDT-17 material inspector (render), EDT-36 string fields/object picker (ecs). Check commands/undo, stable IDs, strings, window registration, consistency.
  - Done when: Review fixes committed with notes per task; tests green.
  - Deps: EDT-07, EDT-08, EDT-09, EDT-14, EDT-16, EDT-17, EDT-36

## Testing & tooling

### Tooling & samples — `TLG` (`skate-tooling`)

- [x] **TLG-01** `P1` Skate CLI
  - Do: Add a headless CLI (`tools/cli`, Gradle `application`) for: create project, validate project/assets, pack/export, run scene headless and print engine stats.
  - Done when: `skate --help` lists the commands, which reuse engine services (no duplicate logic). Argument parsing has unit tests.
- [x] **TLG-02** `P2` MCP server
  - Do: Expose the CLI capabilities plus editor introspection (scene tree, components, logs) over MCP so agents can drive the engine.
  - Done when: An MCP client can list scenes and entities and trigger an export.
  - Deps: TLG-01
- [x] **TLG-03** `P2` Sample project: Tetris
  - Do: A 2D/3D Tetris that exercises input, in-game UI, audio, scenes and scripting.
  - Deps: GUI-02, SCR-02
- [x] **TLG-04** `P2` Sample project: Marble Run
  - Do: Physics-heavy sample (rolling bodies, joints, triggers, camera follow).
  - Deps: PHY-03, PHY-04
- [x] **TLG-05** `P1` Sample project: Feature showcase
  - Do: One scene per engine feature (lights, shadows, materials, post-fx, particles, animation, audio, physics, UI) that serves as a manual regression suite.
  - Done when: Each scene is documented in `docs/samples.md` with what to look for.
- [x] **TLG-06** `P2` Script API for audio, scene switching, UI and material colours
  - Do: Expose these in the script API (needed by the Tetris sample) and update the sample to use them with GUI widgets.
  - Done when: Tetris runs with sound, UI score and game-over scene switch from scripts.
  - Deps: TLG-03, GUI-02
- [x] **TLG-07** `P1` Keep the default skate project current
  - Do: Update the default skate project scenes (`PrefabsGenerator`) to show current features (e.g. UI canvas/button, environment) and remove test-only content (2D `SpriteRenderer` objects, camera sprite). Document it in `docs/samples.md`. Repeat whenever features land (rule 8).
  - Done when: A new project opens a clean, working skate scene without sprites; a test checks the generated scene content.
  - Deps: GUI-05
- [x] **TLG-08** `P1` Feature showcase in the default editor project
  - Do: Human request on PR #13: new projects created in the editor include the Feature Showcase scene(s) (TLG-05, today only generated by the CLI) next to the skate `MainScene`, built from the same builder so both stay in sync, including round-11 features (shadows, particles, prefab library, UI). Rule 8: future features add their demo there.
  - Done when: A new editor project contains a working showcase scene covering current features, documented in `docs/samples.md`; a test checks the generated project contains it.
  - Deps: TLG-05, TLG-07
- [x] **TLG-09** `P0` Showcase camera and click-to-focus demo list
  - Do: Human-reported on PR #13: in Play the FeatureShowcase camera points up and can't move, so nothing is visible. Fix the camera's start pose (looking at the pads) and make the overlay's demo-area list clickable: clicking an item smoothly moves the camera to frame that area (with a 'Overview' entry to go back).
  - Done when: Play shows the scene; each list item focuses its area in the game view and in a standalone run (verified through the real path: generated project saved, reloaded from disk, Play, click through UI input); free-fly camera (WASD + mouse look, like the editor) works in Play and standalone; tests for the focus targets, camera pose math and the reloaded click path.
  - Deps: TLG-08

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
- [x] **QA-03** `P1` Unit tests for pure logic
  - Do: Prioritize math (`MathExtensions`, `SkeletonMath`, `Ray`, `Interpolator`), serialization, commands/undo, `EventSystem`, `JobSystem` and `StringManager`. Use mockk for GL/AL boundaries; no integration tests.
  - Done when: Every listed area has tests in `src/test/...` following `Area_Behavior` naming.
  - Deps: QA-02
- [x] **QA-08** `P1` Fix failing and remove obsolete unit tests
  - Do: `gradlew.bat test` currently has 20 failures (e.g. `UiMutationPipelineGuardTest`, `DeleteFileCommandTest`, `SceneTargetingCommandsTest`, `ViewportRendererScreenshotTest`, `EngineFixedTimestepTest`, `SkateboardModelInspectionTest`, `AudioSystemTest`, `EnvironmentSystemTest`, `InputSystemTest`, `RagdollSystemTest`, `StringManagerTest`). Fix each against the current API, or delete tests that cover removed or obsolete behavior (and say why in the commit).
  - Done when: `gradlew.bat test` is fully green and `docs/bugs.md` has no test-failure entries left.
  - Deps: QA-07
- [x] **QA-04** `P1` Industry-standards audit
  - Do: Check resource lifetimes (`AutoCloseable`/dispose everywhere), logging via `LoggerService` (no `println`), error handling, thread-safety of shared state, frame-allocation hotspots, units/coordinate-system conventions, and config/versioning.
  - Done when: The findings are in `docs/design/QA-04.md`, with per-domain follow-ups reported to the lead.
- [x] **QA-05** `P2` More architecture guards
  - Do: Add guard tests for the rules that other tasks introduce (no GL outside RHI, no Bullet outside its backend, no `println`, etc.).
  - Deps: QA-04
- [x] **QA-06** `P2` CI
  - Do: Add a GitHub Actions workflow on `windows-latest` that runs `gradlew.bat build test`.
  - Done when: CI is green on `main`.

---
- [x] **QA-09** `P2` StreamPolicy tests and pure-logic isolation guard for input/audio/ecs
- [x] **QA-11** `P2` Pure-logic isolation guard for net/shader/IK/text-input
  - Do: Extend the platform-free architecture guard to the networking, shader preprocessing, IK and text-input logic; keep master's suite green.
  - Done when: The guard test covers those packages and the full suite passes.
  - Deps: QA-09
- [x] **QA-12** `P2` Isolation guard for shadow and particle logic
  - Do: From REN-07/REN-10: add `engine/render/shadow/` pure maths and `engine/render/particles/ParticleSimulator.kt`, `ParticlePool.kt` to `PureLogicIsolationGuardTest`.
  - Done when: The guard covers those files and the suite is green.
  - Deps: REN-07, REN-10
- [x] **QA-13** `P1` Feature Showcase play smoke test
  - Do: The audio demo shipped silent because no test exercised the showcase the way the human does. Add an automated smoke test that generates a new project, loads `FeatureShowcase` from disk, starts Play and steps frames, then asserts each demo's observable effect: audio sources playing, particles alive, physics bodies settled (no falling through), joints moving, vehicle resting, animation advancing, UI legend buttons bound and focusing the camera. Use fakes where GL/OpenAL aren't available, but keep the scene/systems real.
  - Done when: The smoke test runs in `gradlew test`, fails if any demo is inert (verify by temporarily breaking one), and is documented for future demos (rule 8).
  - Deps: TLG-09

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
