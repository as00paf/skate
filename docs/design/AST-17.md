# AST-17: City generation from OpenStreetMap (design note)

Status: **revision 3, awaiting the human's approval.** Revision 2's open questions were answered on 2026-11-10 (§0.1);
this revision designs those answers: an embedded map (§3.1), JTS for geometry (§4.3), `meshRef` colliders and a box
fallback for the Simple backend (§4.6). Revision 1 was rejected as not grounded in this codebase. No code has been
written.

## 0. Human decisions (recorded 2026-11-07, kept)

1. **Other OSM tools:** OSM2World was just a suggestion; prioritize the most frictionless, user-friendly approach. The
   tool choice may need to be reconsidered based on this criterion.
2. **Single asset vs chunks:** split the generated city into chunks (like REN-28 terrain).
3. **Simplify option:** yes, add a mesh-count reduction option during generation.
4. **OSM data updates:** no update mechanism; static snapshots only.

### 0.1 Answers to the revision 2 open questions (2026-11-10, verbatim)

1. Place picking: an EMBEDDED MAP NOW in the dialog (pan, zoom, draw/select the area), in addition to
   link/coords/search. Design the tile source and its usage policy (openstreetmap.org tiles forbid heavy or app use
   without permission; compare compliant options: a provider with an editor-friendly free tier, or vector/raster tiles
   we render from Overpass data, caching, attribution on the map), the ImGui map widget (EDT review), offline
   behaviour, and tests with no sockets.
2. Colliders: MeshCollider3D.meshRef (PHY task) as recommended.
3. Triangulation: ADD A LIBRARY instead of porting earcut: compare JTS, poly2tri-java and an earcut JVM port on
   license, size, holes and robustness; pick one.
4. Simple physics backend: BOX FALLBACK: one box per building, flat boxes for roads/areas, when the project uses Simple
   (with a note in the dialog).
5. No tag-to-material mapping file in v1; the generated .skmat files are the styling surface.
6. ODbL: agreed: Produced Work, attribution in ledger and credits, no raw snapshot packed; legal check before a
   commercial release.

## 1. Tool choice (decision 1)

"Frictionless" here means: no install, no second program, no account, works offline once fetched, and the result
lands in the project ready to play (rendered, collidable, credited).

| Option | Install for the user | Fit with Skate | Verdict |
|---|---|---|---|
| **A. Built-in generator** (Kotlin, in the editor): fetch OSM data from the Overpass API, build meshes in-process | none | Writes chunks directly in our layout, exact colliders, our materials, deterministic, unit-testable | **Recommended** |
| B. OSM2World CLI | download a large jar, Java on PATH (we already run a JVM) | One big model per run; chunking, colliders and materials redone after import. ⚠ UNVERIFIED: license (revision 1 said Apache-2.0; it should be checked before any bundling) | Rejected: extra tool, little gain over A for streets and buildings |
| C. Blender + an OSM add-on (e.g. blosm) through AST-19 | Blender **plus** an add-on, some features paid | AST-19 already runs Blender headless, but add-on install and versions are friction | Rejected for v1; possible "advanced" path later |
| D. Commercial city tools | purchase, account | none | Out of scope |

What A generates in v1 (the parts that matter for a skate game):

- buildings extruded from footprints (`height`, `building:levels`, `min_height`, roof as flat cap);
- roads, paths and sidewalks as ribbons (`highway=*` with a width per class, `width` tag when present);
- flat areas: parks/grass (`leisure=park`, `landuse=grass`), water, plazas (`area:highway`, `place=square`);
- a ground plane.

Not in v1: roof shapes, bridges and tunnels as 3D (flattened), trees (REN-29 scatter later), elevation (§6).

## 2. What already exists (and what does not)

