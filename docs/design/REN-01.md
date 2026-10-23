# REN-01 — Renderer audit and target architecture

Status: proposed (audit task, no code changes). Owner: skate-render.
Paths are relative to `src/main/kotlin/com/pafoid/skate/` unless noted.

## 1. Current state

### 1.1 Flow

```
Renderer.render(scene)                       engine/render/renderer/Renderer.kt
  EngineStats.resetDrawCalls()
  RenderGraph.execute(scene)                 engine/render/graph/RenderGraph.kt
    prepare()  on enabled passes
    executeWithTiming(scene) on enabled passes, in insertion order
    cleanup()  on ALL passes
```

Pass order is fixed in `RenderResourcesFactory` (`RenderGraphBuilder.addPass`):
`ShadowPass → PickingPass → GeometryPass → DebugPass`.

| Pass | Work | GL surface |
|------|------|------------|
| `ShadowPass` | binds `ShadowMap` FBO, clears, `ShadowRenderer.render` for all visible root children (`scene.children`, not recursive) | `ShadowRenderer` calls `glEnable/glDepthMask` directly |
| `PickingPass` | renders IDs to `PickingTexture` (3D via `PickingRenderer`, 2D via batch) | direct `glDisable(SCISSOR)`, FBO binds |
| `GeometryPass` | binds FBO (or 0), clear, uploads camera + lighting uniforms, loops `scene.getAllChildren()` calling `ModelRenderer`, collects `Renderable2D`, 2D batch, sky dome | direct `glViewport/glClear/glClearColor/glEnable`, cleanup resets program/VAO/VBO/texture |
| `DebugPass` | grid, axes, light/camera gizmos, collider lines through `DebugRenderer` | `DebugRenderer` owns VAO/VBO and uploads per frame |

`RenderPass` already declares `inputs`/`outputs` (`"ShadowMap"`), but the graph ignores them: ordering is manual and no resource lifetimes exist.

`RenderResources` is a data-class "god container" (shaders, FBO, picking texture, renderers, passes, graph, shadow map). `Renderer.destroy()` and `resize()` hand-list every resource to dispose, so every new resource needs edits in three places (factory, container, destroy).

### 1.2 Direct GL calls

Inside `engine/render/` (allowed by domain rule, but scattered): ~330 `gl*` call sites in 19 files. Heaviest: `RenderBatch2D`/`RenderBatch3D` (33 each), `PickingTexture` (32), `DebugRenderer` (28), `FrameBuffer` (26), `ShadowMap` (22), `VAOLoader` (20), `GLStateTracker` (19). Draw calls (`glDrawElements`/`glDrawArrays`) are issued from renderers (`ModelRenderer`, `SkyboxRenderer`, `SkyDomeRenderer`, `DebugRenderer`, batches), not from a single submission point.

**Outside `render/` (violates the domain rule "all GL inside `engine/render/`"):**

| File | Calls | What | Target owner |
|------|-------|------|--------------|
| `engine/assets/data/Shader.kt` | 17 | program use, uniform upload, uniform location cache, delete | RHI `ShaderProgram` |
| `engine/assets/loaders/ShaderLoader.kt` | 20 | create/compile/link, error query | RHI + shader preprocessor (REN-05) |
| `engine/assets/loaders/TextureLoader.kt` | 8 | `glGenTextures`, params, `glTexImage2D`, mipmaps | RHI `Texture` via resource cache (REN-02) |
| `engine/assets/data/CubeMap.kt` | 10 | cube map creation/bind | RHI `Texture` (cube) |
| `engine/core/Window.kt` | 4 | `GL.createCapabilities`, initial blend state | `RenderDevice.init()` |
| `engine/utils/ScreenshotUtils.kt` | 3 | `glReadPixels` | RHI readback |
| `engine/utils/testing/FrameCapture.kt` | 1 | `glReadPixels` | RHI readback |
| `editor/systems/ThumbnailCache.kt` | 1 | GL texture handling | `ThumbnailRenderer` API |
| `game/GameMain.kt` | 3 | GL bootstrap | `RenderDevice.init()` |

