# REN-13 — Vulkan backend spike: report

Status: **spike done**. Owner: skate-render. Builds on REN-12 (`docs/design/REN-12.md`, the RHI), §7.4 decisions.
Paths are relative to `src/main/kotlin/com/pafoid/skate/` unless noted.

## 1. Result in one paragraph

An offscreen `VulkanDevice` (`engine/render/rhi/vulkan/`, about 2,000 lines) implements the REN-12 interfaces well
enough to run the engine's real, unmodified `GeometryPass` on the default MainScene: textured meshes, PBR material
parameters, the directional light, the light buffer, the skinned skater and the sky dome. It uses the engine's own
GLSL shaders, rewritten for Vulkan at load time and compiled with shaderc. The frame matches OpenGL almost exactly
(1280x720, same camera, both read back through `RhiDevice.readPixels`):

| Metric | Vulkan vs OpenGL |
|---|---|
| Mean absolute difference per channel | 0.003 / 255 |
| Pixels within 2/255 on every channel | 100.00% |
| Largest channel difference | 1 / 255 |

The remaining 1-LSB differences are rounding inside the drivers (blending/filtering), on scattered pixels. The REN-12
interfaces needed no change: the abstraction held. The recommendation (§6) is to keep the spike as an
abstraction check and not build the full backend now.

## 2. How to run it (opt-in)

The Vulkan backend is never used unless asked for: `-Dskate.rhi=vulkan` or the `SKATE_RHI=vulkan` environment
variable (`RhiBackend.selected()`), and only the comparison test and tools honour it. The editor and the exported game
always create an `OpenGlDevice`; OpenGL is unchanged.

```
set SKATE_RHI=vulkan
gradlew.bat :test --tests com.pafoid.skate.engine.render.rhi.vulkan.VulkanGeometryPassGpuTest
```

The test (`@GpuTest`, so CI skips it) renders the geometry pass with both backends in one JVM and writes
`build/ren13/{gl,vulkan,diff}.png` and `build/ren13/report.txt`. It fails if the Vulkan frame is flat or if fewer than
90% of pixels are within 8/255 of OpenGL. `SKATE_VK_VALIDATION=1` enables the Khronos validation layer when it is
installed (it was not on the test machine: Intel Arc Pro B390, driver 101.8976, Vulkan 1.4).

## 3. Dependencies

Declared in `gradle/libs.versions.toml` under the existing LWJGL BOM (3.3.6):

| Library | License | What for | Size |
|---|---|---|---|
| `org.lwjgl:lwjgl-vulkan` | BSD-3-Clause | Vulkan bindings; no native jar on Windows (loads the system `vulkan-1.dll`) | 7.3 MB jar |
| `org.lwjgl:lwjgl-shaderc` + `natives-windows` | BSD-3-Clause (binding), Apache-2.0 (shaderc, glslang, SPIRV-Tools) | GLSL → SPIR-V at runtime | 1.8 MB native |

Shaders are compiled **at runtime with shaderc**, not offline. That keeps one set of GLSL sources for both backends,
keeps shader hot reload (REN-05) working, and needs no build step or SDK. The cost is 1.8 MB of natives and a few
milliseconds per shader on first use. Offline SPIR-V would make sense for a shipping Vulkan build.

## 4. How it works

| Piece | File | Notes |
|---|---|---|
| GLSL 330 → Vulkan GLSL 450 | `GlslToVulkan.kt` | Loose uniforms of both stages → one std140 `Params` block (set 0, binding 0), declared identically in both stages; `#define`s hoisted so array sizes resolve; std140 blocks and samplers get shared bindings; varyings get locations matched by name (matrices take one per column); `gl_VertexID`/`gl_InstanceID` renamed. |
| Depth range fix-up | `GlslToVulkan.kt`, `ClipSpace` | The vertex stage's `main` is wrapped and ends with `gl_Position.z = (z + w) / 2`: GL's [-1, 1] depth maps to Vulkan's [0, 1], so GL projection matrices, clears and depth tests stay as they are. |
| Y flip | `ClipSpace`, `VkCommands.kt` | Negative viewport height (core in Vulkan 1.1): images land in memory top row first. Front faces stay counter-clockwise. `readPixels` returns rows bottom-first, the RHI's GL order, so readback is backend-neutral. Scissors convert from bottom-left to top-left. |
| SPIR-V reflection | `SpirvReflection.kt` | Own parser (no SPIRV-Cross): block member offsets, array and matrix strides, block sizes, sampler bindings with dimension, array count and depth-compare flag, vertex inputs. |
| Named parameters | `ParamsBlock.kt`, `VkShaderParams` | `ShaderParams.set("uName"/"uName[i]")` writes the reflected offset in the member's scalar type. A sampler's value is its texture unit, like GL (`SamplerUnits`). |
| Descriptors | `VkCommands.kt` | One descriptor set per draw. `Params` and every std140 block are copied into a per-pass uniform ring, which gives GL's "value at draw time" semantics. Samplers read the texture bound to their unit; an unusable unit gets a dummy (opaque black, or far depth for shadow samplers). |
| Missing vertex inputs | `VkPipelineBuilder` | Shader inputs the mesh lacks (tangents, colours, joints) read a stride-0 dummy buffer: (0,0,0,1), GL's disabled-attribute value. |
| Pipelines | `VkPipelineBuilder` | Built from `PipelineState` on first draw per render pass, because formats come from the pass. Viewport and scissor are dynamic. |
| Passes | `VkCommandList` | `beginPass` maps clear values to load ops. Attachments rest in their sampled layouts (`SHADER_READ_ONLY`, `DEPTH_STENCIL_READ_ONLY`) between passes. `endPass` submits and waits. |
| Resources | `VkResources.kt`, `VkCore.kt` | One allocation per buffer or image. Vertex and index buffers live in mapped host-visible memory. Textures upload through staging, with mips generated by blits. RGB8 and RGB32F are widened to four channels. |

