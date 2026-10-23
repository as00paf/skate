# AST-01 — Asset pipeline review

Scope: `engine/assets/AssetsManager`, `Assets`, `loaders/*`, `serialization/*`, `engine/utils/AssetsResolver`, `core/JobSystem`.
Status: review + target design. No code changed. Follow-ups: AST-02 (IDs/handles), AST-03 (async/ref-count), AST-04 (versioning), AST-05, AST-06.

## 1. Current state

- `AssetsManager` owns five `ConcurrentHashMap<String, T>` caches (textures, shaders, models, sounds, animations) keyed by **path strings**, and exposes ~25 synchronous entry points (`getTexture`, `loadModel`, `resolveModel`, `getShader`, ...). ~60 call sites use them.
- Loaders decode **and** upload in a single call (`TextureLoader.uploadToGPU`, `AssimpLoader` → `VAOLoader`, `SoundLoader` → `alGenBuffers`), so every load must run on the GL/AL thread.
- Two sources of bytes: disk (`File`/absolute path) and the packed atlas (`AssetsResolver`, used by exported games). They are separate code paths with different cache keys.
- Nothing is ever unloaded.

## 2. Defects

| # | Where | Defect | Sev |
|---|-------|--------|-----|
| D1 | `AssetsManager` | Inconsistent cache keys: some entries keyed by `File(path).absolutePath`, others by raw path (`resolve*`, `getBundledTexture`), `loadModel(bytes, path)` keys by `model.path`. `hasTexture/hasSound/hasAnimation/getModel` look up with different key forms than they were stored with, so they return false/null for loaded assets. Same asset can load twice (packed vs. disk). | High |
| D2 | `AssetsManager` | Check-then-act on `ConcurrentHashMap` (`get` → load → `put`) is not atomic; two callers can load the same asset concurrently, leaking one GL/AL object. Use `computeIfAbsent`/in-flight futures. | High |
| D3 | `AssetsManager.loadModel` / `getShader` | Failure fallback is recursive and silently substitutes a cube / default shader. For `getTexture` the **default texture is cached under the failing path**, so a later-fixed file never reloads. `loadModel(bytes)` falls back to `loadModel(Assets.Models.CUBE)` (a relative path resolved against CWD, not the resolver) – fails in an exported game. | Med |
| D4 | `AssetsManager.resolveAnimation` / `loadAnimationSync` | Only `animations[0]` is returned/cached; extra clips in a file are dropped (TODO in code). | Med |
| D5 | `AssetsManager.resolveModel/resolveTexture/resolveAnimation` | No try/catch: decode failure propagates as exception, unlike the disk paths which fall back. Inconsistent error policy. | Med |
| D6 | `TextureLoader` | `stbi_load*` returns `null` on failure; code then builds `Texture(width.get(0)=0…)` and calls `glTexImage2D` with null pixels instead of throwing — failures are never surfaced and the fallback in `getTexture` never triggers for corrupt files. Reason (`stbi_failure_reason`) is dropped. | High |
| D7 | `TextureLoader` | `stbi_set_flip_vertically_on_load` is **global, non-thread-local** state set before each load. Unsafe once decode moves to workers (use `stbi_set_flip_vertically_on_load_thread` or flip manually). Also `BufferUtils.createIntBuffer` ×3 per call (GC-managed direct memory) – use `MemoryStack`. | High (for async) |
| D8 | `TextureLoader.loadFromBuffer` / `AssetsResolver.resolveData` | Buffer ownership unclear. `resolveData` returns `MemoryUtil.memAlloc` memory; `AssimpLoader.loadModel(ByteBuffer)` frees it, `TextureLoader.loadFromBuffer` and `AnimationLoader.loadAnimations(ByteBuffer)` do **not** → native leak per packed texture/animation. Embedded-texture copy in `AssimpLoader` uses `allocateDirect` (fine) but the contract differs. | High |
| D9 | `AssetsResolver` | `copyOfRange` of `binData` for each request (full copy on heap, then a second copy to native). `resolve<T>` decodes via UTF-8 string copy. `resolveModel` is a near-duplicate of `resolveData` (different allocator). `lateinit` fields + `initialized` flag are unsynchronised; lookup is a linear `firstOrNull { it.path == path }` over an extension bucket. Not thread-safe to initialise while loads run. | Med |
| D9b | `AssetsResolver` | Lookup is by raw string path, case/separator sensitive; `Assets.Bundled` uses `\\` separators while `Assets.Models` uses `/` and shaders use `/shaders/...` (classpath). Three path dialects. | Med |
| D10 | `Assets.kt` | Hard-coded path constants in 4 dialects (`assets/...`, `/shaders/...`, `\\textures\\...`, `EngineDefaults\\...`). Bugs: `Bundled.CYLINDER` points at `cube.obj`; `Textures.WOOD_*` map to `skatelite_*.png` but `Bundled.WOOD_*` to `wood_*.png`; `Textures.APP_ICON` and `Bundled.APP_ICON` differ; `bundledAssets` list hard-codes a build output path (`build\\libs\\skate-game.jar`). | Med |
| D11 | `AssimpLoader` | `loadModel(String)` omits `aiProcess_GenSmoothNormals`, unlike the in-memory overloads → different mesh output depending on the source. Flags are duplicated 3×. | Med |
| D12 | `AssimpLoader` | `aiReleaseImport(scene)` is not in a `finally`; any exception while processing leaks the scene. Disk textures referenced by material are loaded with `loadFromFile(p)` using the path as written in the model (relative to CWD, not to the model file) and cached only in a per-model map, so the same texture is uploaded once per model and bypasses `AssetsManager.textures`. | High |
| D13 | `AssimpLoader` | Decode, material/texture upload and VAO creation are interleaved; cannot be split into worker + main-thread phases without refactor. | High (for async) |
| D14 | `AnimationLoader` | `getAnimInfo` reads `animationChannels.get(1)` (hard-coded index 1 not `i`) and `continue`s without incrementing `i` if `mChannels()` is null → infinite loop. Throws `Error` (not `Exception`) so callers' `catch (e: Exception)` miss it. Scene not released on exceptions. Name derived with `substringBefore(".fbx")`, so non-fbx formats break; uses deprecated `capitalize()`. | High |
| D15 | `SoundLoader` | Catch logs + `printStackTrace()` + rethrows (double reporting). WAV path reads whole file into heap then copies. `stb_vorbis_decode_filename` needs a disk file – cannot load from the packed atlas, so sounds are not exportable via the resolver (no `resolveSound`). Sound buffers have no packed path. | Med |
| D16 | `ShaderLoader` | Loads from classpath/disk only, no packed variant; `getShader` fallback hides compile errors behind the default shader (acceptable in editor, silent in game). | Low |
| D17 | `AssetsManager` | `vaoLoader` is a public field and tracks VAO/VBOs separately; models do not own/free their GPU buffers. No `unload`/`dispose` anywhere for textures, models, sounds (only `Shader`/`SoundBuffer` expose delete; `glDeleteTextures` used only in `ThumbnailCache`). GPU/AL memory grows monotonically across scene switches and editor sessions. | High |
| D18 | `AssetsManager.get*s()` | Return snapshot copies of values only (no keys/IDs), so UI cannot show what an asset is. | Low |
| D19 | `Serializer`/`Texture` | `Texture` is `@Serializable` with `filePath` but not an asset reference; scenes embed paths (breaks on rename/move). No schema version anywhere in `Serializer` (see AST-04). | Med |
| D20 | Whole package | Zero unit tests for loaders/manager (GL-bound code), no seam to fake GPU upload. | Med |

