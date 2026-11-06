# AST-16 — Asset store window

Status: **approved** 2026-11-06 (human). Slice order: a → b → d → f → e → c (no-login libraries first; Sketchfab last and optional; no Mixamo, no Poly Pizza). ND licenses: warn and record the conversion. Slices a (Poly Haven) and b (ambientCG) implemented (`editor/assetstore/**`). Author: `skate-online` (lead-assigned; `skate-assets` reviews).
Paths are relative to `src/main/kotlin/com/pafoid/skate/`.

Marker: **⚠ UNVERIFIED** = an API detail I could not confirm from the provider's public docs on 2026-11-06. Each one needs a spike before its provider ships.

## 1. Goals / non-goals

Goals:
- An editor window that searches free 3D content from several providers, previews it, shows its license, downloads it and imports it into the project's assets folder.
- One provider plugin interface, so adding a source means one class plus JSON fixtures.
- License-aware: every imported file is tracked in an attribution ledger. Exports get a credits file.
- Providers: NVIDIA SimReady (Hugging Face, USD), Poly Haven, ambientCG, Kenney, Quaternius, Sketchfab.

Non-goals (v1): paid assets or purchases, uploading/publishing, Mixamo (no public API; human decision 2026-11-06: no scraping), runtime (in-game) downloads, automatic updates of imported assets.

**No scraping rule.** We only use documented APIs, documented file URLs (CDN/Hub), or files the user downloaded manually. This also applies to Kenney and Quaternius (§5.4, §5.5).

## 2. Where the code lives

The store is editor-only; exported games never contain it.

| Part | Path | Domain conventions |
|---|---|---|
| Provider SPI, registry, download/import pipeline, ledger, credentials | new `editor/assetstore/**` | AST (owner review) |
| Providers | new `editor/assetstore/providers/<name>/**` | AST |
| USD converter | new `editor/assetstore/convert/**` (shares the Blender locator with AST-19) | AST |
| Window | new `editor/ui/windows/assetStore/**` | EDT: strings, tooltips, stable IDs |
| Import/delete mutations | `editor/commands/assetstore/**` via the service layer | UI mutation pipeline guard |
| Strings | new area `assets/strings/editor/assetstore.properties` + `_fr` | `StringTablesTest` |

The HTTP code is not `engine/net` (that is game networking, NET-01). We use JDK 17 `java.net.http.HttpClient`, so there is **no new dependency**. It sits behind a seam so that tests never open sockets:

```kotlin
interface StoreHttp {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult      // small JSON / thumbnails
    suspend fun download(url: String, headers: Map<String, String>, target: Path,
                         progress: (Long, Long?) -> Unit, cancel: CancelToken): DownloadResult
}
```

`JdkStoreHttp` is the only implementation that touches the network. It sends `User-Agent: SkateEngine/<version> (asset-store)` on every request, because Poly Haven requires a unique UA. It honours `429` + `Retry-After` and applies a per-provider concurrency limit (default 2) plus a response cache (§7.2).

## 3. Provider plugin interface

```kotlin
interface AssetStoreProvider {
    val id: String                         // stable, lowercase: "polyhaven", "ambientcg", "sketchfab", "simready", "kenney", "quaternius"
    val displayNameKey: String             // StringManager key
    val kinds: Set<StoreAssetKind>         // MODEL, MATERIAL, HDRI, TEXTURE, PACK
    val auth: AuthRequirement              // None | ApiKey(headerName) | OAuth2(config) | OptionalToken
    val poweredBy: Attribution?            // provider-level credit shown in the window (Poly Haven API condition)

    suspend fun search(query: StoreQuery, session: AuthSession?): StorePage
    suspend fun details(ref: StoreAssetRef, session: AuthSession?): StoreAssetDetails
    suspend fun thumbnail(ref: StoreAssetRef): ThumbnailSource       // URL or bytes; cached by the window
    suspend fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant,
                                session: AuthSession?): DownloadPlan  // fresh URLs, never cached (Sketchfab URLs expire)
    fun importPlan(details: StoreAssetDetails, staged: StagedFiles): ImportPlan  // pure, unit-testable
}

data class StoreQuery(val text: String, val kind: StoreAssetKind?, val category: String?,
                      val licenseFilter: LicenseFilter, val cursor: String?, val pageSize: Int = 40)
data class StorePage(val items: List<StoreAssetSummary>, val nextCursor: String?, val total: Int?)
data class StoreAssetSummary(val ref: StoreAssetRef, val title: String, val kind: StoreAssetKind,
                             val thumbnailUrl: String?, val license: LicenseInfo?, val author: String?)
data class StoreAssetRef(val providerId: String, val sourceId: String, val revision: String? = null)
data class StoreAssetDetails(val summary: StoreAssetSummary, val description: String?, val sourceUrl: String,
                             val authors: List<Author>, val license: LicenseInfo,
                             val variants: List<StoreVariant>, val tags: List<String>)
data class StoreVariant(val id: String, val format: SourceFormat,        // GLTF, GLB, FBX, OBJ, USD, USDZ, BLEND, TEXTURE_SET, ZIP_PACK
                        val resolution: String?, val sizeBytes: Long?)
data class DownloadPlan(val items: List<DownloadItem>, val headers: Map<String, String>)
data class DownloadItem(val url: String, val relativePath: String, val sizeBytes: Long?, val md5: String?, val unzip: Boolean = false)
data class ImportPlan(val entries: List<ImportEntry>, val conversion: ConversionStep?)   // e.g. USD -> glTF
```

