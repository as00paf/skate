# AST-26: Typed asset folders (design note)

Status: **approved by the human (2026-11-10).** The human answered the nine open questions of revision 1 (§0.2) and
the §12 questions of revision 2 (§0.3) on 2026-11-10.
Implements in: AST-27 (skate-assets), AST-28 (skate-online), REN-31 (skate-render, §5), EDT-69 (skate-editor),
EDT-70 (skate-tooling).

## 0. Decisions

### 0.1 Human decisions (2026-11-10, from the backlog)

- `Assets/` holds one folder per type: `Models`, `Shaders`, `Sounds`, `Textures`, `Materials`, `Scripts`, `Animations`.
- **No `EngineDefaults` folder anywhere.** Engine defaults that are copied go straight into `Assets/<Type>/`.
- A New Project setting chooses whether the engine defaults are copied.
- Asset Store imports go to their type folder, not `Assets/Store`. A store model stays together in
  `Assets/Models/<asset>/` (`.gltf`, `.bin`, `textures/`). Texture-only and material downloads go to `Textures/` and
  `Materials/`.
- The Asset Browser gets one tab per type (EDT-69).
- Scope change: "do NOT design migration of existing projects or any backward compatibility — no migration, backup,
  undo or old-layout support. Drop that section; new projects only. Everything else stands."

### 0.2 Answers to the revision 1 open questions (2026-11-10, verbatim)

1. Prefabs: Assets/Prefabs/ (Prefabs tab stays).
2. Other files: MORE type folders: add Fonts/ and Data/ (json configs etc.); animator controllers go in Animations/.
   Each type folder gets an Asset Browser tab (EDT-69).
3. Shaders: the renderer must load shaders through the same engine-default/override rule as other assets, following
   what engines usually do: the engine shaders are engine:Shaders/... defaults, a project copy in Shaders/ overrides
   them (editing it changes rendering, with hot reload), and users can write new shaders and assign them to materials.
   In addition, the basic shaders are ALWAYS copied into Shaders/, even when 'copy engine defaults' is off (an empty
   project still has them). This touches the renderer: design the shader resolution and hot-reload path with
   skate-render (ask it to review; it owns REN), and add the render slice to the task split.
4. 'Copy engine default assets' defaults to ON.
5. Reorganize the engine repo's own assets/ folder into the same typed folders (Models, Textures, Sounds, Shaders,
   Animations, ...) instead of mapping the old layout. Cover tests, samples, export scripts and verify-game-jar.sh.
6. Dedupe in AST-27: PackBuilder stores identical files once (content hash).
7. Confirmed: no conversion of schema-1 attribution ledgers.
8. Model textures hidden from the Textures tab by default, with a 'Show model textures' filter.
9. Name clash when copying a default: copy as 'cube (engine).obj', which becomes the override.

### 0.3 Answers to the §12 open questions (2026-11-10, verbatim; note approved)

- NQ1: editor-only files (strings, themes, help) move into src/main/resources/editor/{strings,themes,help}, packaged in
  the jar, loaded from the classpath and read from src/main/resources in dev runs. Engine default assets (models,
  textures, sounds…) stay in the repo's assets/ folder, reorganised into typed folders. The game string tables go to
  assets/Data/strings/game/.
- NQ2: skater configs: follow-up task (now GPL-26), not AST-27.
- NQ3: Assets/strings.properties stays at the root of Assets/.
- NQ4: delete the Font Awesome desktop zip.
- NQ7: accepted: unedited shader copies follow the engine on project open.
- NQ8: accepted: custom material shaders affect the colour pass only in v1.

The backlog now has REN-31 (the render slice, skate-render; was "REN-31") and GPL-26 (skater configs). skate-qa is
writing help articles into `src/main/resources/editor/help/` (EDT-53): AST-27a moves only what is still under
`assets/help/` and does not move those articles again.

## 1. Current state (what this changes)

- `ProjectLayout` (AST-14): `Assets/` with `EngineDefaults/{Characters,Models,Shaders,Sounds,Textures}`, plus
  `Scenes/` and `Builds/` next to it.
- `Assets.Bundled` pairs project-side paths (`EngineDefaults\Models\cube.obj`) with engine sources under the editor's
  working directory (`assets/obj/cube.obj`, `assets/characters/…`). `EngineAssetCopier` copies them when a project is
  created, plus 10 shaders from the classpath and the app icon.
- Code builds references as `projectAssetsDir + Assets.Bundled.X`: `PrefabsGenerator`, `PrefabCatalog`,
  `MaterialType`, `FeatureShowcase`, the CLI sample builders and `PrefabThumbnailMaterial`.
- **Shaders:**
  - `RenderResourcesFactory`, `PickingRenderer` and `ThumbnailRenderer` ask `AssetsManager.getShader` for classpath
    paths (`Assets.Shaders.*`, plus literals such as `"/shaders/post_bloom.glsl"`).
  - `ShaderLoader.readSource` reads `src/main/resources/<path>` when it exists (dev runs), else the classpath.
    `ShaderPreprocessor` resolves `#include` the same way.
  - `ShaderHotReloader` polls `src/main/resources/shaders` only. `AssetsManager.reloadShaders` recompiles in place with
    `Shader.replaceWith` and keeps the old program when a compile fails.
  - The shader copies in a project are never read.
  - `.skmat` (`MaterialAsset`) has no shader field: it names a `MaterialSchema` (`pbr-metallic-roughness` →
    `shader = "default3d"`).