| Need | Existing code | Gap |
|---|---|---|
| HTTP with a test seam, cancel, user agent | `StoreHttp` / `CancelToken` / `StoreHttpException` (`editor/assetstore/`, AST-16, on `integration/30g`) | Reused through the same interface; a new `OsmHttp` user agent string. |
| Background work | `JobSystem` (decode on workers, GPU on main: the AST rule) | none |
| Attribution | `AttributionLedger` (`.skate/attribution.json`, `LedgerEntry` with license, authors, `assetIds`), `entriesFor(ids)` for export credits (AST-16 §6.2), delete → ledger (AST-25) | An `osm` provider entry, no new format. |
| Static colliders | `MeshCollider3D` (`ColliderShape.TriangleMesh`, static only, vertices **inline** in the scene JSON); `CompoundHullCollider3D`; `BoxCollider3D` | Inline vertices make city scenes huge: `meshRef` (answer 2, §4.6). |
| Simple physics backend | `engine/physics3d/simple/SimplePhysics3D.kt` (PHY-05 spike): **every body is one bounding sphere** (`boundingRadius` of its largest collider, sphere-sphere contacts only, all pairs checked) | A `BoxCollider3D` acts as a sphere around the box, so the box fallback (answer 4) needs real static boxes in Simple (§4.6, `NEEDS PHY`). |
| HTTP caching data | `HttpResult.headers` (`StoreHttp`) already exposes response headers | Tile cache reads `Cache-Control`/`ETag` from it (§3.1). |
| Map widget | none (no tile code, no Web Mercator math, no vector tile decoder) | §3.1. |
| Terrain | REN-28 is a **design** (approved decisions 2026-11-10, streaming revision pending): `TerrainComponent`, `HeightField`, chunked, `ColliderShape.Heightfield` proposed for PHY-15. Nothing implemented yet. | v1 does not depend on it (§6). |
| Model output | Loaders read `.obj`, `.gltf`/`.glb`, `.dae`, `.fbx` (Assimp, `AssimpLoader`); AST-22 path rules. | **No glTF/OBJ writer** in the repo. |
| Polygon geometry | none (no triangulation, clipping or polygon repair) | JTS (answer 3, §4.3). |
| Asset layout | AST-26: `Assets/Models/<asset>/` for multi-file models, `.skmat` in `Materials/`, data in `Data/`, prefabs in `Prefabs/` | none |

## 3. User flow