Rules:
- `relativePath` is sanitised by the pipeline (no `..`, no absolute paths, no drive letters). Providers cannot write outside the staging folder.
- Providers stay stateless. Auth lives in `AuthSession` (§4), and caching lives in the pipeline.
- Registration: `AssetStoreRegistry` holds the built-in list. A `java.util.ServiceLoader<AssetStoreProvider>` hook comes later for third-party plugins (v2, not in v1 scope).
- Every provider ships JSON fixtures under `src/test/resources/assetstore/<provider>/` and has parser tests.

## 4. Auth and credentials

| Provider | Auth | Notes |
|---|---|---|
| Poly Haven | none | Unique User-Agent required (confirmed, polyhaven.com/our-api). |
| ambientCG | none | ⚠ UNVERIFIED: whether there is a UA requirement or rate limit (not in v3 docs). |
| SimReady (HF) | none for public datasets; optional HF token (`Authorization: Bearer hf_…`) for gated repos or higher rate limits | ⚠ UNVERIFIED: anonymous Hub rate limits for large USD trees. |
| Sketchfab | OAuth2 user login, required to download (confirmed: download needs an authenticated end user, `Authorization: Bearer <token>`) | ⚠ UNVERIFIED: whether Sketchfab allows a loopback redirect URI (`http://127.0.0.1:<port>/callback`) for a desktop client, whether PKCE is supported, client registration process, refresh-token lifetime. ⚠ UNVERIFIED: whether a personal API token (`Authorization: Token …`) works for `/download` (the docs show only OAuth Bearer). |
| Kenney / Quaternius | none | No API (§5.4, §5.5). |

Flow for OAuth2: the window shows "Log in to Sketchfab", which opens the system browser on the authorize URL. A one-shot loopback listener (`127.0.0.1`, random port, `state` check, 2-minute timeout) receives the code, which we exchange for a token. Fallback if loopback is not allowed: the user pastes the code or token into a field.

`CredentialStore` (interface):
- Stored **per user, never in the project**: `%APPDATA%/Skate/credentials.json`. It is never committed or exported. A guard test asserts that the store path is outside any project root.
- v1 writes the file with user-only permissions (plain text, which the settings UI says clearly). v2 option: Windows Credential Manager or DPAPI. That would need JNA or a small native call, so it needs lead approval.
- Logging redacts `Authorization` and `X-Auth-Token` headers (unit-tested).

## 5. Providers

### 5.1 Poly Haven (models, textures, HDRIs) — confirmed API
- Base `https://api.polyhaven.com`. Endpoints: `/assets?type=…&categories=…`, `/search?q=…&t=…&limit=…`, `/info/{id}`, `/files/{id}`, `/author/{id}`, `/categories/{type}`.
- `/files/{id}` for models has `gltf`, `fbx`, `usd` and `blend` keyed by resolution. Each entry has `url`, `md5`, `size` and an `include` map of dependent files (textures). We pick **glTF** and download the main file plus every `include` entry, preserving relative paths. MD5 is verified.
- Textures: the `$map`-style per-map entries (name → resolution → format) become a `TEXTURE_SET`, imported as a material (REN material keys). HDRIs are imported as environment maps (REN).
- License: assets are CC0. The API condition (not an asset condition) is that users must be told assets come from Poly Haven, so the provider tab shows "Powered by Poly Haven". ⚠ UNVERIFIED: exact `/search` response field names (the docs say "ranked list of slugs with scores"); confirm with a fixture.