- Store imports (AST-16, on `integration/30g`) go to `Assets/Store/<provider>/<slug>/`. The ledger entry has one
  `folder`.
- `AssetsPacker` + `ReferenceCollector` pack what is reachable from scenes, prefabs and the project file. For a model,
  they also pack every image in the model's folder. `PackBuilder` writes every input separately, even identical ones.
- The engine repo's `assets/` is laid out by history: `obj/`, `characters/` (model, `animations/`, `textures/`),
  `textures/`, `sounds/{motion,music,skate}`, `fonts/`, plus editor-only `strings/`, `themes/`, `help/`. Shaders live in
  `src/main/resources/shaders/` (19 files).

## 2. Layout of a new project

```
<project>/
  <name>.skateproject
  Assets/
    Models/  Animations/  Textures/  Materials/  Sounds/
    Scripts/ Shaders/     Fonts/     Data/       Prefabs/
    strings.properties
  Scenes/
  Builds/
  .skate/          hidden: attribution ledger, store staging (AST-16)
```

- `ProjectLayout.create` makes all ten type folders, even when they are empty. `Shaders/` is never empty: the basic
  shaders are always copied (§5.2).
- `ProjectLayout` gets `enum class AssetFolder(val dirName: String)` with the ten folders, and
  `typeDir(projectDir, folder)`. `ENGINE_DEFAULTS_DIR` and `engineDefaultsDir` are removed.
- `Scenes/` stays outside `Assets/`. `strings.properties` stays at the root of `Assets/` (new question NQ3).
- The type folders are where **new** files land. Nothing forces a file to stay there: references keep working after a
  move (GUIDs, `AssetRegistry.move`).
- Projects made before AST-27 are unsupported (no migration, no old-layout support).

## 3. Which folder a file lands in

One function decides, `AssetFolders.folderFor(file): AssetFolder?` (engine/assets). Drop and import in the Asset
Browser, store imports, Blender imports and the engine-default copier all use it. `null` means the root of `Assets/`.

| File | Folder | Notes |
|---|---|---|
| `.gltf` (+ `.bin`, images), `.glb`, `.obj` (+ `.mtl`), `.dae` | `Models/` | A multi-file model gets its own folder `Models/<asset>/` (§3.1). |
| `.fbx` | `Models/` or `Animations/` | `FbxClassifier` (AST-20): meshes → `Models/`, animation-only → `Animations/`; both → model with clips. |
| `.blend` | `Models/` | Generated outputs stay next to it (§3.2). |
| `.skanim`, `*.controller.json` (animator controllers, `AnimatorControllerCatalog.isController`) | `Animations/` | Decision 2. |
| `FileType.TEXTURE` (`png jpg jpeg tga bmp psd gif hdr pic pnm`) | `Textures/` | A texture set gets `Textures/<set>/` (§3.1). |
| `FileType.MATERIAL` (`skmat mat material`) | `Materials/` | |
| `FileType.SOUND` (`wav ogg mp3`) | `Sounds/` | |
| `FileType.SCRIPT_*` (`kts kt java`, later `ts lua`) | `Scripts/` | |
| `FileType.SHADER` (`glsl`) | `Shaders/` | |
| `FileType.FONT` (`ttf otf`) | `Fonts/` | Decision 2. |
| `FileType.PREFAB` | `Prefabs/` | Decision 1. |
| other `FileType.JSON`, `CONFIG`, `TEXT` (`json config txt`; `.character.json` etc.) | `Data/` | Decision 2. Except `strings.properties` (NQ3). |
| `.scene` | `<project>/Scenes/` | Unchanged. |
| anything else | root of `Assets/` | |

### 3.1 Multi-file assets and AST-22

AST-22 resolves a model's textures and buffers relative to the model file, so a multi-file asset is never split:

- **A model with companions** (`.gltf` + `.bin` + `textures/`, `.obj` + `.mtl` + maps, a store download): the whole
  folder goes to `Assets/Models/<asset>/` with its internal layout unchanged. Its textures stay in
  `Models/<asset>/textures/`.
- **A texture set** (ambientCG, Poly Haven textures): the maps go to `Assets/Textures/<set>/`; its `.skmat` goes to
  `Assets/Materials/<set>.skmat`. A `.skmat` stores project-relative texture paths (written by
  `AssetPaths.relativize`), so it does not need to sit next to its textures.
- **A single file** goes into the type folder itself, with no subfolder.
- `<asset>` is `StorePaths.slug` of the source name. When the name is taken, add `_2`, `_3`… (the same rule as
  `StoreImporter.freshTargetDir`).
- **Packing** (AST-27):
  - `ReferenceCollector` stops packing every image in a model's folder.
  - `.obj` follows `mtllib` and the `.mtl`'s `map_*` lines.
  - `.gltf` follows its URIs (it already does).
  - `.glb` is self-contained.
  - `.dae` keeps the sibling rule, but only inside a `Models/<asset>/` subfolder.

### 3.2 `.blend` imports (AST-19)

Unchanged: `Assets/Models/Board.blend`, with its outputs in `Assets/Models/Board.blend.import/` (`Board.glb`,
`Board@<Action>.glb`).

