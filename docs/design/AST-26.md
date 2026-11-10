# AST-26: Typed asset folders (design note)

Status: **revision 2, awaiting the human's approval.** The human answered the nine open questions of revision 1 on
2026-11-10 (§0.2). No code until the note is approved.
Implements in: AST-27 (skate-assets), AST-28 (skate-online), REN slice (skate-render, §5), EDT-69 (skate-editor),
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
  Data/         (empty for now; skater configs: NQ2)
  Editor/       strings/, themes/, help/   (editor-only, never copied into projects; NQ1)
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
- `assets/fonts/fontawesome-free-7.1.0-desktop.zip` is a source archive, not an asset. It moves to
  `tools/fonts/` (NQ4).
- **Build and scripts:**
  - `build.gradle.kts` `processResources` copies `assets/Shaders/**` into the jar at `/shaders/`, so the editor and
    the game jar keep a classpath copy (`Assets.Fonts.FONTS_FILE` and `/textures/default.png` are classpath resources
    already and stay).
  - `tools/scripts/verify-game-jar.sh` gets a step that checks `skate-game.jar` contains every shader of
    `EngineDefaults.SHADERS` under `/shaders/`.
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
  - the **shaders** (§5): every game-pipeline shader is an engine default;
  - the editor-only shaders (`editor_grid`, `picking`, `shader_3d_picking`, `debugLine2D`, `splash`) stay internal,
    with no override (NQ5).

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
- Shaders are always packed (§5.4).

## 5. Shaders (decision 3; REN slice, reviewed by skate-render)

### 5.1 Engine shaders are engine defaults

- `assets/Shaders/*.glsl` (moved from `src/main/resources/shaders`; also in the jar at `/shaders/`, §4.1) are
  `engine:Shaders/<name>.glsl`.
- `Assets.Shaders` constants and the string literals in `RenderResourcesFactory`, `PickingRenderer` and
  `ThumbnailRenderer` become `EngineDefaults.Shaders.*` references.
- `AssetsManager.getShader` and the `ShaderVariantCache` key on the **reference**, not on `File(path).absolutePath`.

### 5.2 Basic shaders are always copied

Every project gets the game-pipeline shaders in `Assets/Shaders/`, with `engine.*` keys, in both copy modes:

- `shader_3d_default`, `shader_3d_texture`, `shader_2d_batch`
- `shadow`, `skybox`, `skydome`, `particles`
- `fog` (include)
- `ibl_capture`
- `post_bloom`, `post_tonemap`, `post_gamma`, `post_fxaa`, `post_ssao`

Together these are `EngineDefaults.BASIC_SHADERS` (NQ5). The editor-only shaders are not copied and cannot be
overridden.

### 5.3 Resolution

`ShaderLoader` gets a `ShaderSourceResolver` instead of `devSourceRoot` + `getResourceAsStream`. The resolver uses the
same `EngineDefaultResolver` as other assets. For a reference:

1. `engine:Shaders/X`: the project override (any file with `engine.source = Shaders/X`), else
   `<engineRoot>/assets/Shaders/X` (dev runs), else classpath `/shaders/X`. In an exported game: the pack entry, else
   the classpath.
2. A project shader (`Assets/Shaders/my_water.glsl`): the file, or its pack entry.

`#include "fog.glsl"` in `ShaderPreprocessor` resolves relative to the including shader first (project folder or pack),
then as `engine:Shaders/fog.glsl` through rule 1. So overriding an include changes every shader that includes it. The
preprocessor records each program's resolved files, which are its **dependencies**.

### 5.4 Hot reload

- `ShaderHotReloader` watches resolved files instead of one source root:
  - every file in a loaded program's dependency set;
  - the project's `Assets/` tree for `.glsl` files that are created or deleted, because a new or removed override
    changes what a reference resolves to.
- It can reuse `AssetReloader`'s stable-for-two-polls rule. Polling stays on the GL thread
  (`AssetsManager.pollShaderChanges`).