### 5.2 ambientCG (materials) — verified, implemented in slice b
- `GET https://ambientCG.com/api/v3/assets` with `type=material`, `sort=popular`, `limit`, `offset`, `q`, `id`, `include=title,downloads,thumbnails,tags,maps,shortDescription`. The response is `{totalResults, nextPageHttp, currentPageHttp, previousPageHttp, assets: [...]}`. Each asset carries only `id` plus the included fields: `title`, `thumbnails` (`"256-PNG"` → URL, …), `downloads` (`[{attributes: "1K-JPG", extension: "zip", url, size}]`) and `maps` (`color`, `normal`, `roughness`, `metalness`, `ambient-occlusion`, …). There is no license field: everything on ambientCG is CC0. (Verified against the live API on 2026-11-06; fixtures are in `src/test/resources/assetstore/ambientcg/`.)
- Download URLs (`https://ambientcg.com/get?file=<id>_<attr>.zip`) redirect to a CDN, and `JdkStoreHttp` follows the redirect. No MD5 is given, so only the size is checked.
- The zip holds `<id>_<attr>_Color`, `_NormalGL`, `_NormalDX`, `_Roughness`, `_AmbientOcclusion`, `_Displacement` (and `_Metalness` for metals; ⚠ that file name is inferred from the `metalness` map name, not seen in a downloaded zip), plus `.usdc`, `.blend`, `.mtlx`, `.tres` and a preview `.png`.
- `postProcess` unzips in staging (zip-slip and size limits) and packs **Roughness → G, Metalness → B, AO → R** into `<id>_<attr>_ORM.png`. The shader reads metal from B and roughness from G of `metallicRoughness`, and AO from R, so a grayscale roughness map cannot be used directly. Missing maps get neutral values (AO 1, metal 0).
- Import: Color, NormalGL and ORM, plus `<id>.skmat` (baseColor, normal, metallicRoughness, and ao when an AO map exists). Displacement and DirectX normals are not imported. Only `<n>K-JPG/PNG` texture sets are offered; ambientCG 3D models and HDRIs are not in v1.
- Fail soft: malformed assets or downloads are skipped, and a non-JSON or error response surfaces as an inline error for this provider only.

### 5.3 Sketchfab (models) — partly verified
- Search: Data API v3 `GET https://api.sketchfab.com/v3/search?type=models&q=…&downloadable=true` plus a license filter. ⚠ UNVERIFIED: search parameter names (`downloadable`, `license`, `cursor`) and the license slugs in responses.
- Download: `GET https://api.sketchfab.com/v3/models/{uid}/download` with a Bearer token. The response has `gltf` and `usdz` objects with `url`, `size` and `expires` (example shows 300 s). The glTF is a zip with `scene.gltf`, `scene.bin` and `textures/`. URLs are temporary and **must not be cached**, so `resolveDownload` runs right before each download. No source formats (FBX/OBJ) via the API.
- License: per model (CC-BY, CC-BY-SA, CC-BY-ND, CC-BY-NC, CC-BY-NC-SA, CC-BY-NC-ND, CC0). The author and model URL are stored for attribution. Developer Terms and integration guidelines (login UI, branding) still need a read before shipping (⚠ UNVERIFIED).

### 5.4 Kenney (packs) — no API
- Facts: all game assets are CC0 and attribution is optional (kenney.nl/support). No API or programmatic download is documented. The Kenney logo must not be used.
- v1 design: a **curated catalog** `assets/assetstore/kenney.catalog.json` ships with the editor: pack name, page URL, preview image (bundled, our own render or none), tags, license `CC0-1.0`. "Get" opens the pack page in the browser, and the user downloads the zip. **"Import downloaded pack…"** takes the zip, matches it to the catalog entry by filename, and imports the selected models with the license recorded. v1 imports only the **glTF/GLB** files inside the pack (§7.1). Packs that ship only FBX/OBJ are listed as "not importable yet" until the follow-up task in §7.1 lands.
- ⚠ UNVERIFIED: whether stable direct zip URLs exist that Kenney allows hot-linking to. Ask before automating.