- The `@` clips stay with their `.blend` (they are rewritten in place to keep GUIDs). The Animations tab finds them by
  type (§8).
- A `.blend` dropped elsewhere imports where it was dropped.

## 4. Engine defaults

### 4.1 The engine repo's `assets/` (decision 5)

The repo's `assets/` uses the project layout, so a logical path **is** the path under `assets/`:

```
assets/
  Models/       cube.obj … spawn_marker.obj, skateboard.obj, james.glb, Ch06/ (Ch06_nonPBR.dae + textures/), baseball-cap.blend
  Animations/   idle_0.fbx … skate riding.fbx, breakdance/
  Textures/     asphalt.png, concrete_simple.png, metal.png, wood_{brown,light,tan,dark}.png, sky_hdri.png, app_icon.png,
                camera.png, xbox_controller.png, ps_controller_top.png, splash_screen.png, white.png, sources/
  Sounds/       Music/, Motion/, Skate/
  Shaders/      the 19 *.glsl, moved from src/main/resources/shaders (REN-owned, §5.1)
  Fonts/        from fonts/
  Data/         strings/game/ (the game string tables, NQ1); skater configs later (GPL-26, NQ2)

src/main/resources/editor/
  strings/, themes/, help/   (editor-only, in the jar, never copied into projects; NQ1, §0.3)
```

- `skatelite_*.png` are renamed `wood_*.png`, the names projects already use.
- `assets/characters/james.glb` and the loose `Ch06` files move under `Models/`.
- **Case-only renames** (`textures` → `Textures`, `sounds` → `Sounds`, `fonts` → `Fonts`) need two `git mv` steps on a
  case-insensitive Windows checkout (`textures` → `textures_tmp` → `Textures`). AST-27 does the move in its own commit
  with no other changes, so reviews and merges stay readable.
- **References to update:**

  | Repo path | Files referencing it |
  |---|---|
  | `assets/obj` | 9 |
  | `assets/characters` | 13 |
  | `assets/textures` | 8 |
  | `assets/sounds` | 5 |
  | `assets/themes` | 4 |
  | `assets/strings` | 27 |
  | `assets/help` | 2 |
  | `/shaders/` literals | 13 |
  | `resources/shaders` | 12 |

  - Main code: `Assets.*` constants, `Assets.Strings`, `Assets.Themes`, `AssetDiscovery.ENGINE_ASSETS_DIR`, the help
    viewer and `EditorStrings` loaders.
  - Tests: `AssimpLoaderTest`, `AssimpModelInspectionTest`, `GltfReferencesTest`, `SkeletonJointTest`,
    `SkateRidingClipTest`, `SkaterControllerTest`, `RestPoseRetargetTest`, `SkAnimSkaterTest`, `SkateparkShapesTest`,
    `VideoToAnimationControllerTest`, `UndoCoverageCommandsTest`, `SyncCommandSemanticsTest`, `Round16ReviewFixesTest`.
  - Samples: `FeatureShowcase` and the CLI builders.
  - Docs: `.github/copilot-instructions.md`, `docs/`.
- **Guard test (AST-27):** every literal `assets/<lowercase old folder>/` in `src/` or `tools/` fails the build.
- `assets/fonts/fontawesome-free-7.1.0-desktop.zip` is a source archive, not an asset. It is deleted (NQ4).
- **Editor-only files (NQ1):** `assets/strings/editor`, `assets/themes` and `assets/help` move to
  `src/main/resources/editor/{strings,themes,help}`. They are loaded from the classpath (`Assets.Strings`,
  `Assets.Themes`, the help viewer and `EditorStrings` loaders), and dev runs read them from `src/main/resources` so
  edits show without a rebuild. Help articles skate-qa already wrote there (EDT-53) stay where they are.
- **Build and scripts:**
  - `build.gradle.kts` `processResources` copies `assets/Shaders/**` into the jar at `/shaders/`, so the editor and
    the game jar keep a classpath copy (`Assets.Fonts.FONTS_FILE` and `/textures/default.png` are classpath resources
    already and stay).
  - `tools/scripts/verify-game-jar.sh` gets a step that checks `skate-game.jar` contains all 19 shaders (basic and
    internal, §5.1) under `/shaders/`.
  - The shader move is **its own commit with only the shader move**. skate-render is told before it lands, so
    in-flight REN branches can rebase. Tests that read engine shaders (`VertexLayoutShaderTest`, the `GlslToVulkan`
    tests, the preprocessor tests) still find them on the test classpath, because `processResources` keeps the
    `/shaders/` prefix.
  - The export (`ProjectExporter` / `GameJarTask`) needs no path change: it copies the jar and the pack.

### 4.2 Catalog and the `engine:` reference

`Assets.Bundled` becomes `EngineDefaults` (a Kotlin catalog object; there is no folder by that name).

- Each entry is a logical path such as `Models/cube.obj`, and the reference is `engine:Models/cube.obj`.
- The source is `<engineRoot>/assets/<logical path>`. If that file does not exist (an installed editor without the
  repo, the exported game), the source is the classpath copy for shaders and the app icon.
- Code refers to engine defaults only through `EngineDefaults.CUBE = "engine:Models/cube.obj"`.
  `PrefabsGenerator.projectAssetsDir` and every `projectAssetsDir + Bundled.X` go away.