## 3. Threading issues

Rule today: *everything runs on whichever thread calls* (de-facto the main/GL thread). Issues that block moving work off-thread:

1. **GL/AL calls inside loaders** (D13): `glGenTextures`, `glTexImage2D`, VAO creation, `alGenBuffers`/`alBufferData` happen in the same function as decode.
2. **Global stb flip flag** (D7).
3. **Non-atomic cache fill** (D2) – races as soon as two threads (or a coroutine and the main thread) request one asset.
4. **Unsynchronised `AssetsResolver` initialisation** and shared `binData` (read-only afterwards, so reads are fine if published safely; make fields `@Volatile`/immutable snapshot).
5. **Native buffer ownership** unclear across threads (D8): a buffer allocated on one thread and freed by a loader on another needs an explicit owner.
6. **`JobSystem` facts relevant to the design:** `runAsync`/`runAsyncDeferred` use `Dispatchers.Default` (CPU-sized pool — blocking file IO should use `runIO`); `runOnMain` queues to `mainThreadTasks` and `update()` drains at most `maxMainThreadTasksPerUpdate` per frame (a natural GPU-upload budget); `mainThreadId` is captured at construction (JobSystem must be constructed on the main thread); `runIO` returns `Job` only (no result); the exception handler uses `println` instead of `LoggerService`, and a failed `runOnMain` task is only logged — an asset waiting on it would stay `Loading` forever unless the loader catches and records failure.
7. **No cancellation**: loads for a scene being unloaded cannot be aborted.
8. `OpenAL`/`OpenGL` contexts: AL calls (`alGenBuffers`) are context-bound too; treat as main-thread uploads.