### 5.5 Quaternius (packs) — no API
- Facts: "All models are under the CC0 License", with no attribution required (quaternius.com/faq). Formats mentioned: `.blend` and FBX. The "All in one" bundle is on Patreon.
- Same design as Kenney: curated catalog plus manual download plus "Import downloaded pack…". ⚠ UNVERIFIED: where individual pack downloads are hosted, and whether glTF is available. If a pack has only FBX or `.blend`, v1 cannot import it (§7.1). Later routes: the FBX model importer task in §7.1, or `.blend` via AST-19.
- Optional later provider: **Poly Pizza** API (`https://api.poly.pizza/v1/`, `X-Auth-Token` API key, GLB). It hosts many CC0/CC-BY low-poly models and could cover individual Kenney/Quaternius models. ⚠ UNVERIFIED: the official docs page returned 404, and the endpoints are known only from third-party code. Not in v1.

### 5.6 NVIDIA SimReady via Hugging Face (USD models) — partly verified
- Facts: SimReady Central (`nvidia/simready-central` Space) is a semantic-search demo. Its index is pulled from an unnamed S3 bucket, so the Space itself is **not an API**. The registry is the dataset `nvidia/simready-central-library` (Apache-2.0), with a GitHub index (`NVIDIA-Omniverse/simready-central`) of PR-submitted references to contributor datasets at a **pinned commit**. Contributors keep their own licenses, which **can be non-commercial**. Known datasets: `nvidia/simready-assets` (CC-BY-4.0, about 2,677 USD assets), `nvidia/PhysicalAI-SimReady-Warehouse-01` (CC-BY-4.0, 753 assets), plus partner datasets (Vertiv, Trane, Robotiq CC-BY-4.0; FANUC Apache-2.0; DEEP Robotics CC-BY-4.0 + BSD notices).
- ⚠ UNVERIFIED: the index schema (file format, field names for repo, revision, package path, license, thumbnails). Neither the dataset card nor the NVIDIA docs page documents it. ⚠ UNVERIFIED: an HTTP search endpoint (docs mention a "SimReady Central Endpoint" and a Python `simready-search` package only).
- v1 design (no index dependency): a configurable list of dataset repos (defaulting to the two NVIDIA ones). Standard Hugging Face Hub endpoints: `GET https://huggingface.co/api/datasets/{repo}/tree/{revision}/{path}?recursive=…` to list, and `https://huggingface.co/datasets/{repo}/resolve/{revision}/{path}` to download. (These are standard Hub endpoints, ⚠ UNVERIFIED against these specific repos and their layout.) Search is local filtering over a cached listing (name, folder/category). Each asset records the **commit SHA** as `revision` for reproducibility.
- v2: read the central index once its schema is known, which gives search across all contributors with per-entry licenses.
- Package download: the USD root file **plus every referenced layer, material and texture** (docs: "retain the referenced materials and textures"). v1 downloads the asset's whole package folder. ⚠ UNVERIFIED: whether packages are self-contained folders or reference shared material libraries elsewhere in the repo.
- License per dataset, read from the dataset card's `license` field via `GET /api/datasets/{repo}`. ⚠ UNVERIFIED: whether the card has per-package overrides. Non-commercial datasets are hidden unless the project allows NC (§6.1).

## 6. License display and attribution

### 6.1 Model
```kotlin
data class LicenseInfo(val spdx: String,            // "CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "CC-BY-NC-4.0", "Apache-2.0", "LicenseRef-<provider>-<x>"
                       val name: String, val url: String,
                       val attributionRequired: Boolean, val commercialUse: Boolean,
                       val derivatives: Boolean, val shareAlike: Boolean)
```
- A `LicenseCatalog` maps provider slugs to `LicenseInfo`. Unknown slugs map to `LicenseRef-unknown` and **block import** until the user confirms.
- Window badges: green (CC0 or no attribution), blue (attribution required), amber (share-alike or no-derivatives), red (non-commercial). The details pane shows the full license name, link, author and source URL.
- Filter `LicenseFilter`: `CC0_ONLY` | `COMMERCIAL_OK` (default) | `ALL`. The project setting `assetStore.allowNonCommercial` defaults to `false`. When it is false, NC items are hidden and the import button is disabled.
- **ND licenses:** converting a format (USD to glTF) may count as a derivative. Show a warning on import of ND assets and record "format-converted" in the ledger. Legal reading open (Q3).

