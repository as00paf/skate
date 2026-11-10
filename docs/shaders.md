# Shaders

How the engine finds, compiles and reloads shaders (AST-26 §5, REN-31), and what GLSL you can write.

## Engine shaders and project overrides

- Engine shaders are engine defaults: `engine:Shaders/<name>.glsl`. Their sources live in the engine repo's
  `assets/Shaders/`, and the jar carries a copy under `/shaders/`. Code refers to them through
  `EngineDefaults.Shaders` (the legacy `/shaders/<name>.glsl` form still resolves to the same reference).
- **Basic** shaders are copied into every project's `Assets/Shaders/`, with `engine.source` and `engine.sha256` in
  their `.meta`:
  - mesh, texture and 2D batch shaders: `shader_3d_default`, `shader_3d_texture`, `shader_2d_batch`;
  - `shadow`, `skybox`, `skydome`, `particles`;
  - the includes `fog` and `lighting`;
  - post-processing: `post_bloom`, `post_tonemap`, `post_gamma`, `post_fxaa`, `post_ssao`.
- **Internal** shaders (`editor_grid`, `picking`, `shader_3d_picking`, `debugLine2D`, `splash`, `ibl_capture`) stay in
  the jar and cannot be overridden.
- **Editing a copy makes it an override.** The renderer uses your file and recompiles live when you save it.
- **An unedited copy follows the engine.** Its contents still match `engine.sha256`, so the engine's file is used and
  the copy is refreshed when the project opens.
- **Deleting the copy** goes back to the engine shader. Moving or renaming it keeps it in use, because the `.meta`
  names the shader it overrides.

Resolution order for `engine:Shaders/X`:

1. in an exported game, the pack entry; in the editor, a modified project override;
2. the engine repo's `assets/Shaders/X` (dev runs);
3. the jar's `/shaders/X`.

A project shader that is not an engine default (`Assets/Shaders/my_water.glsl`) is read from its file, or from the
pack in an exported game.

## `@skate-contract`

Every basic engine shader starts with `// @skate-contract <n>`. The number goes up whenever the shader's contract with
the engine's Kotlin code changes: uniform names, feature defines (`SKINNED`, `SHADOWED`, `FOG`, `ALPHA_TEST`, ...) or
uniform block layouts.

If your override declares an older contract than the engine shader (or none), it would render wrong after an engine
update. The engine then logs one error naming the file and both versions, and uses the engine shader instead. To get
your changes back:

1. compare your override with the new engine version (`assets/Shaders/` in the engine repo, or "Replace with engine
   version" in the Asset Browser, which is undoable);
2. merge your edits into it;
3. set `// @skate-contract <n>` to the engine's number.

## Includes

`#include "name.glsl"` is expanded once per program (like `#pragma once`) and resolves relative to the file that
contains it:

| Includer | `#include "fog.glsl"` reads |
|---|---|
| an engine shader or override | `engine:Shaders/fog.glsl`, so a project override of `fog.glsl` applies to every shader that includes it |
| a project shader `Assets/Shaders/water/my_water.glsl` | `Assets/Shaders/water/fog.glsl` if it exists, else `engine:Shaders/fog.glsl` |

`#include "engine:Shaders/fog.glsl"` always reads the engine's own file, so an override can wrap the original:

```glsl
// @skate-contract 1
// Assets/Shaders/fog.glsl: the engine fog, slightly bluer
#include "engine:Shaders/fog.glsl"
vec3 applyBlueFog(vec3 c, vec3 cam, vec3 p) { return applyFog(c, cam, p) * vec3(0.95, 0.97, 1.0); }
```

`..` is not allowed in include names.

## Hot reload

- In the editor (and in dev runs from the engine repo), the engine checks the files every loaded shader was built from
  about twice a second: the main file, its includes, and include names that were looked up and not found.
- A change is applied once the file has been stable for two checks, so a half-saved file is not compiled.
- Every program and variant that depends on the changed file is recompiled in place.
- Adding or deleting an override is noticed too: the reference is resolved again before recompiling.
- **A program that fails to compile keeps its previous version.** The error names the original file and line, even
  inside an include (`lighting.glsl:120`).
- An engine shader whose override fails to load or compile uses the engine's version of the **same** shader until the
  override is fixed. It never falls back to another shader.
- Exported games read shaders from the pack and do not watch files.

## The GLSL you can write

Shader files use the single-file format: a `#type vertex` section, then a `#type fragment` section, each with its own
`#version 330 core`. GLSL 3.30 core runs on OpenGL as is. The Vulkan backend (exported games) rewrites it to GLSL 4.50
(`GlslToVulkan`), which supports a subset:

- **One uniform per declaration:** `uniform float a;`, not `uniform float a, b;`.
- **Uniforms at global scope, outside `#if`/`#ifdef` blocks,** with no initializer (`uniform float a = 1.0;` is not
  allowed).
- **Explicit vertex input locations:** `layout (location = 0) in vec3 aPos;`.
- **Matching stage interfaces:** every fragment `in` has a vertex `out` with the same name.
- **Uniform blocks** are `layout(std140) uniform Name { ... };`.
- **Samplers** are plain `uniform sampler2D` (or `sampler2DArray`, `samplerCube`, shadow samplers): the engine assigns
  bindings.

Project shaders and overrides are checked against this subset when they load, also when the editor runs on OpenGL. A
shader outside it still runs on OpenGL, but the console shows an error naming the file, because it would fail in a
Vulkan export.

## Scope (v1)

A material's custom shader only replaces the main colour pass. Shadows, picking and instanced vegetation keep the
engine shaders, so a shader that moves vertices will not match its shadow or its clickable area.