Menu: **File › Import › City from OpenStreetMap…** (also in the Asset Browser's Models tab context menu).

```
City from OpenStreetMap
  Where:  [ link or "lat, lon" ] [Go]   [ Search place… ] (Nominatim)
  ┌──────────────────────────────────────────────────────────┐
  │  embedded map (§3.1): drag = pan, wheel = zoom,          │
  │  [▭ Select area] then drag a rectangle; corner handles   │
  │  resize it; the fetched data and chunk grid draw on top  │
  │                 © OpenFreeMap © OpenMapTiles © OSM contributors │
  └──────────────────────────────────────────────────────────┘
  Area: 640 m × 480 m (0.31 km², max 4 km²)   Size presets: [250 m] [500 m] [1 km] [2 km]
  [Fetch]  → "1,842 buildings, 312 roads, 12 areas (snapshot 2026-11-10)"
  Include: [x] Buildings  [x] Roads & paths  [x] Parks & water  [x] Ground
  Simplify (decision 3):  ( ) None  (o) Medium  ( ) Strong     → triangle and object counts
  Colliders: [x] Buildings  [x] Ground & roads
     ⓘ This project uses the Simple physics backend: colliders are boxes (one per building,
       flat boxes for roads and areas), not exact meshes.        (only with Simple; answer 4)
  Attribution: "© OpenStreetMap contributors, ODbL 1.0" (always added; §7)
                                                  [Cancel] [Generate]
```

- The map, the link/coordinates field, the presets and search all set the same selection (a lat/lon rectangle). Go
  and search results recentre the map; the presets put a square of that size around the map centre.
- **Parsing a pasted link:** `https://www.openstreetmap.org/#map=17/48.8566/2.3522` → zoom/lat/lon;
  `?mlat=..&mlon=..` also works. Unit-tested parser.
- **Fetch:** the progress bar and Cancel come from `CancelToken`. The fetch runs on a `JobSystem` IO worker.
- **Generate:** builds chunks on workers, writes files, then one undoable command (pattern of
  `ImportStoreAssetCommand`) registers the files and adds the city to the open scene.

### 3.1 Embedded map (answer 1)

#### Tile source: options compared

Checked 2026-11-10 against each provider's published terms.

| Option | Terms for our use | Fit | Verdict |
|---|---|---|---|
| `tile.openstreetmap.org` raster (OSMF) | Allowed for apps only with a distinct, stable User-Agent, visible attribution, honouring cache headers (≥ 7 days otherwise); **bulk/pre-emptive fetching and offline use prohibited**; heavy use harms the service and access "can be withdrawn without prior notice", commercial services specifically warned. | Simplest to draw (PNG) | **Rejected as the default**: no offline cache, revocable, and a shipped editor with many users is the "heavy use" the policy warns about. |
| MapTiler Cloud free plan | 100k requests/month, API key, "testing, PoC, prototyping, personal, or non-commercial use", logo on the map | Raster or vector | Rejected as default: needs a key per user, non-commercial. |
| Stadia Maps free plan | 200k credits/month, "Commercial use not allowed" | Raster or vector | Rejected as default (non-commercial). |
| Thunderforest Hobby | 150k tiles/month, sign-up/key, attribution required, commercial use not stated | Raster | Rejected as default (key; commercial unclear). |
| **OpenFreeMap** (`tiles.openfreemap.org`) | No registration, no API keys, **no limits on map views or requests, commercial use allowed**, attribution required ("OpenFreeMap © OpenMapTiles Data from OpenStreetMap"); the project is MIT-licensed and self-hostable; offline downloads limited to 3,000 tiles or 50 MB per download, max 4 parallel requests, unique User-Agent | **Vector tiles only** (Mapbox Vector Tile `.pbf`, OpenMapTiles schema, zoom 0–14; the URL comes from the TileJSON at `https://tiles.openfreemap.org/planet`, which names a dated planet build) | **Recommended default.** We render the tiles ourselves (below). |
| Tiles rendered from Overpass data | Overpass is a query API, not a tile service: a pan/zoom map would send many large bbox queries, against its usage policy, and is useless when zoomed out | Already used for the selected area | Used **only** for the overlay of the fetched area (buildings/roads drawn on top of the map), not for browsing. |
| User-supplied raster URL template (`https://…/{z}/{x}/{y}.png`) | The user's own provider and key, under that provider's terms | Raster | Offered as an advanced setting (`EditorSettings.osmTileUrl`), with an attribution text field that is required to save. |

#### Rendering vector tiles

- **Decode:** a small Mapbox Vector Tile decoder in `editor/assetimport/osm/map/` (protobuf varints, length-delimited
  fields, the `MoveTo`/`LineTo`/`ClosePath` geometry commands, zig-zag deltas). It is about 200 lines of Kotlin with
  no dependency. A full protobuf library is not needed to read one fixed message type (AST-18 is unrelated).
- **Layers drawn:** `water`, `landcover`/`landuse`/`park` (fills), `transportation` (lines, width by `class`),
  `building` (fills from zoom 14), `place` labels at low zoom. Fixed colours in a small style table; no MapLibre
  style JSON in v1.
- **Rasterise on a worker:** each tile becomes a 512 px `BufferedImage` drawn with Java2D (`Path2D` with even-odd fill
  handles holes, so the map itself needs no triangulation). This runs on a `JobSystem` worker. The image is uploaded
  as a GL texture on the main thread (the AST rule), and the widget draws textured quads with ImGui
  (`drawList.addImage`). Above zoom 14, z14 tiles are over-zoomed (re-rasterised at the larger scale).
- **Raster sources** (the user template) skip decode/rasterise: the PNG is decoded on the worker, uploaded on main.

#### Usage policy compliance (in code, not just docs)

- **User-Agent:** `SkateEngine/<version> (+https://github.com/as00paf/skate)`, stable, set by the tile client (not the
  JDK default). The same seam as `StoreHttp`, with its own UA constant (`OsmHttp.USER_AGENT`).
- **Only visible tiles** are requested: no prefetching of neighbours or other zooms, no "download area" button. At
  most 4 requests in flight (OpenFreeMap's limit for downloads; we apply it everywhere). Requests for tiles that
  scrolled out of view are cancelled (`CancelToken`).
- **Caching:** a disk cache under the editor's user data dir (`EditorPaths.userDataDir()/cache/tiles/<source>/<z>/<x>/<y>`)
  plus a memory LRU of textures (256 tiles). Expiry follows `Cache-Control: max-age`/`Expires`; without them, 7 days.
  Expired tiles are revalidated with `If-None-Match`/`If-Modified-Since` (from `HttpResult.headers`), never with
  `no-cache`. The cache is capped (200 MB, oldest evicted) and can be cleared from Editor Settings. The OpenFreeMap
  TileJSON is cached for a day; a new planet build simply starts new cache keys.
- **Attribution on the map:** drawn by the widget itself, bottom-right, always visible, not behind a toggle. For
  OpenFreeMap: "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"; for a user template: the text the user
  entered. Clicking it opens the copyright pages.
- The map's tiles are **never** used as game data and never packed: the city is built from the Overpass snapshot
  (§4.1), which is the ODbL data covered by §7.

#### The ImGui map widget (EDT review)

- **Pure state, unit-tested:** `MapView(centerLat, centerLon, zoom: Float)` with `WebMercator` math (lat/lon ↔ world
  pixels ↔ tile x/y at a zoom), `visibleTiles(viewSize)`, `pan(dxPx, dyPx)`, `zoomAt(cursorPx, wheel)` (zoom around
  the cursor, clamped 2..19), `fitBounds(rect)`. `AreaSelection(rect)` with drag-to-draw, corner/edge handles,
  move by dragging inside, and the 4 km² cap (the rectangle is clamped while dragging, with the size shown).
- **Drawing:** `MapWidget.render(view, selection, tiles, overlay)` uses one ImGui child region and its draw list:
  tiles, then the overlay of fetched features (buildings grey, roads dark, parks green, the chunk grid), then the
  selection rectangle and handles, then the attribution. Missing tiles show a neutral grid with a small spinner.
- **Input:** left-drag pans (or draws when "Select area" is on), wheel zooms, double-click zooms in, keyboard arrows
  pan when the map is focused. Texture IDs are released when tiles leave the LRU.
- **Owner:** the widget is generic (`editor/imgui/map/`), so skate-editor reviews it (EDT review requested in §8).
  The tile client, cache, decoder and rasteriser are AST code.

#### Offline behaviour

- Tiles already in the disk cache are shown (even expired ones, when revalidation fails). Missing tiles show the grid
  and a single "Map offline: showing cached tiles" line; no retry storm (retry on the next pan/zoom, at most every
  30 s).
- Link and coordinates work offline (no network needed to set the selection); search (Nominatim) and Fetch
  (Overpass) need the network and say so.
- **Generate from an existing snapshot** (`Assets/Data/<city>.osm.json`) works fully offline: the dialog can open on a
  snapshot (Asset Browser › snapshot › "Generate city…"), and the map is then optional.

#### Tests with no sockets

- `WebMercator` round trips, tile coverage of a view at several zooms, zoom-around-cursor keeps the point under the
  cursor fixed, area clamp at 4 km².
- MVT decoder on a small `.pbf` fixture in `src/test/resources/osm/` (layer names, feature counts, a polygon with a
  hole, a line) and on hand-built byte arrays for the command encoding.
- Rasteriser: a fixture tile renders to a `BufferedImage` with water/park/building pixels where expected.
- Tile cache with a fake `OsmHttp` and a fake clock: `max-age` honoured, 7-day default, conditional revalidation
  (304 keeps the file), cap eviction, offline fallback to expired files, at most 4 in flight, cancelled when out of
  view, the User-Agent header on every request, no request for tiles outside the view.

## 4. Data and generation

### 4.1 Fetch (static snapshot, decision 4)

- One Overpass QL query for the bounding box (`way`/`relation` with `building`, `highway`, the area tags of §1;
  `out geom;` so nodes come inline), sent to the public endpoint (`https://overpass-api.de/api/interpreter`,
  configurable in `EditorSettings`).
- The size is capped at 4 km² to stay inside the Overpass usage policy (⚠ UNVERIFIED: the current per-query limits).
- The raw JSON response is saved as the snapshot `Assets/Data/<city>.osm.json`. It is committed with the project.
  Re-generating with other options (simplify, layers) reads it and never refetches. There is no update mechanism; a
  new snapshot means a new import.
- Parsing: kotlinx JSON into `OsmWay(id, tags, points)` and `OsmArea(id, tags, outer, holes)`. Multipolygon
  relations are assembled from member ways.

### 4.2 Projection

Local metres around the chosen centre: equirectangular,
`x = R·Δλ·cos φ0`, `z = −R·Δφ`. The error is under 0.1 % at 2 km, which does not matter at game scale. +Y is up, −Z
is north, matching the editor's axes. The centre lat/lon is stored in the generated prefab's metadata, so a second
import of a nearby area can be aligned.

### 4.3 Geometry (pure functions, unit-tested; `editor/assetimport/osm/`)

The generator runs only in the editor, so it lives under `editor/`. Only its outputs (GLB, `.skmat`, prefab) reach
the game.

**Library (answer 3):**

| | JTS (`org.locationtech.jts:jts-core` 1.20.0) | poly2tri-java (`org.orbisgis:poly2tri-core` 0.1.2) | earcut JVM port (`org.maplibre:earcut4j` 3.0.0, fork of `io.github.earcut4j`) |
|---|---|---|---|
| License | EPL-2.0 **or** EDL-1.0 (BSD-style; we use it under EDL) | BSD-3-Clause | ISC |
| Size | about 1.2 MB jar, no dependencies, Java 8+ | about 62 KB | tens of KB |
| Holes | yes: `ConstrainedDelaunayTriangulator` (best quality) and `PolygonTriangulator` (ear clipping, joins holes to the shell) | yes (constrained Delaunay) | yes (hole indices) |
| Robustness on OSM data (self-touching rings, duplicate nodes, touching holes, slightly self-intersecting multipolygons) | **`GeometryFixer`** repairs invalid polygons first; robust predicates | **No duplicate points and no intersecting constraints**; input must be cleaned by another library first | Tolerant (never throws), but bad input gives wrong triangles silently |
| Maintenance | active (LocationTech, 1.20.0 in 2024) | orbisgis repo archived; last core release 2015 | maintained by MapLibre |
| Also gives us | chunk clipping (`intersection` with the chunk square), road ribbons (`buffer` of a line with flat caps and mitre joins), Douglas–Peucker (`DouglasPeuckerSimplifier`/`TopologyPreservingSimplifier`), area/centroid | triangulation only | triangulation only |

**Pick: JTS.** It is the only one that handles OSM's invalid polygons without a second library. It also replaces
clipping, buffering and simplification code we would otherwise write. Its size is acceptable because the dependency
is editor-only (below).

- **Not in the game jar:** `gameLibsJar` merges the whole `runtimeClasspath` today. JTS is excluded from it
  (`exclude("org/locationtech/jts/**")` in `gameLibsJar`) and an architecture guard test fails if any class outside
  `editor/` imports `org.locationtech.jts`. `tools/scripts/verify-game-jar.sh` checks the jar has no
  `org/locationtech/jts/` entries.
- **Footprints:** `GeometryFixer.fix(polygon)` → `ConstrainedDelaunayTriangulator.triangulate` for the roof cap (and
  for areas). Walls are quads per edge. Heights: `height` > `building:levels × 3 m` > 9 m default; `min_height` lifts the base. Normals and UVs
  are generated (wall UV in metres, so tiling textures look right).
- **Roads:** `LineString.buffer(halfWidth, BufferParameters(CAP_FLAT, JOIN_MITRE, mitreLimit = 2))`, half-width per
  class (motorway 7 m … footway 1 m), then triangulated like areas; a small Y offset per layer (road above area above
  ground) to avoid z-fighting. Overlapping roads of the same layer are unioned per chunk, so junctions have no
  overlapping triangles.
- **Areas:** triangulated polygons at ground height + offset.
- **Ground:** one quad per chunk.

### 4.4 Chunks (decision 2)

- A square grid aligned to the city's origin. The chunk side is 256 m by default: a multiple of the REN-28 terrain
  chunk (64 quads × 1 m), so terrain and city chunks line up when REN-28 streaming loads chunks around the camera.
- A feature belongs to the chunk holding its centroid. Large features (long roads, big parks) are clipped at chunk
  borders with JTS `intersection`, so each chunk is self-contained.
- Each chunk is one GameObject `Chunk_<x>_<z>`, under a parent `City <name>`, with:
  - `RenderComponent` → `Assets/Models/<city>/chunk_<x>_<z>.glb`, one mesh part per material (§5), so draw calls per
    chunk ≈ number of materials, not number of buildings;
  - its colliders (§4.6).

### 4.5 Simplify (decision 3)

| Level | Effect |
|---|---|
| None | every feature as mapped |
| Medium | `TopologyPreservingSimplifier` on footprints and roads (0.5 m); drop buildings < 15 m², paths shorter than 5 m; merge all features of a material per chunk (already the default) |
| Strong | 1.5 m tolerance; drop buildings < 40 m²; footways and service roads dropped; buildings lower than 4 m dropped |

The dialog shows triangle and object counts per level before generating (computed on the snapshot, cheap).

### 4.6 Colliders (real APIs)

The backend comes from the project (`Project.physicsBackend`, shared with AST-15/EDT-59; until that field lands,
`Physics3DFactory.defaultBackend`).

**Bullet and Jolt (answer 2):** each chunk gets **one** static collider over its walkable and solid geometry
(buildings + roads + areas + ground), built from the same triangles as the render mesh: `ColliderShape.TriangleMesh`
through `MeshCollider3D` (static only, exact, supported by both backends).

- `MeshCollider3D` stores `vertices`/`indices` inline in the scene JSON; a 256 m chunk can have 50k triangles. So
  `MeshCollider3D` gets an optional **`meshRef: String?`** (an asset GUID, resolved through `AssetRegistry`). When set,
  the triangles come from that model's meshes at load and `vertices`/`indices` stay empty in the JSON. The source is
  `Assets/Models/<city>/chunk_<x>_<z>.collision.glb`, written with Medium simplification whatever the render level,
  and without layer offsets.
  - PHY task (component field, load hook through the model loader, serialisation, a missing or unloadable model logs
    and leaves no collider): `NEEDS PHY`. AST-17 waits for it; no compound-hull fallback is designed any more.
- Grindable curbs and ledges are not generated in v1.

**Simple backend: box fallback (answer 4):**

- Buildings: **one box per building**, the footprint's minimum-area oriented rectangle (JTS
  `MinimumDiameter.getMinimumRectangle`), from base to roof height.
- Roads: **flat boxes** per road segment (between two simplified polyline points), oriented along the segment, width
  of the road class, 0.1 m thick, at the road's height. Areas: one flat box per area, its oriented rectangle (a large
  concave park becomes several boxes: one per piece of a coarse split of its triangulation, capped at 8 per area).
  Ground: one flat box per chunk.