### 6.2 Attribution ledger
- Per project: `<project>/.skate/attribution.json`. `AssetRegistry` is rooted at the project folder, and `scan()` registers every non-hidden file. `scan()` already skips dot-directories (`onEnter { !it.name.startsWith(".") }`), so putting the ledger under `.skate/` keeps it out of the registry and the Asset Browser with **no registry change**. A unit test asserts that `scan()` of a project containing `.skate/attribution.json` does not register it.
- The ledger is human-readable and diffable, and it is **committed** with the project. The project `.gitignore` template ignores only `.skate/cache/`, not the whole `.skate/` folder.
- Format versioning follows `AssetMeta`: a top-level `schema` (`CURRENT_SCHEMA = 1`), and a decoder with `ignoreUnknownKeys = true` and `encodeDefaults = true`. A migration hook `AttributionLedger.migrate(schema, json)` mirrors `SchemaMigrations` and is empty in v1 (rule 8: no upgrade code until there is a v2).
```json
{ "schema": 1, "entries": [ {
  "assetIds": ["<guid of each imported file>"],
  "provider": "sketchfab", "sourceId": "abc123", "revision": null,
  "title": "Skate ramp", "sourceUrl": "https://sketchfab.com/3d-models/...",
  "authors": [{"name": "Jane", "url": "https://sketchfab.com/jane"}],
  "license": {"spdx": "CC-BY-4.0", "url": "https://creativecommons.org/licenses/by/4.0/"},
  "modifications": ["converted USD -> glTF", "scaled cm -> m"],
  "retrievedAt": "2026-11-06T12:00:00Z" } ] }
```
- Each imported file's `.meta` sidecar (`AssetMeta.settings`) gets `store.provider`, `store.sourceId` and `store.license`. These are new keys in the existing free-form `settings: Map<String, String>`, so they need **no `AssetMeta` schema bump**. Older readers ignore them. The Asset Browser and inspector can then show the license for any file, and the ledger can be rebuilt from metas if it is lost.
- Deleting an asset through the editor removes its GUID from the entry and drops the entry when it is empty. A consistency check (Tools menu) lists metas with no ledger entry, and the reverse.
- **Export** (with AST-15): generates `CREDITS.txt` (plus `credits.json`). `ReferenceCollector.collect()` returns `Set<File>`, not GUIDs, so each packed file is mapped to its GUID with `registry.idOf(<project-relative path>)`. Files with no id are skipped. The ledger entries whose `assetIds` intersect that GUID set are emitted. CC0 entries are listed as courtesy credits. Export **warns** if a packed asset is NC or has an unknown license. The "Attribution" panel in the store window shows the ledger.

## 7. Download and import pipeline

```
search/details (provider) → user picks variant → resolveDownload (fresh URLs)
→ download to staging  (<project>/.skate/cache/store/<provider>/<sourceId>/<variant>/, gitignored)
→ verify (size, md5 when given) → unzip (zip-slip + size/entry-count limits)
→ optional conversion (USD → glTF, §8) → path check on converter output (same zip-slip rules)
→ importPlan → copy into ProjectLayout.assetsDir(projectDir)/Store/<provider>/<slug>/
→ registry.register(file) per imported file → add store.* settings to each .meta
→ ledger entry → thumbnail
```
- **Target folder:** built only through AST-14's `ProjectLayout.assetsDir(projectDir)` / `assetPath(...)`, giving `Assets/Store/<provider>/<slug>/` (capitalised, matching the single assets folder). No hard-coded `assets/` string. `<slug>` is sanitised to `[a-z0-9_-]`, and a numeric suffix is added on collision with a different `sourceId`.
- **Registration:** there is no scoped `AssetRegistry.scan`. The pipeline calls `registry.register(file)` for **each imported file**. That creates the sidecar, and we then add the `store.*` settings and write the sidecar back. No full rescan, and no new registry API. (If a scoped `scan(dir)` is wanted later, that is a separate `NEEDS AST` request, not part of this design.)
- **Staging is never exported:** the exporter and `PackBuilder` must ignore `.skate/cache/store/`. `.skate/` is already skipped by registry scans, and AST-15/export adds an explicit exclusion plus a test so that staging never enters a pack even if the export walker changes.
- **Threading:** downloads run on `JobSystem.runIO`. Registry, sidecar and ledger updates happen in `runOnMain`. There is a progress bar and cancel button per download in a "Downloads" strip of the window. Cancel or failure deletes staging and leaves no partial files under the assets folder.
- **Command and undo:** the mutation is `ImportStoreAssetCommand` (an `AsyncCommand`, executed through the service layer). **Undo removes every imported file through `AssetRegistry.delete(file)`**, which drops the registry entry and the sidecar together. The empty `<slug>/` folder is deleted afterwards, and the ledger entry is removed. Raw file deletion is not used.
- **Re-import keeping GUIDs:** re-importing the same `(provider, sourceId, revision, variant)` offers "open existing" or "reimport". Reimport **overwrites the files in place and leaves their `.meta` sidecars untouched**, then calls `register(file)`. `register` reads the existing sidecar and keeps its GUID, because the path is unchanged. Files that no longer exist in the new version are removed with `AssetRegistry.delete`. New files get new sidecars. The ledger entry's `assetIds`, `revision` and `retrievedAt` are updated. Undo of a reimport restores the previous files from a backup copy in staging.
- **Units and axes:** importers already convert to metres, Y-up and right-handed (conventions.md). For USD, see §8.