- **Rules for the reference:**
  - Prefix `engine:`, then a `/`-separated path, with no `..` and no drive.
  - A colon can never appear in a project path, so `ReferenceCollector` and `AssetPaths` can tell the two apart
    without touching the disk.
  - Every resolver checks the prefix **before** building a `File`. On Windows, `File("engine:x")` would otherwise
    probe an NTFS stream.
- **GUIDs:** `AssetRegistry.idOf("engine:…")` returns a GUID derived from the logical path (name-based UUID), so
  GUID-based APIs accept engine defaults whether or not they are copied.
- **Catalog contents:**
  - the prefab models, `james.glb`, the 18 skater clips, the surface textures, `sky_hdri.png`, `app_icon.png` and the
    music;
  - the **basic shaders** (§5.1). The 6 internal shaders are not in the catalog and cannot be overridden.

### 4.3 The two modes (EDT-70 setting "Copy engine default assets", **on** by default; decision 4)

| | Copy (default) | Do not copy |
|---|---|---|
| On creation | Every catalog entry is copied to `Assets/<logical path>`, with `engine.*` keys in its `.meta` (§4.5). | Only the basic shaders (§5.2). |
| References in default scenes, `iconPath` | `engine:…` | `engine:…` (the same) |

A project copy is an **override**: `engine:X` resolves to the project file whose `.meta` has `engine.source = X`,
wherever it is in `Assets/`.

- Editing the copy changes the game.
- Deleting it falls back to the engine's file.
- Moving or renaming it keeps it in use.

The mode is a creation-time action, not a project setting, so the `.skateproject` schema does not change. "Copy into
project" (Asset Browser, §8) adds one later.

### 4.4 Resolution in the editor

`AssetPaths` gets an `EngineDefaultResolver`, which needs the project's `AssetRegistry` and the engine root.

- `absolute("engine:X")` gives:
  1. the project override, through the registry's `engine.source → path` index (built in `scan`, kept up to date by
     `register`, `move` and `delete`);
  2. otherwise `<engineRoot>/assets/X`;
  3. otherwise the classpath copy, extracted once to the cache folder `AssetPaths` already has.
- `relativize(path)` turns an engine source file, or a project file whose `.meta` has `engine.source`, back into
  `engine:X`. A load → save round trip keeps `engine:` (guard test).
- Loaders that build a `File(path)` directly (`AssetsManager.getTexture`, the `AssimpLoader` entry points) are routed
  through `AssetPaths`.
- A missing source shows the missing-asset placeholder and logs an error with the logical path.

### 4.5 Telling engine copies apart (updates, name clashes)

A copy's `.meta` `settings` (`AssetMeta` stays at schema 1):

| Key | Value |
|---|---|
| `engine.source` | the logical path |
| `engine.sha256` | hash of the engine file at copy time |

- **Unchanged copy:** the copy's hash equals `engine.sha256`.
- **Modified copy:** the hashes differ.
- **Update available:** the engine source's hash differs from `engine.sha256`.
  - An unchanged copy can be updated: one notification on project open, "N engine defaults have newer versions —
    Update".
  - A modified copy is never overwritten. It shows "modified, engine has a newer version", with "Replace with engine
    version" (undoable).
  - Nothing updates silently.
- **Exception for shaders:** an unchanged shader copy follows the engine (§5.2).
- **Name clash (decision 9):** `Assets/Models/cube.obj` exists and is not already the override for that logical path.
  The user's file keeps its name, and the copy is `cube (engine).obj`, which carries `engine.source` and becomes the
  override.
- Duplicating a copy in the editor drops the `engine.*` keys.
- Two files with the same `engine.source` (copied outside the editor along with the `.meta`): the first by path wins,
  and `skate validate` warns.
- A catalog entry is never removed or renamed without an alias.
- Guard test: every catalog source exists and every logical path is unique.

### 4.6 Export and dedupe (decision 6)

- `ReferenceCollector` handles `engine:` tokens itself. It resolves each through `EngineDefaultResolver` (override or
  engine source) and emits a pack input whose **pack path is the reference** (`engine:Models/cube.obj`). The packer
  takes a small `PackItem(bytes source, packPath)` instead of a project `File`, because the source can be outside the
  project.
- At runtime, `AssetPaths.readBytes` / `localPath` find `engine:…` entries in the pack (the existing extractor already
  strips `:`).
- **Dedupe in `PackBuilder`:** inputs are grouped by `sha256` before writing. The first occurrence is written, and later
  entries reuse its `position`, `storedSize` and `compressed`. `ManifestEntry` already addresses entries by position,
  so readers (`AssetsResolver`, `PackBuilder.verify`) need no change and `PackManifest.SCHEMA` stays. An override that
  is referenced both as `engine:` and by its project path is stored once. `atlas_report.txt` lists shared entries.
- `ProjectExporter` resolves `iconPath` through `AssetPaths`.
- Shaders are always packed (§5.5).

## 5. Shaders (decision 3; REN-31, reviewed by skate-render, §11.1)

### 5.1 Engine shaders are engine defaults

- `assets/Shaders/*.glsl` (moved from `src/main/resources/shaders`; `processResources` also puts **all 19** in the jar
  at `/shaders/`, §4.1) are `engine:Shaders/<name>.glsl`.