- **Each box is its own child GameObject** (`Transform` with the yaw, `BoxCollider3D(halfExtents)`, static
  `RigidBody3D`), grouped under `Colliders` in each chunk. One box per GameObject is required, because Simple builds
  one body per GameObject (from its largest collider).
- **Gap found in the code:** `SimplePhysics3D` approximates **every** body by a bounding sphere, so these boxes
  would collide as spheres (a 40 m road box becomes a 20 m sphere). The fallback is only correct once Simple gets
  static oriented boxes (sphere-vs-OBB contacts and rays) and a broad phase (it tests all pairs, and a city adds
  thousands of static bodies): `NEEDS PHY` (Q-N1).
- The dialog shows the note from §3 when the project uses Simple, and the generated prefab is tagged with the backend
  it was built for. Switching backend later means re-generating from the snapshot (offline, §3.1).

### 4.7 Output in the project (AST-26 layout)

```
Assets/Data/<city>.osm.json                 snapshot (decision 4)
Assets/Models/<city>/chunk_<x>_<z>.glb      one per chunk (multi-file model folder rule)
Assets/Models/<city>/chunk_<x>_<z>.collision.glb   collider source for meshRef (Bullet/Jolt only, §4.6)
Assets/Materials/<city>_{wall,roof,road,path,grass,water,plaza,ground}.skmat
Assets/Prefabs/<city>.prefab                parent + chunks, so the city can be placed in several scenes
```