## 4. Target design

Goals: stable references (AST-02), non-blocking loads (AST-03), deterministic lifetime, one code path for disk and packed sources, testable without a GL context.

### 4.1 Identity: `AssetId`, `AssetHandle<T>`

- `AssetId` = value class over a 128-bit UUID (serialised as 32-char hex string). Generated once per source file, persisted in a `<file>.meta` JSON sidecar (`{ "schema": 1, "guid": "...", "importer": "texture", "settings": {...} }`). Meta files are created by the editor's file scanner when missing; copy/rename/move of file+meta preserves the GUID.
- `AssetRegistry` (new, in `engine/assets`): `AssetId ⇄ AssetRecord(path, type, sourceHash)`; built from the project folder in the editor, from the packed manifest in exported games (AST-06). Path becomes only a *lookup hint* (`registry.idForPath`).
- `AssetHandle<T : Asset>`: lightweight, serialisable reference `(AssetId)` plus transient resolved state. Scenes/prefabs/components serialise only the id (AST-02). Handle API: `state: AssetState` (`Unloaded | Loading | Ready | Failed(reason)`), `get(): T?` (returns placeholder while loading), `isReady`.
- Built-in assets (`Assets.*` constants) get fixed well-known GUIDs in an `EngineDefaults` registry segment; constants collapse into one path dialect (`/`, project-relative, case-preserved) and D10 defects are fixed during migration.

### 4.2 Loading pipeline (two phases)

```
request(id) ──► cache hit? ──► handle (ref++)
        │ miss
        ▼
  Phase 1 DECODE  (JobSystem.runIO read bytes → runAsync decode)   [worker]
     bytes → TextureData / ModelData / SoundData / ShaderSource / Animation[]  (CPU only, no GL/AL)
        ▼
  Phase 2 UPLOAD  (JobSystem.runOnMain, counted against per-frame budget)  [main thread]
     CPU data → GPU object (texId / VAOs / alBuffer / program); free native staging buffers
        ▼
  state = Ready ─► AssetEvent.Loaded(id)  (EventSystem)
```

- Loaders split into `Decoder<TData>` (pure, thread-safe) and `Uploader<TData, TAsset>` (main thread). Existing `TextureLoader/AssimpLoader/SoundLoader` are refactored accordingly; sync `AssetsManager.getX` stays as a convenience that decodes inline and uploads immediately **only when called on the main thread** (needed for editor/legacy callers and bootstrap).
- Dedup: `ConcurrentHashMap<AssetId, AssetEntry>` with `computeIfAbsent`, entry holds the in-flight `Deferred`. Concurrent requests share one load (fixes D2).
- Byte source abstraction `AssetSource { open(record): ByteBuffer }` with `FileSource` and `PackedSource` (memory-mapped / slice view of `binData`, no `copyOfRange`). Removes D1, D8 (single owner: decoder frees staging memory in `finally`), D9.
- Thread-safety fixes: per-call flip (`stbi_set_flip_vertically_on_load_thread`), `MemoryStack` for out-params, `aiReleaseImport` in `finally`, texture dependencies of a model are requested as child handles through the manager (shared, deduped, ref-counted — fixes D12).
- Placeholders: per-type default asset (1×1 magenta/white texture, unit cube model, silent sound, default shader) returned by `handle.get()` while `Loading`/`Failed`. Failures are recorded on the entry (`Failed(reason)`), logged once through `LoggerService`, and **not** cached as success under the original key (fixes D3, D6).
- Errors: decoders throw `AssetLoadException(id, path, cause)` (never `Error`), used uniformly (D5, D14).
- Cancellation: entries hold the `Job`; releasing the last reference of a still-loading asset cancels it.