- On a change, the programs and variants whose dependencies changed are recompiled in place (`Shader.replaceWith`).
  This works on both backends: `RhiDevice.createShader` compiles GLSL at run time on OpenGL, and through
  `GlslToVulkan` + shaderc on Vulkan.
- A failed compile keeps the previous program, logs the stage error with the file, and shows it in the editor console
  (as today).
- Dev runs from the repo still pick up edits to `assets/Shaders/` (engine sources), with no special case: rule 1, step 2.
- Export: `FileType.SHADER` joins `AssetsPacker.alwaysPacked`, so overrides and user shaders ship without being
  referenced from a scene. Dedupe keeps the cost at one copy each.
- The exported game loads `engine:Shaders/*` from the pack (the project's copies) and falls back to the jar.

### 5.5 User shaders on materials

- `MaterialAsset` gets `shader: String? = null`, a reference to a project shader or `engine:Shaders/…`. A null shader
  means the schema's shader, as today. `MaterialAsset.CURRENT_VERSION` is bumped, with a migration test: an older
  file reads as `shader = null`. That is file-format versioning, not project migration.
- A custom shader must accept the uniforms and samplers of the material's `MaterialSchema`. For `pbr-metallic-roughness`
  those are the ones `shader_3d_default` uses. It is compiled with the same `ShaderFeature` defines (variants) as the
  default.
- If a variant fails to compile, that material draws with the schema's default shader and an error is logged once.
- Shader-declared parameters (custom uniforms shown in the inspector) are a later task (NQ6).
- The renderer picks the program per material (`MaterialBinder` / the 3D pass): REN decides where.
- The material inspector gets a "Shader" field that lists `.glsl` files in the project plus the engine shaders. That
  is an EDT-owned widget; it ships in the REN slice with an EDT review.

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
| **REN-xx** (new) | skate-render | §5: `ShaderSourceResolver` in `ShaderLoader`/`ShaderPreprocessor`, reference-keyed shader and variant caches, dependency-based `ShaderHotReloader`, `EngineDefaults.Shaders` in `RenderResourcesFactory`/`PickingRenderer`/`ThumbnailRenderer`, `MaterialAsset.shader` (version bump) and per-material program selection with fallback, the material inspector "Shader" field (EDT review). | AST-27b |
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
- **Shaders (REN):**
  - the resolver order (override > engine file > classpath; pack > classpath);
  - an `#include` override applies to its includers;
  - a dependency change marks exactly the programs that include it;
  - a failed recompile keeps the old program (fake device);
  - `MaterialAsset` v-old → `shader = null`;
  - a material shader that fails a variant falls back to the default.
- **Store (AST-28):** fixture tests per provider for target folders and ledger `folders`, re-import in place, undo.
- **CLI and validate (EDT-70):** tests for the sample builders and the validate rules.

## 11. Reviews

- **skate-render:** asked through herdr to review §5 and the REN-xx slice. Its answer is recorded in §11.1 when it
  arrives.
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
  - the material "Shader" field (§5.5).
- **skate-tooling:**
  - §7;
  - samples in reference mode;
  - `skate new --engine-defaults`;
  - the validate rules;
  - folding the checkbox into the EDT-59 wizard;
  - `verify-game-jar.sh` and `processResources` (§4.1);
  - the MCP server and `engine:` references.

### 11.1 skate-render review

(pending)

## 12. New open questions (with recommended answers)

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
5. **NQ5 — the shader sets:** recommended: the 14 game-pipeline shaders of §5.2 are the "basic shaders" (always
   copied, overridable); the 5 editor-only shaders stay internal and cannot be overridden. skate-render may adjust the
   list.
6. **NQ6 — custom shader parameters:** v1 user shaders must use their material schema's uniforms. Recommended: shaders
   that declare their own parameters (custom schema, inspector fields) are a separate REN task.