- An instance of the prefab is added to the open scene (one undoable command). `<city>` is the slug of the place name
  or of the coordinates.
- **GLB writer:** a small `GlbWriter` (`engine/assets/gltf/`): positions, normals, UVs, indices, one primitive per
  material, materials by name only (the `.skmat` binds textures). About 200 lines, tested by loading its output back
  through `AssimpLoader`. It is needed because the repo has no model writer.

## 5. Materials

- `.skmat` (`MaterialAsset`, PBR schema) per category. Textures come from the engine defaults (AST-26:
  `engine:Textures/asphalt.png`, `concrete_simple.png`, …). Grass and water are flat colours (`baseColor`).
- `building:colour` / `roof:colour` tags are applied as vertex colours when present. ⚠ The default shader's support
  for vertex colours must be checked by REN; otherwise the colours are ignored in v1.
- Users restyle a city by editing those few `.skmat` files (or swapping in store materials, AST-16): every chunk uses
  them.
- No tag-to-material mapping file in v1 (answer 5): the generated `.skmat` files are the styling surface.

## 6. Elevation (later, depends on REN-28 + PHY-15)

OSM has no terrain heights. v1 generates on a flat ground plane.

A later phase:

1. fetches a DEM for the box (e.g. the open "Terrain Tiles" set; ⚠ UNVERIFIED: source, license and attribution
   terms);