### 7.1 Importable formats (v1)
- Imported files must match an existing importer. Today `AssetMeta.importerFor` treats `fbx` as `model`, but the editor's `FileType.ANIMATION` (`editor/data/FileType.kt`) and the Asset Browser's `AssetDiscovery` classify **every `.fbx` as an animation**. An FBX model from the store would land in the Animations tab.
- **v1 imports only glTF/GLB models plus textures** (PNG/JPG/HDR, which `TextureLoader` handles). Poly Haven (`gltf`), Sketchfab (glTF zip), ambientCG (texture sets) and converted USD (GLB) are all covered. Kenney/Quaternius packs import their glTF/GLB files only (§5.4, §5.5).
- Follow-up (AST, to be filed by the lead): classify `.fbx` as model vs animation by content (meshes vs animations only) so FBX packs become importable.

### 7.2 Caching (window)
- Search and details JSON: memory LRU plus a 24 h disk cache in `%LOCALAPPDATA%/Skate/assetstore-cache/`. Thumbnails: disk cache keyed by URL hash, max 200 MB with LRU eviction. Download URLs are never cached.

## 8. USD → glTF conversion (SimReady, optional for Poly Haven `usd`)

SimReady content is OpenUSD (`.usd`/`.usda`/`.usdc`, maybe `.usdz`). It often uses MDL/OmniPBR materials, Z-up, centimetres (`metersPerUnit = 0.01`) and UsdPhysics schemas. We convert once, at import time, so the runtime never sees USD.

```kotlin
interface UsdConverter {
    val id: String
    fun isAvailable(): Boolean
    fun command(input: Path, output: Path): List<String>   // pure → unit-tested
    fun run(input: Path, output: Path, cancel: CancelToken): ConversionResult   // process + timeout + log capture
}
```
Candidates, in recommended order:
1. **Headless Blender** (recommended): `blender -b --factory-startup --python <bundled script> -- in.usd out.glb`. The script calls Blender's USD importer, then its glTF exporter (Y-up, metres). It reuses the Blender locator and process runner from **AST-19**. `skate-assets` owns that locator, and the store only consumes it. Blender converts `UsdPreviewSurface` materials. ⚠ UNVERIFIED: how Blender handles MDL/OmniPBR. Expect a fallback to defaults.
2. **`usd2gltf`** (Python, needs `usd-core` from pip): used when the user has Python configured. ⚠ UNVERIFIED: maintenance state and material coverage.
3. **Assimp USD importer** (no external tool): Assimp 5.4 added an experimental USD importer (via tinyusdz). ⚠ UNVERIFIED: whether LWJGL 3.3.6's Assimp natives are built with it enabled (it is off by default upstream). Spike: try `aiImportFile` on a sample `.usda`.

If no converter is available, the store marks SimReady items as "needs Blender" and links to the Blender path setting.

Post-processing after conversion:
- The converter writes only into a fresh output directory inside staging. Every produced path (the `.glb`, plus any sidecar textures or `.bin`) goes through the same sanitiser as zip entries (no `..`, no absolute or drive paths, no symlinks that resolve outside staging) before `importPlan` sees it.
- Validate the output loads in `AssimpLoader`. Apply `metersPerUnit`/`upAxis` scaling if the converter did not.
- Materials: map OmniPBR/`UsdPreviewSurface` inputs (diffuse, roughness, metallic, normal) to our material keys. Unmapped MDL goes to the default material with a warning in the import report.
- UsdPhysics (colliders, mass) is kept in the import report only. Mapping to Skate collider components is a follow-up for PHY (not v1).
- The ledger records `"converted USD -> glTF via <converter>"` (CC-BY-4.0 requires indicating changes).

