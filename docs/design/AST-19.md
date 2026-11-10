# AST-19 — Full Blender support

Status: **approved** (human, 2026-11-07) and implemented for steps 19a–19d (FILES mode). Author: `skate-assets`.
Paths are relative to `src/main/kotlin/com/pafoid/skate/`.

Marker: **⚠ UNVERIFIED** = a Blender behaviour or API detail I could not check against a running Blender or its docs on
2026-11-07 (no Blender is installed on the agent machine). Each one gets a spike before the step that depends on it.

## 0. Decisions and spike results (2026-11-07)

Human decisions: generated GLBs are **committed**; minimum Blender **4.2 LTS**; an output no longer produced (action
deleted in Blender) is **kept with a warning**; the "Send to Skate" add-on comes **later**; action mode default
**FILES** (lead).

Spikes run on Blender **5.2.3 LTS** with `baseball-cap.blend` (human's file) and a generated two-action rig. They
resolve the ⚠ UNVERIFIED markers below:

| Item | Result |
|---|---|
| `--factory-startup` and auto-run | `bpy.app.autoexec_fail` false and `use_scripts_auto_execute` **false** with `--factory-startup`: embedded Python does not run. |
| `--python-exit-code 1` | A Python exception and a missing `.blend` both exit with 1. |
| Registry | `HKCR\.blend` default = ProgID (`blender.5.2`); `HKCR\<ProgID>\shell\open\command` names `blender-launcher.exe`; `blender.exe` sits next to it. The `blendfile` key in §3 does not exist. |
| Save files | A save writes `x.blend@` then renames it; `x.blend1` is the backup. Both are ignored by the watch and by `AssetRegistry`. |
| glTF options | `export_animation_mode` has `ACTIONS`, `ACTIVE_ACTIONS`, `BROADCAST`, `NLA_TRACKS`, `SCENE`; `export_image_format` has `AUTO`, `JPEG`, `WEBP`, `NONE`. The script passes only options the running Blender has. |
| Per-action files | Assigning the action (and, in Blender 5, its first slot) then exporting with `ACTIVE_ACTIONS` gives one clip per file; each file holds only its action's keys. |
| Units / axes | The cap exports in metres, Y-up (a +1 m Z move in Blender is +1 m Y in the GLB). |
| Materials | Principled BSDF with packed base color, normal, and metal/rough (through Separate Color) loads with all three textures in `AssimpLoader`. |
| Windows arguments | JSON passed as a command-line argument is mangled (quotes are not escaped by `ProcessBuilder`); the script reads its arguments from `args.json` in the staging folder instead. |

Implementation notes vs. the text below: the importer lives in `editor/assetimport/blender/**` and the shared
locator/runner in `editor/tools/external/**`; dropping a `.blend` (or any model) from the Project window on the viewport
spawns its GLB (`ViewportAction.DropModel`). Inspector settings UI, the "Needs Blender" badge and the settings page are
not done yet (EDT follow-up); results and warnings go to the console.

## 1. Goals / non-goals

Goals:
- Drop a `.blend` into `Assets/` and use it like any model: meshes, materials, skeleton and actions.
- Saving the `.blend` in Blender re-imports it, and the editor reloads it without a restart.
- References to the imported model survive re-imports (stable GUIDs).
- One Blender locator and process runner, shared with AST-16e (USD → glTF through Blender).
- Optional Blender add-on, "Send to Skate", for `.blend` files kept outside the project.

Non-goals (v1): reading `.blend` without Blender (the format is undocumented and changes per version), geometry
nodes/procedural textures beyond what the glTF exporter bakes, Blender physics, shape keys driving gameplay,
exporting back from Skate to Blender, and running Blender inside exported games (the runtime never sees `.blend`).

## 2. Current state (facts from the code)

| Area | Today | Impact on this design |
|---|---|---|
| Loaders | Assimp loads OBJ/FBX/glTF/GLB/DAE (`loaders/AssimpLoader.kt`). No `.blend` support (Assimp's old `.blend` importer is unmaintained and removed upstream). | We convert to **GLB** with Blender's own exporter and load the GLB through the existing path. |
| Materials | `AssimpLoader` maps base color/diffuse, normal, metal-rough, AO and emissive **textures**, base color, alpha mode/cutoff and two-sided. It does **not** read the metallic, roughness or emissive **factors**. | A metal or rough Principled BSDF with no texture comes in wrong. Fix in the loader (AST, step 19e). |
| Animations | `AnimationLoader.loadAnimations` returns every clip, but `AssetsManager.loadAnimationSync/resolveAnimation` keep only `[0]` (AST-01 D4). Clip name = file name (`AnimationLoader.clipName`). | A `.blend` with several actions would lose all but one. See §6. |
| Hot reload | `AssetsManager.pollHotReload` watches loaded textures, models and sounds by absolute path, with a two-poll stability check (`AssetReloader`). Animations are not watched. | Rewriting the generated GLB already reloads the model. The `.blend` itself needs its own watch (§5). |
| GUIDs | `AssetRegistry` (root = project folder) gives every non-hidden file a `.meta` sidecar (`AssetMeta`, schema 1, free-form `settings: Map<String,String>`). Hidden folders are skipped. | Generated files must live in a **visible** folder to get GUIDs (§4). Import settings fit in `settings`, so no schema bump is needed. |
| Packing | `ReferenceCollector` packs only files that scenes and prefabs reference. | A `.blend` is never referenced, so it is never packed. A guard test makes sure of that. |
| External tools | Only `ShellSession` uses `ProcessBuilder` (interactive shell panel). `tools/collada2gltf` is a manual tool. | A new process runner is needed (§3). |

## 3. Blender locator and process runner (shared with AST-16)

New package `editor/tools/external/**` (owner: `skate-assets`). Editor-only: exported games never contain it. AST-16e
uses the same classes for USD conversion.

```kotlin
data class BlenderInstall(val executable: File, val version: BlenderVersion, val source: Source)
enum class Source { SETTING, PATH, REGISTRY, DEFAULT_DIR, STEAM }

interface BlenderLocator {
    /** Every install found, best first: the user setting, then the newest supported version. */
    fun find(): List<BlenderInstall>
}

interface ProcessRunner {
    fun run(command: List<String>, workingDir: File, timeout: Duration, cancel: CancelToken,
            onLine: (String) -> Unit = {}): ProcessResult        // exit code, captured log, timedOut, cancelled
}
```

Order the locator searches (Windows first, since our natives are Windows-only):
1. `EditorSettings.blenderPath`, when it is set and the file exists.
2. `blender.exe` on `PATH`.
3. The registry: the `blendfile` open command under `HKEY_CLASSES_ROOT`. ⚠ UNVERIFIED: the exact key name for
   installer, MSIX/Microsoft Store and portable installs (the Store install may not register one).
4. `C:\Program Files\Blender Foundation\Blender <x.y>\blender.exe`, newest first.
5. Steam: `<steam library>\steamapps\common\Blender\blender.exe`, using the library folders file. ⚠ UNVERIFIED:
   parsing `libraryfolders.vdf` is worth it, or the default library is enough.

Each candidate is checked once with `blender --version` (fast, no UI), and the result is cached per editor session.
Minimum version: **Blender 3.6 LTS** (proposed, Q2). ⚠ UNVERIFIED: the exact glTF exporter options this needs (§4.2).

The runner always runs Blender **in the background, without the user's preferences and without auto-run
scripts**:

```
blender.exe -b --factory-startup --python-exit-code 1 <file.blend> --python <bundled script> -- <args>
```

- `-b` runs with no UI. `--factory-startup` ignores the user's add-ons and preferences so results are reproducible.
- **Security:** a `.blend` can embed Python (drivers, text blocks set to auto-run). We never pass `-y` /
  `--enable-autoexec`, so Blender keeps auto-run disabled. Only our bundled script runs. ⚠ UNVERIFIED: that
  `--factory-startup` also resets "Auto Run Python Scripts" to off when the user enabled it in their preferences
  (it should, since it skips the preferences).
- `--python-exit-code 1` turns a Python exception into a non-zero exit. ⚠ UNVERIFIED: available in 3.6 (I believe it
  exists since 2.8x).
- Timeout per job (default 120 s, a setting). On timeout or cancel, the process tree is killed
  (`ProcessHandle.descendants()`).
- One Blender job per `.blend` at a time. A save during a running job queues **one** follow-up job, and the newest save
  wins. At most two Blender processes run at once (a setting).
- Jobs run on `JobSystem.runIO`. Registry updates and GPU reloads happen on the main thread (domain rule).

The bundled script (`src/main/resources/blender/skate_export.py`) is copied to a temp folder at run time, because
Blender cannot read it from inside the jar.

## 4. `.blend` import

### 4.1 Where the files go

```
Assets/Models/Board.blend              source (user edits it in Blender)
Assets/Models/Board.blend.meta         importer "blender", import settings, output GUID list
Assets/Models/Board.blend.import/      generated, visible (so it gets GUIDs), rewritten on every import
    Board.glb                          meshes, materials, skin (and actions, §6)
    Board.glb.meta
    Board@Ollie.glb                    one per action when actionMode = FILES (§6)
    report.json                        warnings from the last import (§7)
```

- The folder is visible on purpose: `AssetRegistry` skips hidden folders, and generated files need GUIDs so scenes can
  reference them.
- Scenes and prefabs reference the **generated GLB's GUID**, never the `.blend`. Dropping a `.blend` on the viewport
  spawns its GLB.
- **Stable GUIDs:** a re-import writes into a temporary folder, then replaces the files one by one **in place**, so the
  existing `.meta` sidecars and GUIDs stay. A new output gets a new GUID. A removed one is deleted with
  `AssetRegistry.delete` (Q3: or kept and reported, if scenes still reference it).
- `AssetMeta.importerFor` gets `"blend" -> "blender"`. The output list is stored in the `.blend`'s
  `settings["blender.outputs"]` (comma-separated relative names), so the registry can find generated files without
  scanning.
- Generated folders are committed with the project by default, so teammates and CI without Blender can still open and
  export it (Q1).

### 4.2 Conversion

The script opens the `.blend` (already opened by the command line), reads the arguments after `--` and calls the glTF
exporter:

```python
bpy.ops.export_scene.gltf(
    filepath=out_glb, export_format='GLB',
    export_yup=True,              # Blender is Z-up; Skate is Y-up, right-handed, metres (conventions.md)
    export_apply=apply_modifiers, # import setting
    export_animations=export_actions,
    export_animation_mode=mode,   # 'ACTIONS' or 'ACTIVE_ACTIONS' / 'NLA_TRACKS' (§6)
    use_selection=False, use_visible=only_visible,
    export_image_format='AUTO',   # textures embedded in the GLB
)
```

⚠ UNVERIFIED: option names and defaults change between Blender versions. `export_animation_mode` appeared around 3.6/4.0,
and `export_colors` was renamed. The script checks `bpy.app.version` and passes only the options that version supports.
A unit test checks the argument builder, and a spike checks each option on 3.6 LTS, 4.2 LTS and the current release.

- Units: the glTF exporter writes metres. ⚠ UNVERIFIED: whether it applies the scene's `unit_settings.scale_length`.
  The script reports a scale other than 1.0 as a warning, and the import setting `blender.scale` can correct it.
- Linked libraries (`bpy.data.libraries`) and external textures resolve relative to the `.blend`. Missing files are
  listed in the report. Images packed into the `.blend` are embedded in the GLB.
- Before exporting, the script writes a JSON summary (objects, materials and their node types, actions, armatures,
  missing images) to `report.json`. The editor reads that file, not Blender's console output.

### 4.3 Import settings (per `.blend`, stored in its `.meta` `settings`)

| Key | Default | Meaning |
|---|---|---|
| `blender.applyModifiers` | `true` | Apply modifiers on export. |
| `blender.onlyVisible` | `false` | Skip hidden objects. |
| `blender.exportActions` | `true` | Export actions. |
| `blender.actionMode` | `FILES` | `FILES` (one GLB per action) or `EMBEDDED` (all in one GLB, §6). |
| `blender.scale` | `1.0` | Extra uniform scale. |
| `blender.autoReimport` | `true` | Re-import on save (§5). |

The inspector shows these fields when a `.blend` is selected, plus a **Re-import** button, the last import's time and
status, and the warnings from `report.json`. The settings are free-form `settings` keys, so this does not change the
`.meta` format and needs no schema bump.

## 5. Re-import on save

- The editor watches each `.blend` under `Assets/` with an `AssetReloader` entry (key = absolute path). It watches the
  `.blend` itself, not only loaded assets, and the two-poll stability check avoids reading a half-written file.
- Blender saves through a temporary file and keeps a backup. Discovery and watches ignore `*.blend1`..`*.blend32` and
  `*.blend@`. ⚠ UNVERIFIED: the exact temporary name (`file.blend@`) and that the final file is renamed into place.
- A change runs the import job (§3). When the GLB is replaced, the existing model hot reload
  (`AssetsManager.reloadModel`) updates scenes in place. Animations are not hot-reloaded today, so per-action clips
  reload through a new `reloadAnimation` hook (part of 19c).
- A status chip in the Asset Browser/inspector shows "Importing Board.blend…", then the result. Failures go to the
  console with the report and **keep the previous GLB**, so a broken save never breaks the open scene.
- When the editor opens a project, `.blend` files newer than their outputs (by modified time plus a source hash in
  `settings["blender.sourceHash"]`) are queued for import. A setting turns that off for large projects.

## 6. Actions → clips

Problem: one `.blend` usually has several actions, but `AssetsManager` keeps one clip per file and names it after the
file (AST-01 D4).

Proposal for v1, `actionMode = FILES` (default):
- `Board.glb` holds meshes, materials and skin, with no animations.
- `Board@<Action>.glb` holds the armature plus one action. The script exports each action in turn by assigning it to
  the armature and using `ACTIVE_ACTIONS`. ⚠ UNVERIFIED: the cleanest way per Blender version (NLA solo vs. assigning
  `animation_data.action`).
- `AnimationLoader.clipName` maps `Board@Ollie.glb` to the clip name `Ollie`. The Asset Browser lists `*@*.glb` as
  animations (`BrowserAssetType.ANIMATION`, a discovery rule in this domain).
- This fits the current one-clip-per-file loader, the Animations tab and controller clip references, with no ANI
  change.

`actionMode = EMBEDDED` needs multi-clip files (fix D4: `AssetsManager` caches every clip as `<file>#<clip>`). That is
step 19d, and it needs an ANI review of how controllers reference clips: `NEEDS ANI` when we get there.

Mapping rules:
- Bone names go through the existing `BoneNameMapper` (removes Mixamo prefixes), as for FBX.
- Actions with fake users or no keyframes are skipped and listed in the report.
- Root motion: exported as the root bone's motion. Whether a clip uses it stays an Animator setting (ANI).
- Shape-key animation is exported by Blender but ignored by our loader. It is a report warning (non-goal).

## 7. Materials mapping

The glTF exporter turns **Principled BSDF** node trees into glTF PBR. Our loader then maps:

| Blender (Principled BSDF) | glTF | Skate `Material` | Today |
|---|---|---|---|
| Base Color (color / image) | `baseColorFactor` / `baseColorTexture` | `baseColor` / `baseColorTexture` | ✅ |
| Metallic, Roughness (values) | `metallicFactor`, `roughnessFactor` | `metallicFactor`, `roughnessFactor` | ❌ not read, fix in 19e |
| Metallic/Roughness (images) | `metallicRoughnessTexture` (packed G/B) | `metallicRoughnessTexture` | ✅ |
| Normal Map node | `normalTexture` | `normalMap` | ✅ |
| Emission color/strength | `emissiveFactor` (+ `KHR_materials_emissive_strength`) | `emissiveFactor` / `emissiveTexture` | texture ✅, factor ❌ (19e) |
| glTF Material Output "Occlusion" | `occlusionTexture` | `aoTexture` | ✅ |
| Blend mode / Alpha | `alphaMode`, `alphaCutoff` | `alphaMode`, `alphaCutoff` | ✅ |
| Backface culling off | `doubleSided` | `doubleSided` | ✅ |

- Other nodes (procedural textures, mix shaders, toon, SSS) are not exported as-is. The script lists every material
  whose tree has nodes other than Principled BSDF, Image Texture, Normal Map, Mapping/UV and the glTF output group,
  and the report shows them as "simplified on export". Baking is a non-goal for v1 (a later "Bake procedural
  materials" option is possible).
- ⚠ UNVERIFIED: whether Assimp exposes `KHR_materials_emissive_strength`. If it doesn't, strength is multiplied into
  `emissiveFactor` by our script before export.
- Material overrides in Skate (`.skmat`, `RenderComponent.materialPath`) still win over the GLB's materials, so a
  re-import never resets a material the user set in the editor.

## 8. "Send to Skate" Blender add-on (optional)

For `.blend` files kept **outside** the project (an art repo, a shared drive).

- A small Python add-on (`tools/blender-addon/skate_send/`) adds **File > Export > Send to Skate** and a button in the
  N-panel. The user picks a target project once, and the add-on remembers it per `.blend`
  (`bpy.types.Scene` custom property).
- Transport (v1): **files only**. The add-on saves a copy of the `.blend` into
  `<project>/Assets/<chosen folder>/` (or exports GLB directly when the project setting asks for it). The editor's
  `.blend` watch (§5) then imports it. No network port, no editor running required.
- v2 option: notify a running editor through the existing MCP server (`tools/mcp`) to focus the asset. Not in v1.
- Packaging: Blender 4.2+ uses **Extensions** (`blender_manifest.toml`), while older versions use `bl_info` add-ons.
  We ship one zip with both. ⚠ UNVERIFIED: that a single zip installs on both 3.6 and 4.2+.
- The editor's settings page has **Install Blender add-on…**, which copies the zip and shows the steps. Installing it
  is never automatic.

## 9. Settings

- `EditorSettings` (per user, never in the project): `blenderPath: String?`, `blenderTimeoutSeconds: Int = 120`,
  `blenderMaxJobs: Int = 2`, `blenderImportOnOpen: Boolean = true`. Editor settings are JSON; adding fields with
  defaults stays backward-compatible (no migration, same as `gameJarPath`).
- Project settings: none in v1. A team that wants a fixed Blender version documents it. The report records the
  Blender version used, so differences show up in review.
- Settings UI (EDT conventions): Blender section with the detected installs, "Browse…", "Test" (runs `--version`),
  "Install add-on…". Strings in `assets/strings/editor/assets.properties` + `_fr`, buttons `tipped(...)`.

## 10. Failure modes

| Failure | Detection | Behaviour |
|---|---|---|
| Blender not found | Locator returns nothing | `.blend` files show a "Needs Blender" badge with a link to settings. Nothing else breaks. AST-16 SimReady items show the same state. |
| Version too old | `--version` < 3.6 | Same badge, with the found version. |
| Script error / bad `.blend` | Non-zero exit (`--python-exit-code`) | Keep the previous outputs. Console error with the last 50 log lines; report saved. |
| Timeout / hang | Runner timeout | Kill the process tree; same as script error. |
| Partial output | GLB missing or fails to load in `AssimpLoader` | Output goes to a temp folder first and replaces files only after a test load. |
| Missing textures / linked libraries | Report from the script | Import succeeds, with warnings listed in the inspector. |
| Save during import | Watch fires while a job runs | One queued follow-up job (§3). |
| `.blend` deleted | Watch sees it missing | Outputs kept (scenes may reference them). Console warning, with "Delete generated files" in the inspector. |
| Compressed / newer-format `.blend` | Blender error | Error says the file needs a newer Blender (we print the found version). |
| Embedded Python in `.blend` | — | Never executed (auto-run off, §3). |
| Unit scale ≠ 1 | Report | Warning, with the `blender.scale` setting. |

## 11. Testing (unit only, no Blender in tests)

- `BlenderLocator` with a fake file system and fake registry/PATH: order, version filtering, user setting wins,
  missing setting falls back.
- `--version` output parsing (fixtures from 3.6, 4.2 and current). ⚠ UNVERIFIED: exact output text (fixtures recorded
  in the spike).
- Command builder: `-b`, `--factory-startup`, `--python-exit-code`, **never** `-y`/`--enable-autoexec` (guard test),
  arguments after `--`, paths with spaces.
- `ProcessRunner` behind an interface: timeout, cancel, non-zero exit, log capture (fake process).
- Import job with a fake runner: outputs replaced in place, **GUIDs kept** across re-imports, new outputs registered,
  removed outputs handled, failed import keeps the previous files.
- Watch rules: `.blend1`/`.blend@` ignored, save-during-import queues one job, stability check.
- `report.json` parsing; materials report; action list → `Board@Action.glb` names; `clipName("Board@Ollie.glb") ==
  "Ollie"`; discovery lists `*@*.glb` as animations.
- `AssetMeta.importerFor("x.blend") == "blender"`; import settings round-trip through `settings`.
- Packing guard: a project with `.blend` files packs the generated GLBs and never a `.blend`.
- `AssimpLoader` factor mapping (19e): metallic/roughness/emissive factors from a small glTF fixture (no GPU: test the
  material-reading function on its own).
- The Python script gets a separate smoke test run by hand during the spike (needs Blender). It is not part of `gradlew
  test`.

## 12. Phasing (proposed follow-up tasks)

| Task | Scope | Deps |
|---|---|---|
| AST-19a | Locator + `ProcessRunner` + settings fields + "Test" button. Shared with AST-16e. | — |
| AST-19b | `.blend` import: script, outputs folder, GUID-stable replace, report, inspector settings | 19a, spike on 3.6/4.2 |
| AST-19c | Re-import on save + import on open + animation reload hook | 19b |
| AST-19d | Actions: `FILES` mode (`@` convention, discovery, `clipName`); `EMBEDDED` mode after D4 | 19b; ANI review for EMBEDDED |
| AST-19e | `AssimpLoader` metallic/roughness/emissive factors | — (can go first) |
| AST-19f | "Send to Skate" add-on + install button | 19c |

## 13. Open questions for the human / lead

1. **Commit generated GLBs?** Proposed yes (teammates/CI without Blender can open and export). The cost is binary
   churn in git on every save.
2. **Minimum Blender version:** 3.6 LTS (proposed) or 4.2 LTS only (simpler script, one add-on format)?
3. **Removed outputs** (an action deleted in Blender): delete the generated clip (scenes referencing it break visibly)
   or keep it and warn?
4. **Action mode default:** `FILES` (works with today's loader) vs. waiting for multi-clip support (`EMBEDDED`).
5. **Add-on in v1** or later? It only matters for `.blend` files outside the project.
