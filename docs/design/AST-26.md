# AST-26: Typed asset folders (design note)

Status: **draft, awaiting the human's approval.** No code until it is approved.
Implements in: AST-27 (skate-assets), AST-28 (skate-online), EDT-69 (skate-editor), EDT-70 (skate-tooling).

## 0. Human decisions (2026-11-10)

- `Assets/` holds one folder per type: `Models`, `Shaders`, `Sounds`, `Textures`, `Materials`, `Scripts`, `Animations`.
- **No `EngineDefaults` folder anywhere.** Engine defaults that are copied go straight into `Assets/<Type>/`.
- A New Project setting chooses whether the engine defaults are copied.
- Asset Store imports go to their type folder, not `Assets/Store`. A store model stays together in
  `Assets/Models/<asset>/` (`.gltf`, `.bin`, `textures/`). Texture-only and material downloads go to `Textures/` and
  `Materials/`.
- The Asset Browser gets one tab per type (EDT-69).
- **New projects only.** No migration of existing projects, no backup or undo for a migration, no support for the old
  layout (`Assets/EngineDefaults/…`, `Assets/Store/…`). Projects made before AST-27 are unsupported and must be recreated.

## 1. Current state (what this changes)

- `ProjectLayout` (AST-14): `Assets/` with `EngineDefaults/{Characters,Models,Shaders,Sounds,Textures}`, plus `Scenes/`
  and `Builds/` next to it.