- `Assets.Shaders` constants and the string literals in `RenderResourcesFactory`, `PickingRenderer` and
  `ThumbnailRenderer` become `EngineDefaults.Shaders.*` references.
- **Two sets**, kept in the catalog and checked by a guard test (every `.glsl` in `assets/Shaders/` belongs to exactly
  one):
  - **Basic** (always copied, can be overridden), 13 today: `shader_3d_default`, `shader_3d_texture`,
    `shader_2d_batch`, `shadow`, `skybox`, `skydome`, `particles`, `fog` (include), `post_bloom`, `post_tonemap`,
    `post_gamma`, `post_fxaa`, `post_ssao`. REN-28 task 1 adds `lighting.glsl`; REN-29 adds `wind.glsl`,
    `water_waves.glsl`, `water.glsl`; REN-28 adds `terrain.glsl`.
  - **Internal** (only in the jar, never copied, cannot be overridden), 6:
    - `editor_grid`;
    - `picking` and `shader_3d_picking`: they must match the entity-id encoding in `PickingTexture`;
    - `debugLine2D`;
    - `splash`: it draws before any project is resolved;
    - `ibl_capture`: its output encoding (`IblMath.encode`) is a contract with `shader_3d_default` and the Kotlin
      bake.

    Internal shaders also ship in exported games, so all 19 stay in the jar.
- **Contract version:** every basic engine shader starts with `// @skate-contract <n>`. `n` is bumped whenever its
  contract with Kotlin code changes: uniform names, `ShaderFeature` defines, UBO layouts.

### 5.2 Basic shaders are always copied, and unchanged copies follow the engine

- Every project gets the basic set in `Assets/Shaders/`, with `engine.*` keys, in both copy modes (decision 3).
- **Shader exception to §4.5** (skate-render CR1): an engine shader is a contract with Kotlin code, so a stale copy
  renders wrong after an engine update.
  - An **unchanged** shader copy (its hash equals `engine.sha256`) is **not** an override: it resolves to the engine
    file. The copy is refreshed silently when the project opens. It has no user edits, so nothing is lost. NQ7
    confirms this exception with the human.
  - A **modified** override whose `@skate-contract` is lower than the engine shader's logs one clear error and falls
    back to the engine shader instead of drawing garbage. `skate validate` reports it too. The user then merges their
    edits into the new version ("Replace with engine version" keeps a backup through undo).

### 5.3 Resolution

`ShaderLoader` gets a `ShaderSourceResolver` instead of `devSourceRoot` + `getResourceAsStream`.
`AssetsManager.shaderDevRoot` (hard-coded `src/main/resources`) goes away. For a reference:

1. `engine:Shaders/X`:
   1. a modified project override (`engine.source = Shaders/X`) with a current contract;
   2. else `<engineRoot>/assets/Shaders/X` (dev runs);
   3. else the classpath `/shaders/X`.

   In an exported game: the pack entry, else the classpath.
2. A project shader (`Assets/Shaders/my_water.glsl`): the file, or its pack entry.

**Includes** (skate-render CR4):

- `ShaderPreprocessor`'s resolver becomes `(name, includerId) -> ResolvedSource(id, text)?`. An include resolves
  relative to its **includer**, so an include nested in an engine include resolves from the engine's `Shaders/`.
- If that fails, it resolves as `engine:Shaders/<name>` through rule 1.
- The included set and the dependency set are keyed by the resolved id.
- An explicit `#include "engine:Shaders/fog.glsl"` is allowed, so an override can wrap the original.
- Overriding an include changes every shader that includes it.

**Caches** (CR3): one `ShaderVariantCache` keyed by `ShaderVariantKey(reference, defines)`; empty defines is the base
program. It replaces the `shaders` cache keyed by `File(path).absolutePath`. A failed load falls back to the **engine
version of the same reference**, then a hard error. It never falls back to `shader_3d_default` (today a broken
`post_bloom` would silently get the mesh shader).

**Line numbers** (CR9): `ShaderLoader` keeps a line map from the expanded source back to each file, so a compile
error reads `my_water.glsl:42`.

### 5.4 Validation for Vulkan (CR2)

`GlslToVulkan` handles only the engine's GLSL subset:

- one uniform per declaration, at global scope, outside conditionals, no initializers;
- the `#type vertex`/`#type fragment` single-file format.

A shader that compiles on GL may therefore fail only in a Vulkan export. So:

- `GlslToVulkan.convert` (text only, no GPU) runs on every user shader and override when it loads in the editor,
  even on GL, and in `skate validate`. Errors go to the console with the file name.
- The subset is documented in `docs/shaders.md` (new, REN-owned).
- Vulkan start-up compiles every shader through shaderc (`VkShaders`) with no SPIR-V cache. That grows with user
  shaders; a cache keyed by source hash is a later VK task.

### 5.5 Hot reload (CR5)

- A reload **re-resolves before it recompiles**: adding or deleting an override changes which file a reference
  points to.
- `ShaderHotReloader` watches only the files in loaded programs' dependency sets, not all of `Assets/` every 500 ms.
  It uses `AssetRegistry` register, move and delete events for overrides that appear or disappear.
- It can reuse `AssetReloader`'s stable-for-two-polls rule. Polling stays on the GL thread
  (`AssetsManager.pollShaderChanges`).
