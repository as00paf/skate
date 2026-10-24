# REN-04 — Material system (design note)

Status: **awaiting lead approval**. No rewrite code has been written. Builds on REN-01 (layering) and REN-02/03 (resource cache, sort keys).

## 1. Current state

- `assets/data/models/Material` is a fixed `@Serializable` data class: 5 `Texture?` fields, `baseColor`, 4 scalar factors, `doubleSided`, `alphaMode`, `alphaCutoff`. It is stored inline in each `MeshPart` and in `RenderComponent.material` (override).
- `ModelRenderer.renderMeshPart` hard-codes every uniform name (`Uniforms.BASE_COLOR`, `u_Has*`, ...) and 5 texture slots (`TextureSlots`). Every draw re-uploads all values, including the sampler indices and `Has*` flags. `renderMeshPartSimple` repeats a subset.
- Materials are not assets: they exist only embedded in a model (Assimp import) or in a scene/prefab. They cannot be shared, edited once, or saved standalone. `RenderComponent` references textures by embedded `Texture` object, not by asset reference.
- Shader is not part of the material: `GeometryPass` passes a single `defaultShader` to every object.
- Sort key (REN-03) uses `baseColorTexture.texId` as a material-id proxy.

## 2. Goals / non-goals

Goals: data-driven material = shader reference + typed parameter block + named texture slots; PBR metallic/roughness as the default; materials serializable as standalone assets (`.skmat`); binding logic in one place and unit-testable; per-material uniform-value caching; backwards compatibility with existing scenes/prefabs/models.

Non-goals (other tasks): shader variants/`#include` (REN-05), light buffers (REN-06), node-graph material editor, UBO packing (follow-up, see 7).

## 3. Data model (new, in `engine/render/material/`, CPU-only, GL-free)

```kotlin
enum class ParamType { FLOAT, VEC2, VEC3, VEC4, INT, BOOL }

@Serializable
data class MaterialParamDef(val name: String, val type: ParamType, val uniform: String, val default: ParamValue)

@Serializable
data class TextureSlotDef(val name: String, val slot: Int, val samplerUniform: String, val hasUniform: String? = null)

/** Describes what a shader expects. Registered per shader/asset; PBR ships built in. */
@Serializable
data class MaterialSchema(val id: String, val shader: String, val params: List<MaterialParamDef>, val textures: List<TextureSlotDef>)

@Serializable
class MaterialAsset(
    val guid: String,
    val name: String,
    val schema: String,                       // schema id, default "pbr-metallic-roughness"
    val params: Map<String, ParamValue>,      // only overrides; missing = schema default
    val textures: Map<String, String?>,       // slot name -> texture asset path/GUID
    val renderState: RenderStateDef,          // doubleSided, alphaMode, alphaCutoff, depthWrite
    val version: Int = 1,
)
```

`ParamValue` is a small sealed class (Float/Vec2/Vec3/Vec4/Int/Bool) with a polymorphic serializer. Runtime `Material` (the class used by `MeshPart`/`RenderComponent`) becomes a thin façade over `MaterialAsset` + resolved `Texture` objects so existing call sites (`material.baseColor`, `metallicFactor`, ...) keep compiling through delegating properties for the PBR schema. The legacy fields are kept as `@Deprecated` accessors for one release.

Built-in PBR schema (`pbr-metallic-roughness`): params `baseColor` (vec4), `baseColorFactor`, `metallicFactor`, `roughnessFactor`, `emissiveFactor` (vec3), `alphaCutoff`; textures `baseColor`(0), `normal`(1), `metallicRoughness`(2), `ao`(3), `emissive`(4) — slot numbers identical to today's `TextureSlots`, so the existing GLSL is unchanged.

## 4. Binding (`MaterialBinder`, in `engine/render/material/`)