Asset data classes (`Shader`, `Texture`, `CubeMap`) hold raw GL ids and own their GPU lifetime, so assets and renderer are coupled; there is no cache keyed by asset, so uploads are not deduplicated.

### 1.3 Redundant state changes

- `GLStateTracker` exists and `withBlendState/withDepthMask/withCullFace/withDepthTest` use it, but it is bypassed by raw calls: `GeometryPass.clearColor` (`glEnable(DEPTH_TEST)` every frame), `ShadowRenderer` (`glEnable(DEPTH_TEST)`, `glDepthMask(true)`), `PickingPass` (`glDisable(SCISSOR_TEST)`), `Window` (initial blend). Tracker caches go stale when raw calls change state.
- `withBlendState(x){withDepthMask(y){withCullFace(z){draw}}}` is applied **per mesh part**, with enter/restore pairs, even for consecutive opaque parts with identical state.
- `ModelRenderer.renderMeshPart` per part: re-uploads every sampler (`uploadInt(..., slot)`), every `Has*` boolean and all material uniforms, even when the same material/shader was just used; rebinds textures without a bound-texture cache; binds/unbinds the VAO around each part (`bindVAO/unbindVAO` enable/disable attribute arrays each time).
- `defaultShader.start()/stop()` once per pass, but `Shader.start()` has no "already current program" check; `cleanup()` ends with `glUseProgram(0)`, `glBindVertexArray(0)`, `glBindBuffer(0)`, `glBindTexture(0)` unconditionally.
- Uniform locations are cached per shader in a `mutableMapOf<String, Int>` (hash lookup + string concat per call; light arrays build keys like `"${POINT_LIGHTS}[$i].color"` every frame). Uniform *values* are not cached, so unchanged values are re-uploaded each draw.
- No sorting: draw order is scene-graph order, so shader/material/VAO switches are arbitrary; alpha-blended parts are not sorted back-to-front.
- No frustum culling in any pass; `ShadowPass` iterates only `scene.children` (non-recursive, differs from `GeometryPass`'s `getAllChildren()` → shadow casters nested in hierarchies are missed).
- Draw calls are the only `EngineStats` counter; state changes, program switches, texture binds, uploads are not measured.

### 1.4 Per-frame allocations

- `RenderGraph.execute`: `passes.filter{}` twice (2 lists/frame); `getAllPasses()` copies on each call (used by UI).
- `GeometryPass.execute`: new `mutableListOf<Renderable2D>()`, one `Renderable2D` per sprite, `getTranslation(Vector3f())` for camera position, `scene.getAllComponents<...>().filter{}.mapNotNull{}` twice (point/spot lights), `scene.getAllChildren()` list, `getComponent<T>()` lookups per object (several per object per pass).
- `LightingUniformsLoader`: `Vector3f(color).mul(intensity)` per light per frame, `Matrix4f()`/`Vector3f()` defaults, string-key construction for every light array element.
- `ShadowPass`: `scene.children.filter{ it.isVisible }` per frame.
- `DebugRenderer`: `Vector3f` copies per line/triangle (`Line3D(Vector3f(from), ...)`, capsule/box helpers allocate dozens of vectors), `DebugPass` per-frame `getTranslation(Vector3f())`.
- `PickingTexture.readPixel`: `FloatArray(3)` per read (on demand, minor).
- `Shader.upload*`: uses a shared static buffer (documented in `Shader.kt`), so not allocating, but not thread-safe.
- `ModelRenderer.visualizeBoneRecursive`: `Vector3f()` per bone when skeleton display is on.

### 1.5 Other pain points

1. **No RHI**: GL types and ids leak everywhere (`part.vaoId: Int`, `texId: Int`, `GL11.glDrawElements` inside renderers). Vulkan (REN-12/13) impossible without a seam.
2. **Passes own scene traversal**: each pass re-walks the ECS and decides what to draw; no shared "render world"/draw-list extracted once per frame.
3. **Passes are constructor-wired with concrete shaders/renderers** (`GeometryPass` takes 10 ctor params, incl. `useFbo`, `shadowMapTextureId`, `shadowMapResolution` fixed at construction → cannot change without rebuilding the graph).
4. **Render graph is just an ordered list**: `inputs/outputs` unused, no resource aliasing, no per-pass render target declaration, no automatic barrier/target binding.
5. **Material handling lives in `ModelRenderer`** (hard-coded uniform names and 6 texture slots) → blocks REN-04.
6. **Shaders are static single files** with fixed feature toggles via runtime booleans (`u_Has*`) → blocks variants (REN-05).
7. **Lifetimes**: `Renderer.destroy()` manually destroys a subset (skips `splash`, `texture3D`, `model`, `debug`, `thumbnail` renderers and the 2D batch).
8. Existing tests (`RenderPipelineTest`, `graph/`, `CameraTest`) cover only graph ordering/camera; there is no test for state-tracking or sorting.

## 2. Target architecture

```
┌──────────────────────────────────────────────────────────────┐
│ 4. High-level API   (engine/render/api)                      │
│    Renderer, RenderWorld/FrameData extraction, DebugDraw,    │
│    ThumbnailRenderer, TextureRenderer2D/3D, screenshot       │
├──────────────────────────────────────────────────────────────┤
│ 3. Passes           (engine/render/passes)                   │
│    Shadow, Depth/Picking, Opaque, Transparent, Sky, Sprites, │
│    PostProcess, Debug — record DrawItems, no raw GL         │
├──────────────────────────────────────────────────────────────┤
│ 2. Render graph     (engine/render/graph)                    │
│    Declared resources (transient targets), pass deps,        │
│    topological order, culling of unused passes, timings      │
│    + Render state/sort layer: DrawItem, sort keys, batching  │
├──────────────────────────────────────────────────────────────┤
│ 1. RHI              (engine/render/rhi, rhi/opengl)          │
│    RenderDevice, CommandEncoder, Buffer, VertexLayout/Mesh,  │
│    Texture, RenderTarget, ShaderProgram, PipelineState       │
│    + GPU resource cache (handles, ref-counting, dispose)     │
└──────────────────────────────────────────────────────────────┘
```

Dependency rule: each layer depends only on layers below. Only `rhi/opengl/**` may import `org.lwjgl.opengl`. Windowing (`GLFW`) stays in `engine/core` but context/capability creation moves behind `RenderDevice`. An architecture guard test (QA domain request) will enforce this once the migration finishes.

### 2.1 RHI (layer 1)
- Opaque, typed handles (`BufferHandle`, `TextureHandle`, `ShaderHandle`, `RenderTargetHandle`) instead of raw `Int` ids; assets keep CPU data + handle, not GL ids.
- `PipelineState` (immutable value: blend, depth test/write/func, cull, polygon mode, color mask, scissor) replaces ad-hoc `with*State` blocks; the GL backend diffs against the last applied state (promotes `GLStateTracker` into the backend and makes it the *only* path to GL).
- Command-style API (`bindPipeline`, `bindVertexInput`, `bindTexture`, `setUniforms`/uniform blocks, `draw`, `drawIndexed`, `beginTarget`, `endTarget`, `readPixels`). First backend executes immediately; the shape allows deferred command lists for Vulkan.
- Uniforms: precomputed `UniformSlot` ids resolved once per shader (no per-call string hashing), value caching, and UBOs for camera/lights/skin (std140) as the longer-term path.
- Counters (draw calls, program/texture/VAO/pipeline switches, uploads, bytes) reported to `EngineStats` from the backend.

### 2.2 Render graph and state/sort layer (layer 2)
- Passes declare `reads`/`writes` of named graph resources (replace string `inputs/outputs`); graph compiles a topological order, creates/reuses transient render targets (resize-aware), and skips passes whose outputs are unused (e.g. picking when no request).
- `DrawItem(pipeline, shader, mesh, material, transform index, sortKey)`; `RenderQueue` builds 64-bit sort keys (pass | pipeline | shader | material | depth), sorts opaque front-to-back/by state and transparent back-to-front, and merges consecutive identical state. Pure logic → unit-testable without GL.
- Per-frame `FrameData` extracted once from `Scene` (camera, lights, visible renderables, sprites, shadow casters, frustum-culled) in preallocated, reused arrays. Passes read `FrameData`; they never walk the ECS.

### 2.3 Passes (layer 3)
Small, single-purpose, constructed from `RenderContext` (device + cache + frame data) instead of 10 constructor args. Mapping from today:
`ShadowPass`→ShadowPass (cascade-ready), `PickingPass`→IdPass, `GeometryPass`→Opaque + Transparent + Sprites + Sky, `DebugPass`→DebugPass; new: PostProcess stack (REN-08), Particles (REN-10). Material binding moves out of `ModelRenderer` into a `MaterialBinder` fed by the material system (REN-04), shader permutations via REN-05.

### 2.4 High-level API (layer 4)
What the rest of the engine/editor sees: `Renderer.render(scene)`, `resize`, `readPixel`/picking request, `DebugDraw` (line/box/sphere API, pooled), `Thumbnail`/offscreen texture rendering, screenshots, `EngineStats`. No GL ids or passes exposed; editor panels read pass timing through a `RenderGraphInspector` view instead of `RenderPass` internals. `RenderResources` shrinks to a `RenderContext` owned by `Renderer` with a single `dispose()` that tears down everything via the resource cache.

## 3. Migration plan (ordered, each step keeps the build green)

0. **Guard rails (REN-01 follow-up, small)**: add a pure-Kotlin test list of GL-touching files outside `render/` (allowlist that shrinks each step). Request from QA: promote to an architecture guard.
1. **REN-02 GPU resource cache**: handles + ref-counted cache for shaders, meshes (VAO/VBO), textures, uniform locations. Move `TextureLoader`/`CubeMap`/`ShaderLoader`/`Shader` GL code into `engine/render/` behind the cache (assets keep CPU data + key). Removes ~58 GL calls from `engine/assets`. Add `Renderer.dispose()` freeing every cached resource.
2. **State layer cleanup (part of REN-03)**: make `GLStateTracker` the only state path: replace the raw calls in `GeometryPass`, `ShadowRenderer`, `PickingPass`, `Window` (init via `RenderDevice.init()`); cache current program/VAO/texture binds; extend `EngineStats` with state-change counters; remove unconditional cleanup resets.
3. **REN-03 draw items + sort**: introduce `FrameData` extraction (reused buffers; fixes `ShadowPass` non-recursive traversal and per-frame list allocations), `DrawItem`, sort keys, frustum culling, opaque/transparent ordering; `ModelRenderer` submits items instead of drawing. Unit tests for key ordering and batching.
4. **Pass decomposition**: split `GeometryPass` into Opaque/Transparent/Sprites/Sky; graph gains declared resources and topological ordering; `RenderGraph.execute` becomes allocation-free; replace `RenderResources` data class with `RenderContext`.
5. **REN-04 material system (REWRITE, needs design note)**: `MaterialBinder` replaces the hard-coded block in `ModelRenderer`; uniform value caching; UBO for material params.
6. **REN-05 shader variants**: preprocessor/includes, permutation keys in the cache; replaces `u_Has*` runtime booleans.
7. **REN-06/07/09/11**: lighting UBO (removes per-frame string keys and vector allocations in `LightingUniformsLoader`), shadows, fog, texture rendering on top of the graph.
8. **REN-08 / REN-10**: post-process stack and particles as ordinary passes in the graph.
9. **REN-12 RHI abstraction**: extract interfaces from steps 1–4 (`RenderDevice`, `CommandEncoder`, handles); move all GL into `rhi/opengl`; enable the architecture guard "no `org.lwjgl.opengl` outside `rhi/opengl`".
10. **REN-13 Vulkan spike**: second backend behind `RenderDevice`, triangle + one pass, to validate the seam.

### Risks / assumptions
- Windows-only natives, so no GL context in tests: all new logic (sort keys, cache bookkeeping, state diffing, graph ordering) is written GL-free; GL boundaries mocked with mockk.
- Steps 1–2 touch `engine/assets/**`, which belongs to the Assets domain. Proposed split: render owns the new cache API; assets keep only CPU-side loading and hand over pixel/shader source. Needs lead coordination (see NEEDS in report).
- `DebugRenderer` pooling and `Vector3f` allocation fixes are folded into step 3/4, not separate tasks.