- Programs and variants whose dependencies changed are recompiled in place (`Shader.replaceWith`) on both backends.
- A failed compile keeps the previous program and logs the mapped file and line.
- Dev runs pick up edits to `assets/Shaders/` through rule 1, step 2.
- Export: `FileType.SHADER` joins `AssetsPacker.alwaysPacked`. Dedupe stores each distinct file once. The exported
  game loads `engine:Shaders/*` from the pack (modified overrides) and falls back to the jar.

### 5.6 User shaders on materials

- `MaterialAsset` gets `shader: String? = null`, a reference to a project shader or `engine:Shaders/…`. Null means the
  schema's shader, as today. `MaterialAsset.CURRENT_VERSION` is bumped, with a migration test: an older file reads as
  `shader = null`. That is file-format versioning, not project migration.
- **Uniform contract:** a custom shader must declare the uniforms and samplers of the material's `MaterialSchema`. This
  is checked when the shader is assigned, from `SpirvReflection` / GL's active uniforms, and a warning is shown when
  something is missing.
- **Features** (CR8): the shader is compiled with the draw's `ShaderFeature` defines (`SKINNED`, `SHADOWED`, `FOG`,
  `ALPHA_TEST`; later `INSTANCED`, `WIND`). A shader opts in to a feature by containing `#ifdef X` or
  `#if defined(X)` (text scan). For a draw needing a feature the shader does not handle, or a variant that fails to
  compile, the material falls back to the schema shader, logged once.
- **Frame uniforms** (CR6, REN-internal): today `GeometryPass` uploads frame uniforms (matrices, camera, sun,
  `LightBuffer`, shadow cascades, IBL via `EnvironmentProbe.bind`) only to the default shader. REN-31 uploads
  them to each program on its first use in a frame, and fills the 16-bit shader field of `SortKey.build` (0 today),
  so draws group by program.
- **Scope v1: the colour pass only** (CR7). Shadow, picking and instanced vegetation keep the engine shaders, so a
  user shader that moves vertices will not match its shadow or its clickable area. This is documented; an optional
  `shadowShader` is a later task.
- **Editor:** the material inspector gets a "Shader" field (`.glsl` files in the project plus the basic engine
  shaders). The Asset Browser gets a "New Shader" action that creates a template compatible with the PBR schema in
  the `#type` format. These are EDT widgets in REN-31, with an EDT review.
- **Custom parameters** (NQ6, answered by skate-render): a later REN task. Parameters are declared in the GLSL file
  with annotations (`// @param float uWaveHeight = 0.5 "Wave height"`, `// @texture uFoam slot 6`). A GL-free parser
  turns them into a `MaterialSchema` with id `shader:<reference>`, registered through `MaterialSchemas.register`; this
  is the same mechanism as REN-29's water schema.

## 6. Store imports (AST-28, skate-online)

- `StorePaths.importDir(providerId, slug)` becomes `importTarget(plan)`:
  - **model item:** `Assets/Models/<slug>/`, internal layout kept. A Kenney/Quaternius pack import makes one folder per
    model.
  - **texture set:** maps in `Assets/Textures/<slug>/`; `.skmat` in `Assets/Materials/<slug>.skmat`.
  - **single texture / HDRI:** `Assets/Textures/<slug>.<ext>`.
  - **sound:** `Assets/Sounds/<slug>.<ext>`, or `Assets/Sounds/<slug>/` for several files.
  - **font:** `Assets/Fonts/<slug>.<ext>`.
- The `.meta` store keys are unchanged: `store.provider`, `store.sourceId`, `store.license`.
- **Ledger:** `LedgerEntry.folder` becomes `folders: List<String>`, ledger `schema` 2. A schema-1 ledger is reported as
  unsupported and **not converted** (decision 7).
- `ledgerFolder` trusts a folder only when it resolves inside one of this project's type folders.
- Re-import in place uses the ledger's `folders`. Undo (`StoreImporter.revert`), backups, AST-25 delete → ledger, and
  GUIDs kept across re-imports work unchanged, because they are keyed by file.

## 7. Samples, CLI, `skate validate`, export (EDT-70, skate-tooling)

- **CLI `new` and the sample builders** (Tetris, Marble Run, Showcase):
  - `ProjectLayout.create` + `EngineDefaults` references; `copyEngineAssets` only in copy mode (the basic shaders
    always).
  - `skate new --engine-defaults copy|reference`, default `copy`.
  - Samples are generated in **reference** mode: smaller, and they exercise that mode.
  - Scripts go to `Assets/Scripts/`, materials to `Assets/Materials/`.
- **`skate validate`:**
  - **errors:** an `engine:` reference not in this engine's catalog; a missing engine source.
  - **warnings:**
    - a missing type folder
    - a missing basic shader in `Assets/Shaders/`
    - two overrides for one logical path
    - a modified engine copy whose engine version changed
  - **unchanged:** today's checks.
  - No old-layout checks.
- **`skate pack` / `export`:** go through §4.6 and §5.4; no flag changes.
- **New Project dialog:** a "Copy engine default assets" checkbox, on by default, with a tooltip explaining both modes.
  `ProjectManager.prepareProjectFolder(name, folder, copyDefaults)`.

## 8. Asset Browser (EDT-69, skate-editor)