- `Assets.Bundled` holds the project-side paths (`EngineDefaults\Models\cube.obj`, Windows separators) and
  `bundledAssets` pairs each one with its engine source (`assets/obj/cube.obj`, `assets/characters/…`, relative to the
  editor's working directory). `EngineAssetCopier` copies them when a project is created. It also copies 10 shaders from
  the classpath and the app icon from the jar.
- Code builds references as `projectAssetsDir + Assets.Bundled.X`: `PrefabsGenerator`, `PrefabCatalog`, `MaterialType`,
  `FeatureShowcase`, the CLI sample builders and `PrefabThumbnailMaterial`. The saved scenes hold the project-relative
  form (`Assets/EngineDefaults/Models/cube.obj`, AST-09).
- **The copied shaders are dead.** The renderer loads shaders from the classpath (`Assets.Shaders`), never from the
  project.
- Store imports (AST-16, on `integration/30g`) go to `Assets/Store/<provider>/<slug>/` (`StorePaths.importDir`). The
  ledger entry has one `folder`, and `StoreImporter.ledgerFolder` only trusts folders under `Assets/Store/`. Imported
  files get `store.provider`, `store.sourceId` and `store.license` in their `.meta` `settings`.
- `AssetsPacker` + `ReferenceCollector` pack the project files that are reachable from the scenes, the prefabs and the
  project file. For a model, the collector also packs **every image in the model's folder** (`modelCompanions`) and
  follows glTF URIs (AST-22).

## 2. Layout of a new project

```
<project>/
  <name>.skateproject
  Assets/
    Models/        Animations/   Textures/   Materials/
    Sounds/        Scripts/      Shaders/    Prefabs/     (Prefabs: open question Q1)
    strings.properties
  Scenes/
  Builds/
  .skate/          hidden: attribution ledger, store staging (AST-16)
```

- `ProjectLayout.create` makes every type folder, even when it is empty, so the layout shows from the start.
- `ProjectLayout` gets `enum class AssetFolder(val dirName: String)` (one entry per type folder) and
  `typeDir(projectDir, folder)`. `ENGINE_DEFAULTS_DIR` and `engineDefaultsDir` are removed.
- `Scenes/` stays outside `Assets/`, as it is today. `strings.properties` stays at the root of `Assets/`.
- The type folders are where **new** files land. Nothing forces a file to stay there: the user may make subfolders or
  move files, and references keep working (by GUID or by `AssetRegistry.move`).

## 3. Which folder a file lands in

One function decides, `AssetFolders.folderFor(file): AssetFolder?` (engine/assets). Drag-and-drop into the Asset
Browser, "Import…", store imports, Blender imports and the engine-default copier all use it. `null` means the root of
`Assets/`.

| File | Folder | Notes |
|---|---|---|
| `.gltf` (+ `.bin`, images), `.glb`, `.obj` (+ `.mtl`), `.dae` | `Models/` | A multi-file model gets its own folder, `Models/<asset>/` (§3.1). |
| `.fbx` | `Models/` or `Animations/` | `FbxClassifier` (AST-20): meshes → `Models/`, animation-only → `Animations/`. A file with both counts as a model with clips. |
| `.blend` | `Models/` | Its generated outputs stay next to it (§3.2). |
| `.skanim` | `Animations/` | AST-21. |
| `.png .jpg .jpeg .tga .bmp .hdr …` (`FileType.TEXTURE`) | `Textures/` | A texture set gets its own folder, `Textures/<set>/` (§3.1). |
| `.skmat`, `.mat`, `.material` | `Materials/` | |
| `.wav .ogg .mp3` | `Sounds/` | |
| `.kts .kt .java` (later `.ts`, `.lua`) | `Scripts/` | |
| `.glsl .vert .frag .vs .fs` | `Shaders/` | |
| `.prefab` | `Prefabs/` | Q1. |
| `.scene` | `<project>/Scenes/` | Unchanged. |
| fonts, `.json` configs, animator controllers, anything else | root of `Assets/` | Q2. |

### 3.1 Multi-file assets and AST-22

AST-22 resolves a model's textures and buffers **relative to the model file**. A multi-file asset must therefore never
be split across type folders:

- **A model with companions** (`.gltf` + `.bin` + `textures/`, `.obj` + `.mtl` + maps, a store download) is placed as
  a whole folder: `Assets/Models/<asset>/…`, internal layout unchanged. Its textures stay in `Models/<asset>/textures/`,
  not in `Textures/`. This is the human's store rule, applied to every import.
- **A texture set** (ambientCG, Poly Haven texture downloads: albedo/normal/roughness/…) goes to
  `Assets/Textures/<set>/`. Its generated `.skmat` goes to `Assets/Materials/<set>.skmat`. A `.skmat` stores
  project-relative texture paths (`Assets/Textures/<set>/<map>.png`, written by `AssetPaths.relativize`), so it does not
  need to sit next to its textures.
- **A single file** (a `.glb`, a texture, a sound) goes into the type folder itself, with no subfolder.
- `<asset>` is `StorePaths.slug` of the source name. When a folder or file by that name already exists, add a suffix
  `_2`, `_3`… (the rule `StoreImporter.freshTargetDir` already uses).
- Packing: `ReferenceCollector` today packs **every image in a model's folder**. With a flat `Models/` folder that would
  ship unrelated images. AST-27 changes this:
  - `.obj` follows `mtllib` and the `.mtl`'s `map_*` lines.
  - `.gltf` follows its URIs (already does).
  - `.glb` is self-contained.
  - `.dae` keeps the sibling rule, but only inside a `Models/<asset>/` subfolder, never in `Models/` itself.

### 3.2 `.blend` imports (AST-19)

Unchanged in shape:

- `Assets/Models/Board.blend`
- `Assets/Models/Board.blend.import/` with `Board.glb` and `Board@<Action>.glb`

The generated `Board@Ollie.glb` clips stay in the `.import/` folder (they are outputs of the `.blend`, rewritten in
place to keep GUIDs). They are **not** moved to `Animations/`; the Animations tab finds them by type (§7). A `.blend`
dropped into another folder imports where it was dropped, as today.

## 4. Engine defaults

### 4.1 Catalog and the `engine:` reference

`Assets.Bundled` is replaced by `EngineDefaults` (a Kotlin catalog object; there is no `EngineDefaults` folder). Each
entry has:

- a **logical path** in the typed layout, which is also the reference, e.g. `engine:Models/cube.obj`
- its source: a file under the engine's `assets/` folder, or a classpath resource (app icon)

| Logical path | Source today |
|---|---|
| `engine:Models/<prefab>.obj` (cube, sphere, rail, … skateboard) | `assets/obj/<prefab>.obj` |
| `engine:Models/james.glb` | `assets/characters/james.glb` |
| `engine:Animations/<clip>.fbx` (18 clips) | `assets/characters/animations/<clip>.fbx` |
| `engine:Textures/{asphalt,concrete_simple,metal,wood_*,sky_hdri}.png` | `assets/textures/…` (the `wood_*` names stay as in projects today) |
| `engine:Textures/app_icon.png` | classpath `/app_icon.png` |
| `engine:Sounds/Music/aquarela do brasil.mp3` | `assets/sounds/music/…` |

- The engine's own `assets/` folder in the repo is **not** reorganised; the catalog maps the paths (Q5).
- Engine shaders are not in the catalog (Q3).
- **Code** refers to engine defaults only by `EngineDefaults.CUBE` = `"engine:Models/cube.obj"`.
  `PrefabsGenerator.projectAssetsDir` and every `projectAssetsDir + Bundled.X` go away.
- **Rules for the reference:**
  - Prefix `engine:`, then a `/`-separated path, with no `..` and no drive.
  - A colon can never be a project path, so `ReferenceCollector` and `AssetPaths` can tell it apart without touching
    the disk.
  - Every resolver checks the prefix **before** building a `File`. On Windows, `File("engine:x")` would otherwise probe
    an NTFS stream.
- **GUIDs:** `AssetRegistry.idOf("engine:…")` returns a GUID derived from the logical path (name-based UUID, stable
  across machines). GUID-based APIs and `AssetRegistry.resolve` therefore accept engine defaults whether or not they
  are copied.

### 4.2 The two modes (EDT-70 setting "Copy engine default assets")

| | Copy (recommended default, Q4) | Do not copy |
|---|---|---|
| On creation | Each catalog entry is copied to `Assets/<Type>/…` (its logical path under `Assets/`), with a `.meta` that marks it as an engine copy (§4.4). | Nothing is copied. The type folders are empty. |
| References in the default scenes and the project | `engine:…` | `engine:…` (the same) |
| `iconPath` | `engine:Textures/app_icon.png` | the same |

**References are the same in both modes.** A project copy of a default is an **override**: wherever it is in `Assets/`,
the copy whose `.meta` has `engine.source = Models/cube.obj` is what `engine:Models/cube.obj` resolves to.

- In copy mode, editing `Assets/Models/cube.obj` changes the game, as the human expects.
- Deleting it falls back to the engine's file.
- Moving or renaming it keeps it in use.

The mode is therefore a **creation-time action, not a project setting**: it is not stored in `.skateproject`, so the
project file schema does not change. A not-copied project can copy a default later from the Asset Browser ("Copy into
project", §7). The copy becomes the override without any reference being rewritten.

### 4.3 Resolution in the editor

`AssetPaths` gets an `EngineDefaultResolver`, which needs the project's `AssetRegistry` and the engine root.

- `absolute("engine:X")` gives:
  1. the project file whose `.meta` has `engine.source = X`, if there is one (the registry keeps an index
     `engine.source → path`, built in `scan` and kept up to date by `register`, `move` and `delete`);
  2. otherwise the engine's source file (`<engineRoot>/assets/…`). For a classpath resource, a copy extracted once to
     the cache folder `AssetPaths` already has.
- `relativize(path)` gives:
  - `engine:X` when `path` is an engine source file, or a project file whose `.meta` has `engine.source`;
  - otherwise the project-relative form, as today.

  The round trip load → save keeps `engine:` references, and does not turn them into absolute engine paths. A guard test
  covers that round trip.
- Every loader that already goes through `AssetPaths.absolute` / `localPath` / `readBytes` gets this for free. AST-27
  greps for loaders that build `File(path)` directly (`AssetsManager.getTexture`, the `AssimpLoader` entry points) and
  routes them through `AssetPaths`.
- `engineRoot` is `user.dir`, as for `EngineAssetCopier` today. A missing engine source shows the missing-asset
  placeholder and logs an error with the logical path.

### 4.4 Telling engine copies apart (updates, name clashes)

A copy's `.meta` `settings` (free-form map, so `AssetMeta` stays at schema 1):

| Key | Value |
|---|---|
| `engine.source` | logical path, e.g. `Models/cube.obj` |
| `engine.sha256` | hash of the engine file at copy time |

From those two keys:

- **Unchanged copy:** the copy's hash equals `engine.sha256`.
- **Modified copy:** the hashes differ.
- **Update available:** the engine source's hash differs from `engine.sha256`.
  - When the copy is unchanged, the editor shows one notification when the project opens: "N engine defaults have
    newer versions — Update". Update overwrites them and records the new `engine.sha256`.
  - When the copy was modified, it is never overwritten. It is listed as "modified, engine has a newer version", with a
    per-file "Replace with engine version" action (the editor's undo covers it, through `DeleteFileCommand`-style
    backups).
- Nothing updates silently.
- **Name clash on copy:** the target `Assets/Models/cube.obj` exists.
  - It is already the override for this logical path → skip, as today.
  - Otherwise the user's file wins and the copy is named `cube (engine).obj`. Lookup goes by `engine.source`, so the
    name does not matter.
- **Duplicating a copy** in the editor drops the `engine.*` keys, so the duplicate is an ordinary asset.
- **Two files with the same `engine.source`** (copied outside the editor along with the `.meta`): the first by path
  wins. `skate validate` warns.
- A catalog entry is never removed or renamed without an alias in the catalog. A guard test checks that every source
  exists (today's `missingSources`) and that every logical path is unique.

### 4.5 Export

- `ReferenceCollector.resolve` handles the `engine:` token itself: it resolves through `EngineDefaultResolver` (override
  file or engine source) and returns a pack input whose **pack path is the reference itself** (`engine:Models/cube.obj`).
  The engine source is outside the project folder, so this cannot go through today's "inside the project root" check.
  The packer gets a small `PackItem(source: File/bytes, packPath)` instead of a bare `File`.
- At runtime, `AssetPaths.readBytes("engine:…")` finds that entry in the pack. `localPath` extracts it (the existing
  extractor already strips `:`).
- An override also referenced by its project path is packed twice. A later improvement: dedupe by `sha256` in
  `PackBuilder` (Q6). No pack format change is needed.
- Engine defaults that nothing references are not packed. "Pack everything" (`onlyReferenced = false`) adds only the
  project's files, as today.
- `ProjectExporter` resolves `iconPath` through `AssetPaths`, so an `engine:` icon works.
- Credits: engine defaults carry no store ledger entry. The bundled music's credit stays as today.

## 5. Store imports (AST-28, skate-online)

- `StorePaths.importDir(providerId, slug)` becomes `StorePaths.importTarget(plan)`:
  - **model item** (any plan with a model entry): `Assets/Models/<slug>/`, internal layout kept (`.gltf`, `.bin`,
    `textures/`). Kenney/Quaternius pack items are each a model; a pack import makes one folder per imported model.
  - **texture set** (ambientCG, Poly Haven textures): maps in `Assets/Textures/<slug>/`; `.skmat` in
    `Assets/Materials/<slug>.skmat`.
  - **single texture / HDRI**: `Assets/Textures/<slug>.<ext>`.
  - **sound**: `Assets/Sounds/<slug>.<ext>`, or `Assets/Sounds/<slug>/` for several files.
- The provider no longer appears in the path (it was `Store/<provider>/`). The provider, source ID and license stay in
  the `.meta` (`store.provider`, `store.sourceId`, `store.license`), unchanged.
- **Ledger:** an item can now span two folders (texture set + material).
  - `LedgerEntry.folder` becomes `folders: List<String>`, ledger `schema` 2.
  - Per the human's no-backward-compatibility rule, there is **no** schema-1 migration: a schema-1 ledger is reported
    as unsupported, not converted (Q7 confirms this applies to the ledger).
  - `ledgerFolder` trusts a folder only when it resolves inside one of this project's type folders. The current check
    only allows `Assets/Store/`.
- Re-import in place uses `folders` from the ledger. Undo (`StoreImporter.revert`), backups, `AST-25` delete → ledger,
  and GUIDs kept across re-imports work unchanged. They are keyed by file, not by folder.
- `.skmat` written by the importer keeps project-relative texture paths (`AssetPaths.relativize`, as today).

## 6. Samples, CLI, `skate validate`, export (EDT-70 / skate-tooling, AST-27)

- **CLI `new` and the sample builders** (Tetris, Marble Run, Showcase): `ProjectLayout.create` + `EngineDefaults`
  references (`engine:Models/cube.obj`); `copyEngineAssets` only when copying. `skate new` gets
  `--engine-defaults copy|reference` (default per Q4). Samples are generated in **reference** mode: smaller, and they
  show that mode works. Tetris and Showcase scripts go to `Assets/Scripts/`, as now. Showcase materials go to
  `Assets/Materials/`.
- **`skate validate`:**
  - **errors:** an `engine:` reference whose logical path is not in this engine's catalog; an engine source missing
    from the engine install.
  - **warnings:**
    - a missing type folder
    - two overrides for one logical path
    - modified engine copies whose engine version changed
  - **unchanged:** today's checks (`duplicateAssetFolders`, scenes, default scene).
  - It does **not** look for the old layout (no backward compatibility).
- **`skate pack` / `export`:** no flag changes. They go through §4.5.
- **New Project dialog** (EDT-70): a "Copy engine default assets" checkbox with a tooltip that explains both modes
  (§4.2). `ProjectManager.prepareProjectFolder(name, folder, copyDefaults)` gets the flag.

## 7. Asset Browser (EDT-69, skate-editor)

- Tabs: Models, Animations, Textures, Materials, Sounds, Scripts, Shaders, plus Prefabs (Q1).
- **A tab lists by type, over the whole `Assets/` tree, not by folder**, using `FileTypeResolver` + `FbxClassifier`
  (AST-20) + the `@` rule for `.blend` clips. So:
  - a file the user moved still shows;
  - `Board@Ollie.glb` in `Models/Board.blend.import/` shows under Animations.
- Files inside a multi-file model folder (`Models/<asset>/textures/*.png`) are hidden from the Textures tab by default
  (they belong to the model), with a filter "Show model textures". Q8 for skate-editor.
- Engine defaults are listed **whether copied or not**:
  - not copied: a virtual entry with an "Engine" badge, resolved through `EngineDefaultResolver`. Context menu: "Copy
    into project" (copies to `folderFor`, writes the `engine.*` keys).
  - copied: an "Engine copy" badge, plus "Modified" / "Update available" (§4.4). Context menu: "Replace with engine
    version".
- Drop and import into a tab place the file with `folderFor`, whichever tab is open.
- The shared search, empty states and context menu of EDT-52C apply to every tab.

## 8. Slicing

| Task | Owner | Contents | Deps |
|---|---|---|---|
| **AST-27** | skate-assets | `ProjectLayout`/`AssetFolder`, `AssetFolders.folderFor`, `EngineDefaults` catalog replacing `Assets.Bundled`, `engine:` resolution in `AssetPaths` + `AssetRegistry` (index by `engine.source`, derived GUIDs), copier with `engine.*` meta and name-clash rule, update detection (service + tests; the notification UI belongs to EDT-69), `ReferenceCollector`/`AssetsPacker` packing of `engine:` items and narrowed model companions (§3.1), `PrefabsGenerator`/`PrefabCatalog`/`MaterialType`/`FeatureShowcase`/`PrefabThumbnailMaterial` on `engine:` references, `ProjectManager` creation with the copy flag. NEEDS TLG for the CLI builder lines if they are not lead-assigned to me. | AST-26 |
| **AST-28** | skate-online | §5: `importTarget`, ledger schema 2 (`folders`), `ledgerFolder` trust rule, provider fixture tests. | AST-27, AST-16 |
| **EDT-69** | skate-editor | §7: tabs by type, virtual engine entries, badges, "Copy into project", "Replace with engine version", update notification, drop via `folderFor`. | AST-27 |
| **EDT-70** | skate-tooling | §6: New Project checkbox (or EDT-59 wizard step), `skate new --engine-defaults`, samples in reference mode, `skate validate` rules. | AST-27 |

Inside AST-27, possible split if it is too large: (a) layout + catalog + resolution + creation; (b) packing + update
detection.

## 9. Tests (focused unit tests, no integration tests)

- `folderFor`: every row of §3, including FBX model vs animation fixtures (AST-20 fixtures), and `.blend`.
- `ProjectLayout.create` makes every type folder and no `EngineDefaults`.
- Copier: copy mode writes every catalog entry to `Assets/<Type>/…` with `engine.source` / `engine.sha256`; reference
  mode copies nothing; name clash → `cube (engine).obj`.
- `AssetPaths`: `engine:` → engine file without an override, → the override with one (moved or renamed too); `relativize`
  maps both back to `engine:`; the JSON round trip of a scene keeps `engine:`; no `File()` is built for an `engine:`
  string.
- Registry: derived GUID is stable; `engine.source` index follows `move`/`delete`; duplicate overrides → first by path.
- Update detection: unchanged / modified / update available, from fixture hashes.
- Packing: a reference-mode project packs `engine:Models/cube.obj` from the engine source; a copy-mode project packs
  the override's bytes under the same key; the exported game's `AssetPaths.readBytes` reads it; an `.obj` packs its
  `.mtl` maps but not unrelated images in `Models/`.
- Sample builders and `skate validate`: TLG tests (EDT-70).
- Store: AST-28 fixture tests per provider (target folders, ledger `folders`, re-import in place, undo).

## 10. Reviews

I did not prompt the other agents directly. Under the herdr protocol the lead reads each agent's last output for its
status line, and a review request from me would overwrite it. The lead should dispatch these checks.

- **skate-online:**
  - §5 overall;
  - whether every provider's `ImportPlan` can say "model item" vs "texture set" vs "sound" without guessing;
  - Kenney/Quaternius packs with several models from one archive (one folder per model, shared textures duplicated or
    not?);
  - ledger schema 2 with `folders` and **no** schema-1 conversion;
  - the `ledgerFolder` trust rule;
  - the SimReady/USD slice (e) landing in `Models/<slug>/`.
- **skate-editor:**
  - §7 overall;
  - tabs by type over the whole tree vs. by folder;
  - hiding model-internal textures (Q8);
  - virtual engine entries and badges in the AST-23 async discovery/cache;
  - keeping the Prefabs tab (Q1);
  - where the update notification lives.
- **skate-tooling:**
  - §6 overall;
  - the samples generated in reference mode;
  - the `skate new --engine-defaults` flag and its default;
  - the `skate validate` rules;
  - folding the checkbox into the EDT-59 wizard;
  - the MCP server exposing `engine:` references to tools without surprising them.

## 11. Open questions for the human

1. **Prefabs:** `.prefab` is not in your type list. Add `Assets/Prefabs/` (recommended; the Asset Browser keeps its
   Prefabs tab), or put prefabs at the root of `Assets/`?
2. **Other files** (fonts, `.json` configs, animator controllers): root of `Assets/` (recommended), or more type
   folders (`Fonts/`, `Data/`)?
3. **Engine shaders:** the 10 copied shaders are never used (the renderer loads them from the classpath). Recommended:
   stop copying them; `Shaders/` is for the user's own shaders. Or keep copying them as read-only examples?
4. **Default for "Copy engine default assets":** on (recommended: the project is self-contained, works with another
   engine version, commits cleanly to git) or off (smaller project, picks up engine updates)?
5. **Engine repo `assets/` folder:** keep its current layout and map it in the catalog (recommended, no churn), or
   reorganise it into the same typed folders?
6. **Duplicate pack entries** when an override is referenced both as `engine:` and by its project path: accept for now
   (recommended), or require sha256 dedupe in `PackBuilder` within AST-27?
7. **Ledger schema 2:** confirm the no-backward-compatibility rule also means a schema-1 attribution ledger is not
   converted (only projects made before AST-27 have one).
8. **Model textures in the Textures tab:** hidden by default with a filter (recommended), or always listed?
9. **Name clash** when copying a default: name the copy `cube (engine).obj` (recommended), or skip the copy and keep
   using the engine's file?