2. writes a REN-28 `.skterrain` data asset (`heights.r16`), aligned to the city chunk grid;
3. drapes roads and building bases on `HeightField.heightAt`.

The terrain collider is then PHY-15's `ColliderShape.Heightfield`, and the city ground quads are dropped.

## 7. Licensing and attribution (ODbL)

- Every import adds an `AttributionLedger` entry:
  - `provider = "osm"`, `sourceId = <bbox + snapshot date>`, `title = <place>`;
  - `license = LedgerLicense("ODbL-1.0", "https://opendatacommons.org/licenses/odbl/1-0/")`;
  - `authors = [LedgerAuthor("OpenStreetMap contributors", "https://www.openstreetmap.org/copyright")]`;
  - `retrievedAt`, and `assetIds` = the snapshot, the chunks and the materials.
  - Export credits (AST-16 §6.2, `entriesFor`) then include "© OpenStreetMap contributors" whenever a city chunk is
    packed. Deleting the city removes the entry (AST-25).
- The dialog states the attribution line; there is no "I agree" checkbox (nothing to agree to beyond attribution).
- **Reading agreed (answer 6):** a game that shows a city built from OSM is treated as a *Produced Work* under ODbL:
  attribution in the ledger and in the export credits, no share-alike on the game. The raw snapshot
  (`Assets/Data/*.osm.json`) is **never packed**: `AssetsPacker` excludes `*.osm.json` explicitly (not just because
  nothing references it), with a unit test. A legal check is required before a **commercial** release; the export
  wizard does not enforce it, but the credits line is mandatory and cannot be removed while a city chunk is packed.
