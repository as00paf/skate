# REN-12 — RHI abstraction (Vulkan-ready): design note

Status: **approved** (§7 proposals as written); migration in progress, see §5. Owner: skate-render.
Paths are relative to `src/main/kotlin/com/pafoid/skate/` unless noted. Counts are from `master` at `c843d9fb` (PR #16).

## 1. Goal and non-goals

Goal (GameEngine.md REN-12): backend-agnostic interfaces (`RhiDevice`, `RhiBuffer`, `RhiTexture`, `RhiPipeline`,
`RhiCommandList`) with every GL call moved into an OpenGL backend under `engine/render/rhi/opengl/`, enforced by an
architecture guard. Done when no GL call exists outside `render/rhi/opengl`.

Non-goals:
- A Vulkan backend. That is REN-13 (spike); this task only makes it possible.
- A new render graph or resource aliasing.
- Changing what is drawn. Every migration step must render pixel-identical frames.
- Multithreaded command recording.

## 2. Current GL inventory

There are **517 `gl*` call sites using 87 distinct GL functions in 40 files**, plus 3 non-render GL touchpoints (§2.3).
REN-01 counted about 330 sites in 19 render files. Since then, the post chain, cascaded and local shadows, picking,
runtime UI, particles, GPU timers, UBO lighting and skinning have added roughly 190 more.

### 2.1 Inside `engine/render/` (442 sites, 31 files)

| Area | Files | Sites | Notable GL features |
|------|-------|------:|---------------------|
| Batching (2D/3D sprites) | `data/RenderBatch2D.kt`, `data/RenderBatch3D.kt` | 66 | VAO/VBO/EBO, `glVertexAttribPointer`, dynamic `glBufferSubData`, 8 texture units |
| Picking | `PickingTexture.kt`, `passes/PickingPass.kt`, `renderer/PickingRenderer.kt` | 50 | RGB32F target, `glReadPixels` + `glFinish` readback, scissor |
| Shadows (REN-07/22) | `ShadowMap.kt`, `renderer/ShadowRenderer.kt`, `passes/ShadowPass.kt` | 35 | `GL_TEXTURE_2D_ARRAY` depth with compare mode, `glFramebufferTextureLayer`, polygon offset, depth clamp |
| Debug | `renderer/DebugRenderer.kt` | 30 | dynamic line/triangle VBOs, `glLineWidth`, `gl_FragDepth` pull (REN-19) |
| UI (GUI-0x, REN-21) | `ui/UiBatchRenderer.kt`, `ui/SdfFont.kt` | 30 | dynamic VBO, scissor per batch, separate blend factors |
| Particles (REN-10) | `passes/ParticlePass.kt` | 28 | instancing (`glVertexAttribDivisor`, `glDrawArraysInstanced`), triangle strip |
| Scene targets | `FrameBuffer.kt` | 26 | RGBA8 + D32 attachments, resize |
| State cache | `utils/GLStateTracker.kt`, `utils/GLState.kt`, `state/GLBindings.kt` | 32 | blend/depth/cull/depth-func cache, VAO/texture binding cache |
| Post chain (REN-08/17) | `ColorTarget.kt`, `passes/PostProcessPass.kt` | 21 | ping-pong RGBA8 targets, `glBlitFramebuffer` |
| Mesh upload | `VAOLoader.kt` | 20 | static VAO/VBO/EBO, integer attributes (joints) |
| Thumbnails/splash/sky | `ThumbnailRenderer.kt`, `SplashRenderer.kt`, `SkyDomeRenderer.kt`, `SkyboxRenderer.kt` | 49 | own FBO, raw enable/disable in splash, cube map draw |
| UBOs | `renderer/SkinningUbo.kt`, `lighting/LightBuffer.kt` | 14 | `GL_UNIFORM_BUFFER`, `glBindBufferBase`, `glUniformBlockBinding` |
| Lifetimes | `GpuResource.kt`, `cache/GpuResources.kt` | 7 | typed deleters |
| Timing (REN-22) | `graph/GpuPassTimer.kt` | 6 | `GL_TIME_ELAPSED` query rings |
| Misc | `GeometryPass`, `ModelRenderer`, `LightingUniformsLoader`, `TextureQualitySettings`, `RenderResourcesFactory` | 23 | clears, viewport, sampler binds, anisotropy query |

Draw calls are issued from 9 places: `ModelRenderer`, `ShadowRenderer`, `PickingRenderer`, the two batches,
`UiBatchRenderer`, `DebugRenderer`, `ParticlePass`, and the sky/skybox/splash renderers. Most draws go through
`Shader.upload*(name, value)` (named uniforms, location-cached in `UniformLocationCache`).

### 2.2 Outside `engine/render/` (75 sites, 9 files)

| File | Sites | Used for | Target |
|------|------:|----------|--------|
| `engine/assets/data/Shader.kt` | 19 | program bind, named uniforms, UBO block binding, delete | `RhiShader` (backend object) and a GL-free `ShaderParams` facade |
| `engine/assets/loaders/ShaderLoader.kt` | 26 | compile, link, info log (preprocessing is already GL-free) | `RhiDevice.createShader(source)` |
| `engine/assets/loaders/TextureLoader.kt` | 8 | texture create, upload, mipmaps | `RhiDevice.createTexture` + `RhiTexture.upload` |
| `engine/assets/data/CubeMap.kt` | 10 | cube map create/bind | `RhiTexture` (cube) |
| `engine/core/Window.kt` | 4 | `GL.createCapabilities`, initial blend | `RhiDevice` creation (`OpenGlDevice.create(window)`) |
| `game/GameMain.kt` | 3 | GL bootstrap | same |
| `engine/utils/ScreenshotUtils.kt`, `engine/utils/testing/FrameCapture.kt` | 4 | `glReadPixels` | `RhiDevice.readPixels(target, rect)` |
| `editor/systems/ThumbnailCache.kt` | 1 | `glDeleteTextures` | `RhiTexture.close()` |

### 2.3 GL that is not a `gl*` call but still binds us to GL

- **ImGui.** `editor/imgui/ImGuiLayer.kt` uses imgui-java's `ImGuiImplGl3`. `ImGui.image` takes raw GL texture names
  (viewport `frameBuffer.getTextureId()`, asset browser thumbnails). A Vulkan backend would need `ImGuiImplVulkan`
  and descriptor-set handles.
- **Shaders.** They are GLSL 330 (`#type vertex/fragment` files, REN-05 includes and variants). Vulkan needs SPIR-V
  (GLSL 450 with explicit `set`/`binding` layouts).
- **Loose uniforms.** Named uniform uploads have no Vulkan equivalent; Vulkan needs UBOs and push constants.

## 3. Proposed interfaces (`engine/render/rhi/`, GL-free)

The design principles:
- Backend objects are interfaces; hot-path arguments are small value types; descriptions are data classes.
- Nothing in `rhi/` imports LWJGL GL.
- The GL backend executes commands immediately. `RhiCommandList` is a thin facade over the current context, so GL
  pays no recording cost. A Vulkan backend would record into a real command buffer.

```kotlin
interface RhiDevice : AutoCloseable {
    val caps: RhiCaps                                    // max texture size, array layers, anisotropy, timer queries
    fun createBuffer(desc: BufferDesc): RhiBuffer         // VERTEX / INDEX / UNIFORM / INSTANCE, STATIC / DYNAMIC
    fun createTexture(desc: TextureDesc): RhiTexture      // 2D, 2D array, cube; format; mips; sampler state
    fun createRenderTarget(desc: RenderTargetDesc): RhiRenderTarget
    fun createShader(desc: ShaderDesc): RhiShader         // preprocessed sources (REN-05) + defines
    fun createVertexLayout(desc: VertexLayoutDesc): RhiVertexLayout
    fun createPipeline(desc: PipelineDesc): RhiPipeline   // shader + vertex layout + fixed-function state
    fun createTimer(): RhiTimer                           // non-blocking ring, replaces GpuPassTimer's GL calls
    fun commandList(): RhiCommandList                     // GL: one shared immediate list per frame
    fun readPixels(target: RhiRenderTarget, attachment: Int, rect: IntRect, format: PixelFormat, out: ByteBuffer)
    fun beginFrame(); fun endFrame()                      // resets caches; flushes deferred deletes (§6.3)
}

interface RhiBuffer : RhiResource { val size: Long; fun update(offset: Long, data: FloatBuffer /* or ByteBuffer */) }
interface RhiTexture : RhiResource {
    val desc: TextureDesc
    fun upload(level: Int, layer: Int, data: ByteBuffer?)
    fun generateMips()
    val nativeHandle: Long   // ImGui only (GL: texture name); see §7
}
interface RhiRenderTarget : RhiResource {           // color attachments + optional depth; layered depth for shadows
    val width: Int; val height: Int
    fun resize(width: Int, height: Int)
    fun colorTexture(i: Int = 0): RhiTexture; fun depthTexture(): RhiTexture?
}
interface RhiShader : RhiResource { fun parameters(): ShaderParams }     // name -> slot table, built once
interface RhiPipeline : RhiResource { val desc: PipelineDesc }
interface RhiResource : AutoCloseable                                   // close() = deferred delete (§6.3)

/** Immutable fixed-function state. Pipelines are created once (per pass or material key) and reused every frame. */
data class PipelineDesc(
    val shader: RhiShader, val layout: RhiVertexLayout,
    val topology: Topology = Topology.TRIANGLES,          // LINES, TRIANGLE_STRIP (particles)
    val depth: DepthState = DepthState.READ_WRITE_LESS,   // test, write, func (LEQUAL sky/debug), clamp (shadows)
    val blend: BlendState = BlendState.OPAQUE,            // ALPHA_KEEP_TARGET_OPAQUE (REN-19), ADDITIVE, ...
    val cull: CullMode = CullMode.BACK, val polygonOffset: PolygonOffset? = null,
)

interface RhiCommandList {
    fun beginPass(target: RhiRenderTarget?, layer: Int = 0, clear: ClearValues? = null, viewport: IntRect? = null)  // null target = backbuffer
    fun endPass()
    fun setPipeline(p: RhiPipeline)
    fun setVertexBuffer(slot: Int, b: RhiBuffer, offset: Long = 0); fun setIndexBuffer(b: RhiBuffer)
    fun setTexture(slot: Int, t: RhiTexture); fun setUniformBuffer(binding: Int, b: RhiBuffer, offset: Long = 0, size: Long = -1)
    fun setParams(params: ShaderParams)                    // pushes changed named values (GL: glUniform*)
    fun setScissor(rect: IntRect?)
    fun draw(vertexCount: Int, first: Int = 0, instances: Int = 1)
    fun drawIndexed(indexCount: Int, firstIndex: Int = 0, instances: Int = 1)
    fun blit(src: RhiRenderTarget, dst: RhiRenderTarget, color: Boolean = true, depth: Boolean = false)
    fun beginTimer(t: RhiTimer); fun endTimer(t: RhiTimer)
}
```

**Uniforms.** `ShaderParams` keeps today's named API (`set("uFogDensity", 0.02f)`) so callers do not change
semantics. Slots are resolved once per shader into a flat value array. On `setParams`, the GL backend uploads only
values that changed since the last draw with that program, which replaces today's per-call location-hash lookups. A
Vulkan backend would map the same table to a per-draw UBO built from SPIR-V reflection. Moving the remaining loose
uniforms into std140 blocks, as `LightBuffer` and `SkinningUbo` already do, is a REN-13 prerequisite and is not part
of this task.

**Value types.** `IntRect`, `ClearValues`, the state enums and the descriptors are plain Kotlin data or value
classes: allocation-free on the hot path and testable without GL.

## 4. Mapping the existing helpers

| Today | Becomes | Notes |
|-------|---------|-------|
| `utils/GLStateTracker` (blend, separate factors, depth, mask, func, cull, `invalidateCache`) | `rhi/opengl/GlStateCache` | Applies the diff between the current and the requested `PipelineDesc` fixed-function state. `withBlendState`, `withDepthTest` and similar scope helpers disappear; passes state their pipeline instead. `invalidateCache()` stays, called from `beginFrame` and after ImGui. |
| `utils/GLState.kt` (`with*`, `bindTexture`, `bindVAO`, `setAlphaBlendKeepingTargetOpaque`) | `BlendState`/`DepthState` constants plus `RhiCommandList.setTexture` | The REN-19 opaque-target blend becomes `BlendState.ALPHA_KEEP_TARGET_OPAQUE`. |
| `state/BindingCache` + `GLBindings` (VAO and per-unit texture cache) | `rhi/opengl/GlBindingCache`, owned by the GL command list | Already GL-injected and unit-tested; it moves as is. The pass-boundary `reset()` in `RenderGraph` becomes `cmd.beginPass`. |
| `GpuResource` (id + deleter) | `RhiResource` implementations (`GlBuffer`, `GlTexture`, ...) | `close()` enqueues the deletion into the device's deferred-delete queue (frames in flight for Vulkan). |
| `cache/GpuResourceCache` / `GpuResources` (ref-counted mesh/texture cache) | Unchanged API, values become `RhiBuffer`/`RhiTexture` | Already GL-free generic code. Only the factories change. |
| `material/MaterialBinder` + `UniformSink` | Writes into `ShaderParams` and `setTexture` | Its snapshot skip stays and complements the GL backend's value diff. |
| `shader/ShaderPreprocessor`, `ShaderVariantCache` | Unchanged (already GL-free), feeding `ShaderDesc` | |
| `graph/GpuPassTimer` (`GpuQueryApi`) | `RhiTimer`; `GpuQueryApi.openGl()` moves to the backend | Already injectable and tested. |
| `FrameBuffer`, `ColorTarget`, `ShadowMap`, `PickingTexture` | `RhiRenderTarget` with `RenderTargetDesc` | Covers color formats (RGBA8, RGB32F), depth (D32), a layered depth array with compare sampler, and resize. |
| `VAOLoader`, the batches, `UiBatchRenderer`, `DebugRenderer`, the `ParticlePass` instance buffer | `RhiBuffer` + `RhiVertexLayout` (step rate per binding for instancing) | |

## 5. Incremental migration plan

Status: steps 0 to 6 are done. `RhiDevice` and `RhiCommandList` only declare what the backend implements so far;
each later step adds the methods it migrates to. Amendments made while implementing:

- **`RhiVertexInput`** (step 2): vertex and index buffers bound to a vertex layout, created by
  `RhiDevice.createVertexInput`. On GL it is a VAO; on Vulkan it is the binding list recorded at draw time. It replaces
  `setVertexBuffer`/`setIndexBuffer` in the command list (step 6 adds `setVertexInput`). Until step 6 moves the draws,
  `nativeHandle` gives draw code the VAO name.
- **`RhiContext.device`** (step 2): the device of the current context, set when the device is created. Asset loaders
  are built before the window (and so before the device), so they reach it lazily; `Engine.device` is the same object.
- **Buffer updates** (step 2): `RhiBuffer.update` takes float arrays, float buffers or byte buffers; `resize` and
  `replace` give new storage (GL: orphaning). Index buffers upload through `GL_COPY_WRITE_BUFFER`, so an upload never
  rebinds the index buffer of the VAO that happens to be bound.
- **Textures** (step 3): `createTexture` returns an empty texture with its sampler applied; `upload(level, layer,
  data)` fills a level of a 2D texture, a cube face or an array layer, and `setSampler` changes filtering later
  (texture quality). Formats are sized on GL (`RGBA8` -> `GL_RGBA8`; the loaders used unsized `GL_RGBA`, which renders
  the same). Asset `Texture.texId` stays the native handle for draw code and ImGui until step 6. The SDF font atlas
  asked for legacy `GL_CLAMP`; it is now `CLAMP_TO_EDGE`, which Vulkan can express.
- **Render targets** (step 4): `RenderTargetDesc` gained `layered` (2D-array attachments, so a one-layer shadow map
  is still a `sampler2DArrayShadow`) and per-attachment samplers (`colorSampler`, `depthSampler`). `beginPass` takes
  the layer to render, the clear values (applied like a Vulkan load op: whole attachment, whatever the depth-write
  and scissor state) and an optional viewport; a back-buffer pass must give its viewport. `endPass` returns to the
  default framebuffer, so nothing leaks between passes. `blit(src, dst, srcRect, dstRect, filter)` copies color only
  (post-processing result, the exported game's stretched present); `dst = null` is the back buffer.
  `readPixels(target, attachment, rect, format, out)` lives on the device and is synchronous (picking, thumbnails,
  screenshots, `FrameCapture`). The scene depth is `DEPTH32F` (it was 32-bit fixed point); frames are unchanged.
- **Shaders** (step 5): `createShader(ShaderDesc)` throws `ShaderCompileError(stage, log)`; `ShaderLoader` turns
  it into its usual log line and `ShaderCompileException`. `ShaderParams` gained vec2 and int-array setters, and
  `RhiShader.bindUniformBlock` links std140 blocks (GL 3.3 has no `binding` qualifier). On GL a parameter upload
  happens at once and is skipped when the program already holds that exact value (bit compare, `UniformCache`);
  the backend tracks the current program (`GlPrograms`, invalidated with the state cache each frame) so an upload
  always reaches its own program. `cmd.setParams(params)` makes the shader current; `Shader.stop()` no longer binds
  program 0. The `Shader` asset is GL-free, so `engine/assets/` has no GL imports.
- **Pipelines and draws** (step 6): `PipelineDesc` is a shader, a vertex layout and a `PipelineState` (topology,
  depth, blend, cull, polygon offset, line width). `state/PipelineStates` names every kind of draw, each spelling out
  what the old `with*` scopes left in effect; `state/PipelineCache` keeps one pipeline per shader and layout and
  rebuilds a shader's pipelines after a hot reload. The command list gained `setPipeline`, `setVertexInput`,
  `setTexture` (2D, 2D array or cube by the texture's type), `setScissor`, `draw` and `drawIndexed`.
  `RhiVertexInput.nativeHandle` is gone; `MeshPart.input` carries the vertex input and `MeshPart.vaoId` is now
  `VAOLoader`'s mesh id. `SamplerDesc.wrapV` lets the sky texture repeat around and clamp at the poles. The GL
  backend's `GlStateCache` (formerly `GLStateTracker`) diffs pipeline state from "unknown" after every
  `beginFrame`, so nothing inherited from ImGui or an earlier draw leaks in. `EngineStats.drawCalls` now counts
  every draw (sprite batches, debug lines and the sky were not counted), and `stateChanges` counts binds that used
  to be raw GL (shadow draws, post-processing, batches).

Each step is one PR-sized commit. Each must keep the full test suite green, produce pixel-identical offscreen frames
(REN-18/19/20 harness: default `MainScene` and `FeatureShowcase`, created, saved and reloaded, edit mode and Play),
and keep the REN-22 timings within budget (§6). Domain agents keep working: renderers change internally, but public
engine APIs stay source-compatible until the step that moves them.

0. **Ratchet guard** (test only). `RhiGlIsolationGuardTest` lists every file importing `org.lwjgl.opengl` or calling
   `gl[A-Z]` today (40 files plus `ImGuiLayer`) as an allowlist. It fails when a file is added and when an allowed
   file stops using GL without being removed from the list, so the list can only shrink. This stops new GL leaking
   out while the migration runs.
1. **Skeleton and move.** Add the `engine/render/rhi/` interfaces and value types. Move `GLStateTracker`, `GLBindings`,
   `BindingCache`, `GpuResource` and the `GpuQueryApi` GL implementation into `rhi/opengl/`. Add an `OpenGlDevice`
   that owns them, created by `Window`/`GameMain` in place of `GL.createCapabilities`, and exposed by `Engine`. No
   behavior change; existing callers go through thin forwarding functions.
2. **Buffers and vertex layouts.** Migrate `VAOLoader`, `RenderBatch2D/3D`, `UiBatchRenderer`, `DebugRenderer`, the
   particle instance buffer, `LightBuffer` and `SkinningUbo`. Add unit tests for the layout descriptors (this
   replaces `BatchShaderLayoutTest`'s constant check with descriptor-to-shader checks).
3. **Textures and samplers.** Migrate `TextureLoader`, `CubeMap`, `SdfFont`, `TextureQualitySettings` and
   `ThumbnailCache`. `RhiTexture.nativeHandle` serves ImGui.
4. **Render targets.** Migrate `FrameBuffer`, `ColorTarget`, `ShadowMap` and `PickingTexture`, plus readback and
   blit, `ScreenshotUtils` and `FrameCapture`. `cmd.beginPass` replaces the raw FBO binds, viewports and clears in
   every pass.
5. **Shaders and parameters.** `ShaderLoader` builds `RhiShader` through the device. `Shader` becomes a GL-free asset
   holding an `RhiShader` plus `ShaderParams`, so `assets/` loses its GL imports.
6. **Pipelines and draws.** Each pass and renderer creates its `RhiPipeline`s once and submits through
   `RhiCommandList` (`setPipeline`, `drawIndexed`, `draw(instances = n)`). The `with*` scopes and remaining raw
   `glEnable`/`glDisable` calls go away (splash, shadows, picking scissor).
7. **Timers and cleanup.** `RenderGraph` uses `RhiTimer`. Remove the forwarding shims. Flip the guard to strict:
   no GL outside `render/rhi/opengl/`, with a single named exception for `editor/imgui/ImGuiLayer.kt` (§7).

Steps 2 to 4 are independent and could be split by area (UI, particles, shadows) if a smaller PR is wanted.

## 6. Performance

- **Calls.** Interface dispatch is cheap next to the GL driver calls it wraps. A frame issues on the order of a
  thousand calls; a megamorphic call costs nanoseconds, so the overhead is well under 0.1 ms.
- **No hot-path allocation.** Pipelines and `ShaderParams` slot tables are built once. Commands take value types and
  existing objects.
- **Fewer redundant calls.** Pipelines move state diffing from per-scope enter/restore pairs (today, per mesh part)
  to a single diff per `setPipeline`, and `ShaderParams` value diffing removes repeated uniform uploads. Both should
  reduce `EngineStats.stateChanges`. Report before/after counts per step.
- **Budget.** At most 3% regression in the REN-22 offscreen frame time (default project, 1920x1080, MEDIUM shadows,
  idle, hover and orbit modes) and in the Render Graph per-pass CPU/GPU times. Measure at each step.
- **Deferred deletion.** `close()` enqueues and `endFrame()` flushes, one frame late on GL. This is required for
  Vulkan frames-in-flight and harmless on GL.

## 7. Risks and decisions needing approval

1. **Scope and churn.** About 440 render-internal call sites plus `assets/`, `core/`, `game/` and `editor/`. This is
   mitigated by the ratchet guard and step-sized PRs, but other agents touching the same files will see conflicts.
   Proposal: one render-owned PR per step; `assets/` (step 5) and `core/`/`game/` bootstrap (step 1) are coordinated
   with skate-assets and the lead.
2. **ImGui exception.** imgui-java's GL3 backend has to run on the GL context. Proposal: `ImGuiLayer` stays the one
   allowed GL user outside the backend (it only calls `ImGuiImplGl3`, with no `gl*` calls of ours), and textures
   reach ImGui through `RhiTexture.nativeHandle`. A Vulkan backend would swap `ImGuiImplGl3` for `ImGuiImplVulkan`
   in REN-13 or later. Alternative: move the ImGui backend glue into `rhi/opengl/` behind an `RhiImGuiBackend`
   interface (more code, cleaner guard).
3. **Uniform model.** Keeping named uniforms (§3) is the least risky route. Its Vulkan cost is a per-draw UBO built by
   reflection. Converting all shaders to std140 blocks now would double this task.
4. **Vulkan semantics we encode now, so REN-13 does not need a redesign.** Explicit pass begin/end with clear values
   (load/store ops), depth range [0,1] versus GL's [-1,1], Y-flip of clip space, sampler state on the texture or a
   separate sampler object, and pipeline objects that bake fixed-function state. Our `gl_FragDepth` tricks (REN-19
   debug pull) and texel snapping in shadows depend on the depth range. The backend has to own a clip-space fix-up
   matrix.
5. **Picking readback.** Picking is a synchronous `glReadPixels` + `glFinish` (a pipeline stall noted in REN-22).
   `RhiDevice.readPixels` keeps today's semantics; an async readback (PBO or fence) is a follow-up, not in scope.
6. **Threading.** GL calls stay main-thread only, as today. The RHI does not add thread safety.
7. **Tests.** GL backends cannot run in unit tests (no context). Coverage comes from GL-free descriptors, state-diff
   logic (`GlStateCache` with injected GL functions, like `BindingCache` today) and the guard. The offscreen GPU
   harness stays a manual verification tool, as in REN-18 to REN-22.

## 8. Test plan

- `RhiGlIsolationGuardTest` (step 0 ratchet, step 7 strict).
- Pipeline state diff (`GlStateCache_*`): only changed state is applied; separate blend factors; `invalidateCache`
  re-reads state. GL functions are injected.
- `ShaderParams_*`: the slot table is built once; unchanged values are not re-uploaded; unknown names are ignored, as
  the current uniform cache does.
- Descriptor tests: vertex layouts match the shaders' `layout(location)` declarations (supersedes
  `BatchShaderLayoutTest`); render-target formats for scene, picking and shadow-array targets.
- Deferred deletion: `close()` twice deletes once, and deletion happens at `endFrame()`.
- Per-step pixel-identical offscreen frames and REN-22 timings, recorded in each step's commit message.