- **Tabs, one per type folder** (decision 2): Models, Animations, Textures, Materials, Sounds, Scripts, Shaders, Fonts,
  Data, Prefabs.
- **A tab lists by type over the whole `Assets/` tree**, not by folder (`FileTypeResolver` + `FbxClassifier` + the
  `@` rule for `.blend` clips + `isController` for Animations). A moved file still shows, and `Board@Ollie.glb` shows
  under Animations.
- **Model textures** (images inside a multi-file `Models/<asset>/` folder) are hidden from the Textures tab by default,
  with a "Show model textures" filter (decision 8).
- **Engine defaults are listed whether copied or not:**
  - not copied: a virtual entry with an "Engine" badge, and "Copy into project";
  - copied: an "Engine copy" badge, "Modified" / "Update available", and "Replace with engine version";
  - the update notification (§4.5).
- Drop and import place the file with `folderFor`, whichever tab is open.
- The shared search, empty states and context menu of EDT-52C apply to every tab.

## 9. Slicing

| Task | Owner | Contents | Deps |
|---|---|---|---|
| **AST-27a** | skate-assets | Repo `assets/` reorganisation (§4.1) in its own commit: moves, path updates in main code, tests, samples, docs, guard test, `processResources` + `verify-game-jar.sh` (the shader move itself needs REN's OK, `NEEDS REN`). | AST-26 |
| **AST-27b** | skate-assets | `ProjectLayout`/`AssetFolder`, `folderFor`, `EngineDefaults` catalog, `engine:` resolution in `AssetPaths` + `AssetRegistry`, copier (both modes, basic shaders, name clash, `engine.*` meta), update detection service, `ReferenceCollector`/`AssetsPacker` (`engine:` items, narrower companions, `SHADER` always packed), `PackBuilder` dedupe, `ProjectManager` creation flag, `PrefabsGenerator`/`PrefabCatalog`/`MaterialType`/`FeatureShowcase`/`PrefabThumbnailMaterial` on `engine:` references. | AST-27a |
| **REN-31** (new) | skate-render | §5: `@skate-contract` headers and the stale-override fallback, `ShaderSourceResolver` with includer-aware includes (`ShaderPreprocessor`), one reference-keyed `ShaderVariantCache` with same-reference fallback, line maps, `GlslToVulkan` subset validation on load + `docs/shaders.md`, dependency-based re-resolving `ShaderHotReloader`, `EngineDefaults.Shaders` in `RenderResourcesFactory`/`PickingRenderer`/`ThumbnailRenderer`, `MaterialAsset.shader` (version bump), uniform-contract check, feature opt-in scan and fallback, per-program frame uniforms and the `SortKey` shader field, colour pass only. EDT review for the material "Shader" field and the "New Shader" template. | AST-27b; coordinate with REN-28 task 1 |
| **AST-28** | skate-online | §6. | AST-27b, AST-16 |
| **EDT-69** | skate-editor | §8. | AST-27b |
| **EDT-70** | skate-tooling | §7. | AST-27b |

## 10. Tests (focused unit tests, no integration tests)

- **`folderFor`:** every row of §3, with the FBX fixtures (AST-20), `.blend`, `*.controller.json`, fonts, `.json` →
  `Data/`.
- **Layout:** `ProjectLayout.create` makes the ten folders and no `EngineDefaults`.
- **Repo:** catalog guard (all sources exist, unique logical paths); no literal old `assets/` folder path left in
  `src/` or `tools/`.
- **Copier:**
  - copy mode writes every entry with `engine.*`;
  - reference mode writes only the basic shaders;
  - a name clash gives `cube (engine).obj`, and that copy is the override.
- **`AssetPaths`:**
  - `engine:` resolves to the engine file without an override, and to the override with one (moved or renamed too);
  - `relativize` maps both back to `engine:`;
  - the scene JSON round trip keeps `engine:`;
  - no `File()` is built for an `engine:` string.
- **Registry:** the derived GUID is stable; the `engine.source` index follows `move`/`delete`; with duplicate overrides,
  the first by path wins.
- **Update detection:** unchanged / modified / update available.
- **Packing:**
  - `engine:` items come from the engine source or the override;
  - the exported game's `readBytes` reads them;
  - an `.obj` packs its `.mtl` maps and not unrelated images;
  - shaders are always packed;
  - identical inputs are stored once and both entries extract and `verify`.
- **Shaders (AST-27b):** catalog guard (every `assets/Shaders/*.glsl` is basic or internal); an unchanged copy
  resolves to the engine file and is refreshed on open; a modified copy stays an override.
- **Shaders (REN):**
  - the resolver order (override > engine file > classpath; pack > classpath);
  - an `#include` override applies to its includers;
  - a dependency change marks exactly the programs that include it;
  - a failed recompile keeps the old program (fake device);
  - `MaterialAsset` v-old → `shader = null`;
  - a material shader that fails a variant, or lacks a feature, falls back to the schema shader;
  - a modified override with an older `@skate-contract` falls back to the engine shader;
  - a nested include resolves against its includer; `#include "engine:Shaders/fog.glsl"` works;
  - a failed `post_bloom` override falls back to the engine `post_bloom`, never to `shader_3d_default`;
  - a compile error is reported with the original file and line (line map);
  - `GlslToVulkan.convert` rejects a shader outside the subset, on GL too.
- **Store (AST-28):** fixture tests per provider for target folders and ledger `folders`, re-import in place, undo.
- **CLI and validate (EDT-70):** tests for the sample builders and the validate rules.

## 11. Reviews

- **skate-render:** reviewed §4.1, §5 and the REN-31 slice (§11.1); its changes are in this revision.
- **skate-online:**
  - §6;
  - whether each provider's `ImportPlan` can say "model item", "texture set", "sound" or "font" without guessing;
  - one folder per model in multi-model packs;
  - ledger schema 2 without conversion;
  - the `ledgerFolder` trust rule.
- **skate-editor:**
  - §8 with ten tabs;
  - listing by type over the whole tree;
  - the "Show model textures" filter;
  - virtual engine entries in the AST-23 async cache;
  - where the update notification lives;
  - the material "Shader" field and the "New Shader" template (§5.6).
- **skate-tooling:**
  - §7;
  - samples in reference mode;
  - `skate new --engine-defaults`;
  - the validate rules;
  - folding the checkbox into the EDT-59 wizard;
  - `verify-game-jar.sh` and `processResources` (§4.1);
  - the MCP server and `engine:` references.

### 11.1 skate-render review

Verdict (2026-11-10, against `ShaderLoader`, `ShaderPreprocessor`, `ShaderHotReloader`, `ShaderVariantCache`,
`AssetsManager`, `RenderResourcesFactory`, `MaterialBinder`/`MaterialAsset`, `SortKey`, `GlslToVulkan`/`VkShaders` on
`integration/30g`): **agree with changes.**

| # | Request | Where it is applied |
|---|---|---|
| CR1 (blocking) | An unchanged shader copy follows the engine and is refreshed on open; `@skate-contract <n>` headers; an older modified override falls back to the engine shader with an error | §5.1, §5.2, NQ7 |
| CR2 | `GlslToVulkan.convert` validation on load and in `skate validate`; `docs/shaders.md` | §5.4 |
| CR3 | One cache keyed by `(reference, defines)`; fall back to the engine version of the same reference, never to `shader_3d_default` | §5.3 |
| CR4 | Include resolver `(name, includerId)`; dependency set keyed by resolved id; explicit `engine:` includes | §5.3 |
| CR5 | Reload re-resolves; drop `shaderDevRoot`; watch dependency files plus registry events, not all of `Assets/` | §5.5 |
| CR6 | Per-program frame uniforms; fill the `SortKey` shader field | §5.6, REN-31 row |
| CR7 | v1 changes the colour pass only; documented | §5.6 |
| CR8 | Feature opt-in by `#ifdef` scan; fallback per draw | §5.6 |
| CR9 | Line map for errors; "New Shader" template | §5.3, §5.6 |
| CR10 | Shader sets as a guarded catalog; shader move in its own commit, announced to skate-render; tests keep the `/shaders/` classpath | §4.1, §5.1 |
| NQ5 | 13 basic + 6 internal ("internal", not "editor-only": they ship in exported games) | §5.1 |
| NQ6 | Parameters as annotations in the GLSL file → a `MaterialSchema` `shader:<reference>` | §5.6 |
| Missing | Uniform contract check on assignment; Vulkan start-up compile cost (SPIR-V cache later, VK) | §5.4, §5.6 |

## 12. Open questions of revision 2 (all answered in §0.3)

1. **NQ1 — editor-only resources in the repo:** `strings/`, `themes/` and `help/` are not project asset types.
   Recommended: `assets/Editor/{strings,themes,help}`, never copied into projects; the engine's **game** string tables
   (`Assets.Strings.GAME_STRINGS`, packed by `AssetsPacker`) go to `assets/Data/strings/game/`.
2. **NQ2 — skater configs:** `configs/skater.character.json` and `configs/skater.controller.json` are classpath configs
   with a working-directory override. Recommended: a follow-up task (ANI/GPL-owned) makes them
   `engine:Data/skater.character.json` and `engine:Animations/skater.controller.json` under the same override rule;
   not part of AST-27.
3. **NQ3 — project strings file:** keep `Assets/strings.properties` at the root (recommended: the localisation loader
   expects it there, and it is one file), or move it to `Data/`?
4. **NQ4 — Font Awesome desktop zip** (`assets/fonts/fontawesome-free-7.1.0-desktop.zip`, a source archive):
   recommended: move to `tools/fonts/`, out of the assets tree.
5. **NQ5 — the shader sets:** answered by skate-render (§5.1, §11.1): 13 basic, 6 internal. No longer open.
6. **NQ6 — custom shader parameters:** answered by skate-render: a later REN task using GLSL annotations (§5.6). No
   longer open.
7. **NQ7 — unchanged shader copies update silently:** skate-render asks for one exception to "nothing updates
   silently": an unchanged copy of an engine shader (hash equal to `engine.sha256`) follows the engine and is
   refreshed when the project opens, because a stale shader breaks rendering after an engine update. Recommended:
   accept (only files with no user edits are touched).
8. **NQ8 — user shaders in the colour pass only (v1):** a custom material shader does not affect shadows or picking,
   so vertex-moving shaders show a mismatched shadow. Recommended: accept for v1, with an optional `shadowShader` as a
   later REN task.