## 9. Window UX (EDT conventions)

`Window > Asset Store` (stable ID `editor.assetstore.title`):
- Top bar: provider combo (with a login state icon), search field, kind filter, license filter, sort.
- Main area: a thumbnail grid with infinite scroll (cursor paging). Each tile carries a license badge.
- Details pane: large preview, title, author link, license block (name, link, badge explanation), size, variant/resolution picker, **Import**, "Open on website".
- Downloads strip: progress and cancel. Attribution tab: the ledger table.
- Provider footer: "Powered by Poly Haven" and similar credits.
- Every label has a `StringManager` key in `editor/assetstore` (EN + FR), and buttons are `tipped(...)`. No mutations in the window: import and delete go through `AssetStoreService` to commands.
- Offline or provider down: an inline error per provider, never a modal. The other providers keep working.

## 10. Testing (unit only, no sockets)

- Parser tests per provider against recorded JSON fixtures (search, details, files/downloads).
- `LicenseCatalog` mapping (every slug from each provider, unknown leads to blocked).
- `DownloadPlan` path sanitisation, zip-slip and zip-bomb limits, md5 verification (temp dirs). The same sanitiser runs on converter output (a fake converter that emits `../x.glb` is rejected).
- `ImportPlan` for glTF + includes, a texture set to material slots, and a Kenney zip matched to its catalog entry.
- Ledger encode/decode (unknown keys ignored, `schema` round-trip), rebuild from metas, removal on delete.
- `AssetRegistry.scan()` of a project with `.skate/attribution.json` and `.skate/cache/store/**` registers neither.
- Credits: a packed `Set<File>` is mapped to GUIDs via `registry.idOf(relativePath)`, and only the matching ledger entries are emitted.
- Import target is `ProjectLayout.assetsDir(projectDir)/Store/<provider>/<slug>/`. Each file is registered via `register(file)`, and its sidecar carries the `store.*` settings.
- Reimport in place keeps every surviving file's GUID. Removed files go through `AssetRegistry.delete`.
- Format gate: `.fbx`/`.obj`/`.blend` entries are rejected in v1, and `.gltf`/`.glb` plus textures are accepted.
- `ImportStoreAssetCommand` execute/undo with a fake pipeline. Undo calls `AssetRegistry.delete` for every imported file (no sidecar left behind). `CredentialStore` path outside the project, plus header redaction.
- `UsdConverter.command(...)` argument building. The process runner is behind an interface (no Blender in tests).
- `StoreHttp` fake: 429 + `Retry-After` handling, cancellation.

## 11. Phasing (proposed follow-up tasks)

| Task | Scope | Deps |
|---|---|---|
| AST-16a | SPI, registry, `StoreHttp`, pipeline, ledger, window skeleton, **Poly Haven** | AST-14 (`ProjectLayout`) |
| AST-16b | ambientCG + texture-set → material import | 16a |
| AST-16c | Sketchfab OAuth2 + `CredentialStore` + download | 16a, Q1 |
| AST-16d | Kenney/Quaternius catalogs + "Import downloaded pack" | 16a |
| AST-16e | SimReady (HF Hub) + `UsdConverter` (Blender) | 16a, AST-19 |
| AST-16f | `CREDITS.txt` on export + warnings | 16a, AST-15 |

## 12. Open questions for the human / lead

1. **Sketchfab app registration:** who registers the OAuth client (needs the project's or human's Sketchfab account), and is a loopback redirect acceptable? Without it, v1 uses the "paste token" fallback.
2. **Credential storage:** is a plain-text user-only file in `%APPDATA%` acceptable for v1, or should we add Windows Credential Manager (JNA dependency) now?
3. **ND licenses and format conversion:** should we block ND assets that need conversion (USD to glTF), or warn only? I propose warning only, with the conversion recorded.
4. **Kenney/Quaternius:** is the curated catalog with manual download acceptable, or should we ask Kenney and Quaternius for permission to link their zips directly?
5. **Blender as the USD converter:** OK to make SimReady support depend on AST-19's Blender integration?
6. Should Poly Pizza be added as a later provider (needs a user API key)?