- Pure logic part, no GL: `MaterialBinder.plan(material, schema)` produces a flat, cached `BindPlan` (array of `(uniformSlotIndex, type, value)` + texture assignments + render state) rebuilt only when the material's `revision` counter changes. This is what unit tests cover.
- GL part: `bind(plan, shader)` uploads via an injected `UniformSink` interface (`setFloat/Vec3/...`), implemented by `Shader` (mockable). Value caching: the binder remembers the last material revision bound per shader and skips uploads when the same material is bound again (consecutive draws after REN-03 sorting). Textures go through the REN-03 `BindingCache`.
- Render state (blend, depth write, cull) is derived from `renderState` once and applied via `GLStateTracker`.
- `ModelRenderer.renderMeshPart` shrinks to: resolve material → `binder.bind` → draw. `renderMeshPartSimple` uses a `MaterialBinder.bindDepthOnly` (base-color texture + alpha mode only).
- Sort key: material id = the material's runtime id (index in a `MaterialRegistry`), replacing the texture-id proxy; shader id = schema's shader.

## 5. Serialization / asset integration

- Material files are JSON (`.skmat`) via the existing kotlinx.serialization `Serializer`, with `version` and a migration hook.
- `MaterialRegistry` (render side) holds loaded `MaterialAsset`s keyed by GUID/path, ref-counted via the REN-02 `GpuResourceCache`. Textures referenced by path resolve through `AssetsManager.getTexture` (which will use the REN-02 cache).
- Import: `AssimpLoader` builds `MaterialAsset`s in memory from glTF/FBX materials (same values as today). Saved scenes/prefabs written by the old format keep the inline `Material` JSON; a legacy reader converts it to a `MaterialAsset` on load (no data loss). New saves reference a material asset by GUID, or embed it when it is unsaved.
- Editor "save as asset", inspector editing and thumbnail are editor work (EDT); this task exposes `MaterialAsset` mutation + `revision` bump and a `NEEDS EDT` for the inspector UI.

## 6. Migration plan (each step keeps the build green)

1. Add `MaterialSchema`, `ParamValue`, `MaterialAsset`, built-in PBR schema + tests (no behavior change).
2. Add `MaterialBinder.plan/bind` + `UniformSink`, unit tests (parameter binding, defaults, revision cache, missing textures).
3. Make runtime `Material` delegate to `MaterialAsset`; legacy JSON reader + round-trip tests.
4. Switch `ModelRenderer` to `MaterialBinder` (removes hard-coded uniform block); sort key uses the registry id.
5. `.skmat` load/save + `MaterialRegistry`; `AssimpLoader` emits assets.
6. Report editor needs to the lead.

## 7. Risks / decisions needing approval

1. **Ownership:** `Material`, `MeshPart`, `AssimpLoader`, `RenderComponent` and serialization are in Assets/ECS-owned paths. Proposal: render owns `engine/render/material/**` and the binder; one small cross-domain change to `Material` (façade) and a legacy reader. Needs lead sign-off or a coordinated Assets/ECS task.
2. **Backwards compatibility:** saved scenes/prefabs with inline `Material` must still load (legacy reader, step 3). Confirm that a deprecated façade for one release is acceptable vs. a hard break.
3. **Schema scope:** only PBR ships built in; custom schemas are data (shader + param list) but no editor for them yet. Confirm that is enough for "data-driven".
4. **Uniform value caching** is per (material revision, shader), not per-uniform; per-uniform diffing and UBO material blocks are deferred to REN-06 (light/UBO work).
5. `alphaMode` stays on the material (render state), not on the shader.
6. GLSL stays unchanged in this task (names and slots preserved); REN-05 variants will later replace the `u_Has*` booleans.

## 8. Test plan (GL-free, `Area_Behavior` naming)

`MaterialSchema_*` (defaults, unknown params rejected), `MaterialBinder_Plan_*` (override vs default, texture assignment, missing texture sets `Has*=false`), `MaterialBinder_Bind_*` (mockk `UniformSink`: same revision skips uploads, bump re-uploads), `MaterialSerialization_*` (round-trip, legacy inline → asset conversion, version migration), sort-key material id.