### 4.3 Lifetime: ref-counting

- Each `AssetEntry` has an `AtomicInteger refCount`. `acquire(id): AssetHandle<T>` increments; `AssetHandle.close()`/`manager.release(handle)` decrements. Owners: components/scenes acquire on load, release on destroy/scene unload. Dependencies (model → textures) hold their own references and release them when the parent is disposed.
- At `refCount == 0` the entry becomes *evictable*; actual dispose is deferred to the main thread (`runOnMain`) after a grace period (e.g. N frames or until scene-switch end) to avoid thrash on reload of the same scene. `AssetManager.collect()` forces it; `pin(id)` exempts engine defaults/placeholders.
- Disposal frees GPU/AL objects: `glDeleteTextures`, VAO/VBO deletion tracked by the model (VAOLoader ownership moved into the model), `alDeleteBuffers`, `glDeleteProgram`. Disposal asserts main thread.
- Debug: `AssetManager.stats()` (id, path, type, refCount, state, bytes) for the editor asset panel (replaces `get*s()` snapshots, D18); leak report on shutdown listing entries with refCount > 0.

### 4.4 Events & serialisation

- `AssetEvent` (sealed): `Loaded(id)`, `Failed(id, reason)`, `Unloaded(id)`, `Reloaded(id)` (hot reload, AST-05) published on `EventSystem` from the main thread.
- Scene/prefab/project JSON: add `"schema"` int to the root, a `Migrations` chain (`N → N+1`) run on `JsonElement` before decode; v1→v2 migration for AST-02 converts path references into GUIDs via the registry. Each migration gets a unit test (AST-04).
- Hot reload (AST-05) re-decodes by `AssetId`, swaps GPU payload in place on the main thread, keeping the handle identity.

### 4.5 Testability

- `GpuUploader` interface (`uploadTexture`, `createMesh`, `uploadSound`, `delete*`) with a recording fake for unit tests; decoders are pure and tested with tiny fixtures (PNG, WAV, glTF).
- Ref-count / lifecycle tests: acquire/release, dedup of concurrent requests, evict-after-grace, failure → placeholder, cancellation. Use a manual `JobSystem` pump (`update()`) for determinism.

## 5. Migration plan

1. **AST-02**: `AssetId`, `.meta` sidecar, `AssetRegistry`, `AssetHandle`; path→GUID migration of scenes/prefabs; unify `Assets` constants (fix D10) and cache keys (D1) first as a small preparatory commit.
2. **AST-03**: split decode/upload (D13), `AssetSource`, `AssetManager` with dedup, placeholders, ref-count, `AssetEvent`. Keep old `AssetsManager` API as deprecated adapters delegating to the new manager until all ~60 call sites are migrated.
3. **AST-04**: schema versions + migrations (in parallel after this review).
4. **AST-05/06**: hot reload and manifest-based packing build on `AssetRegistry`/`AssetSource`.

Quick wins that can be done ahead of the redesign (suggested as small separate fixes, not part of this task): D6 (throw on `stbi` null), D8 (free buffers), D12 (`finally` release), D14 (loop/index bug, `Exception`), D11 (flags parity), D10 constant typos.

## 6. Cross-domain needs

- **ECS/Editor**: components referencing assets by path (`MeshRenderer`, `AudioSource`, materials) switch to `AssetHandle` fields (ECS-02 prefab/serialisation, EDT asset browser for `.meta`/GUID display).
- **Render**: `VAOLoader` ownership/dispose and `Shader` placeholder policy (REN).
- **Core**: `JobSystem` — return values from `runIO`, route exception handler to `LoggerService`, optional per-frame upload time budget.