- The **map tiles** (§3.1) are not part of the game; their attribution is shown on the map in the editor only.

## 8. Slicing and owners

| Slice | Owner | Contents |
|---|---|---|
| AST-17a | skate-assets | Overpass client on the `OsmHttp` seam, snapshot, parser, projection, link parser, JTS dependency (editor-only, excluded from `gameLibsJar`, guard test, `verify-game-jar.sh` check), geometry (repair, triangulation, extrusion, road buffers, clipping), simplify, chunking, `GlbWriter`, collision GLB, box fallback, `.skmat`/prefab output, ledger entry, `*.osm.json` pack exclusion. Pure functions + fixtures. |
| AST-17c | skate-assets | Map data side (§3.1): OpenFreeMap TileJSON + tile client, disk/memory cache with HTTP caching rules, MVT decoder, Java2D rasteriser, raster template source, offline handling, Editor Settings entries (tile source, URL template + attribution, clear cache). |
| AST-17b | skate-editor | Generic map widget (`editor/imgui/map/`: `MapView`, `WebMercator`, `AreaSelection`, drawing and input), the dialog, the overlay of fetched data, progress/cancel, the Simple-backend note, the undoable import command. **EDT review of the widget** (answer 1). |
| PHY-a | skate-physics | `MeshCollider3D.meshRef` (answer 2). |
| PHY-b | skate-physics | `SimplePhysics3D`: static oriented boxes (contacts and rays) and a broad phase, so the box fallback behaves as boxes (answer 4, §4.6). |
| Later | REN + AST | Elevation (§6), trees through REN-29 scatter. |