Unit-tested without a GPU: the GLSL rewrite, clip-space and viewport math, SPIR-V reflection (hand-assembled module),
the `Params` writer, sampler units, the state and format translation, and backend selection. One more test compiles
**every engine shader** (16 files plus the shadow `ALPHA_TEST` variant) through the rewrite, shaderc and the reflection,
and checks every `Params` member is reflected. It needs the shaderc native, not a GPU, so it runs on CI.
`RhiGlIsolationGuardTest` keeps Vulkan and shaderc inside `rhi/vulkan/`.

Found and fixed on the way: `MaterialBinder` treated a material texture as present only when `Texture.texId > 0`.
That held for GL texture names, but a Vulkan image handle truncated to an `Int` is negative about half the time, so
materials randomly lost their textures. Presence now comes from the texture's `RhiTexture` (`MaterialSystemTest`
covers it). Other code that still reads `texId` is editor ImGui, which is OpenGL-only.

## 5. What is missing for a full backend

| Gap | Why it matters | Estimate |
|---|---|---|
| Swapchain, presentation, window integration | The editor and game need a surface, a swapchain, resize and vsync; `Window` creates only GL today. | 3-4 days |
| Frames in flight | The spike waits for the GPU after every pass. A real backend needs per-frame fences, command buffers and uniform rings, deferred deletion (REN-12 §6) and versioned dynamic vertex buffers: sprite batches, debug lines, particles and UI rewrite buffers between draws in one pass. | 4-5 days |
| ImGui on Vulkan | imgui-java ships no `ImGuiImplVulkan`. Either write an ImGui renderer on the RHI (portable, also removes the last GL exception) or bind a Vulkan backend natively. | 4-6 days |
| Descriptor management for named uniforms | One set per draw plus a full `Params` copy works but scales poorly: use dynamic-offset UBOs, cache sets by bound resources, or move hot uniforms to push constants. | 3 days |
| Memory allocator | One `vkAllocateMemory` per resource hits `maxMemoryAllocationCount` in big scenes; use VMA (`lwjgl-vma`) or a simple suballocator. | 2 days |
| Shadows | Layered depth targets, per-layer framebuffers, depth clamp and depth bias are implemented but untested; the shadow pass was disabled in the comparison. | 2 days |
| Post-processing | Render targets sampled as textures are stored top row first because of the Y flip, so GL-convention UVs read them upside down. A convention is needed: flip UVs when sampling targets, or flip with the projection instead of the viewport. Also `blit`. | 3-4 days |
| Remaining passes | Picking readback through RGBA32F, debug lines (wide lines are optional in Vulkan), UI scissors, particles, thumbnails. | 2-3 days |
| GPU timers | Timestamp queries behind `RhiTimer`. | 1 day |
| Hardening | Validation-clean runs (the layer was not installed here), pipeline cache on disk, AMD/NVIDIA/Linux testing, a CI job on a GPU runner. | 4-5 days |

**Total: about 6 to 8 weeks** for one engineer to reach parity with the OpenGL backend (editor included), plus
ongoing double maintenance of every rendering feature.

## 6. Recommendation

**Do not build the full Vulkan backend now.** The spike answered the questions REN-12 left open:

- The RHI is Vulkan-shaped enough. Explicit passes with load ops, immutable pipelines, named parameters reflected into
  blocks, and readback were all implemented without changing an interface or a renderer.
- The engine's shaders port mechanically: one GLSL source set, no hand-written Vulkan variants.
- Clip-space and Y conventions are owned by the backend, as §7.4 decided.

What Vulkan would buy (multithreaded command recording, lower driver overhead, consoles or a future Metal path via
MoltenVK) does not address a current bottleneck. REN-22 frames take 3-4 ms at 1080p on an integrated GPU, and the
engine is single-threaded by design (§7.6). Six to eight weeks is a large share of the remaining roadmap.

Keep the spike in the tree as an opt-in backend and an abstraction check. Its unit tests (the shader rewrite of every
engine shader in particular) fail fast when a renderer or shader change would not port. Revisit when there is a
concrete need: a platform without OpenGL (macOS deprecation, consoles), measured CPU-bound draw submission, or
multithreaded rendering. The work would then start with frames in flight and presentation (§5, first two rows).