## 9. Tests (unit, fixtures, no sockets)

- Map: see §3.1 "Tests with no sockets".
- Link and coordinate parser; projection round trip; Overpass JSON fixture (a small real extract saved under
  `src/test/resources/osm/`) → expected feature counts.
- Geometry through JTS: squares, L-shapes, polygons with holes, a self-touching OSM ring and a duplicate node (repaired,
  area preserved, no inverted triangles); road buffers have the class width; clipping keeps every triangle inside its
  chunk.
- Box fallback: one oriented box per building (yaw and extents of an L-shaped and a rotated footprint), one flat box
  per road segment, one GameObject per box.
- `AssetsPacker` never packs `*.osm.json`.
- Architecture guard: no `org.locationtech.jts` import outside `editor/`.
- Extrusion heights from tags; road widths per class; simplify counts per level on the fixture.
- Chunk assignment and clipping at borders; every chunk's mesh within its bounds.
- `GlbWriter` output loads through `AssimpLoader` with the same vertex and index counts.
- Ledger entry written with the right license and asset IDs; credits include it.
- The HTTP fake for fetch, cancel and HTTP errors (`StoreHttpException`).

## 10. Open questions (revision 3, with recommended answers)

Revision 2's questions are answered in §0.1.

1. **Q-N1 — Simple backend boxes:** `SimplePhysics3D` treats every body as a sphere, so the box fallback needs PHY-b
   (static oriented boxes + broad phase) to behave as boxes. Recommended: AST-17 depends on PHY-b for the Simple path;
   until it lands, the dialog's Simple note also says the colliders are approximate. Alternative: limit the fallback to
   buildings only (fewer, more compact bodies) until PHY-b.
2. **Q-N2 — default tile source:** recommended: OpenFreeMap vector tiles rendered by us (free, no key, commercial use
   allowed), plus an optional user raster template with mandatory attribution text. `tile.openstreetmap.org` is not
   offered as a preset.
3. **Q-N3 — User-Agent contact:** the tile, Nominatim and Overpass requests carry
   `SkateEngine/<version> (+https://github.com/as00paf/skate)`. Recommended: the repository URL (public contact
   point); or should it be an email address?
4. **Q-N4 — Simple path when the backend changes later:** recommended: no automatic conversion; re-generate from the
   snapshot (offline, a few seconds). Alternatively the prefab could carry both collider sets, at the cost of size.
