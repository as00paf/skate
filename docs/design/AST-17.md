# AST-17: City generation from OpenStreetMap (design note)

Status: **revision 2, awaiting the human's approval.** Revision 1 was rejected as not grounded in this codebase. It
treated AST-14 as a glTF loader, assumed colliders and terrain APIs that do not exist, and kept OSM2World although the
human asked to reconsider the tool. No code has been written.

## 0. Human decisions (recorded 2026-11-07, kept)

1. **Other OSM tools:** OSM2World was just a suggestion; prioritize the most frictionless, user-friendly approach. The
   tool choice may need to be reconsidered based on this criterion.
2. **Single asset vs chunks:** split the generated city into chunks (like REN-28 terrain).
3. **Simplify option:** yes, add a mesh-count reduction option during generation.
4. **OSM data updates:** no update mechanism; static snapshots only.

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
| Static colliders | `MeshCollider3D` (`ColliderShape.TriangleMesh`, static only, vertices **inline** in the scene JSON); `CompoundHullCollider3D`; `BoxCollider3D` | Inline vertices make city scenes huge (Q2). |
| Terrain | REN-28 is a **design** (approved decisions 2026-11-10, streaming revision pending): `TerrainComponent`, `HeightField`, chunked, `ColliderShape.Heightfield` proposed for PHY-15. Nothing implemented yet. | v1 does not depend on it (§6). |
| Model output | Loaders read `.obj`, `.gltf`/`.glb`, `.dae`, `.fbx` (Assimp, `AssimpLoader`); AST-22 path rules. | **No glTF/OBJ writer** in the repo. |
| Polygon triangulation | none | Needed for footprints with holes (courtyards, multipolygons). |
| Asset layout | AST-26: `Assets/Models/<asset>/` for multi-file models, `.skmat` in `Materials/`, data in `Data/`, prefabs in `Prefabs/` | none |

## 3. User flow

Menu: **File › Import › City from OpenStreetMap…** (also in the Asset Browser's Models tab context menu).

```
City from OpenStreetMap
  Where:  [ paste an openstreetmap.org link, or "lat, lon" ]    or   [ Search place… ]   (Nominatim, Q1)
  Size:   ( ) 250 m  (o) 500 m  ( ) 1 km  ( ) 2 km     (square around the point; max 4 km²)
  [Fetch]  → shows "1,842 buildings, 312 roads, 12 areas (snapshot 2026-11-10)"
  Preview: 2D outline drawn with ImGui (buildings grey, roads dark, parks green), with the chunk grid
  Include: [x] Buildings  [x] Roads & paths  [x] Parks & water  [x] Ground
  Simplify (decision 3):  ( ) None  (o) Medium  ( ) Strong     → shows triangle and object counts
  Colliders: [x] Buildings  [x] Ground & roads
  Attribution: "© OpenStreetMap contributors, ODbL 1.0" (always added; §7)
                                                  [Cancel] [Generate]
```

- **No embedded tile map in v1.** An interactive slippy map needs a tile renderer in ImGui, and the OSM tile servers'
  usage policy limits app use (attribution, a real user agent, no bulk use). A pasted link or coordinates plus the
  preview drawn from the fetched data cover picking without tiles (Q1).
- **Parsing a pasted link:** `https://www.openstreetmap.org/#map=17/48.8566/2.3522` → zoom/lat/lon;
  `?mlat=..&mlon=..` also works. Unit-tested parser.
- **Fetch:** the progress bar and Cancel come from `CancelToken`. The fetch runs on a `JobSystem` IO worker.
- **Generate:** builds chunks on workers, writes files, then one undoable command (pattern of
  `ImportStoreAssetCommand`) registers the files and adds the city to the open scene.

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

### 4.3 Geometry (pure functions, unit-tested; `engine/assets/osm/`)

- **Footprints:** ear-clipping triangulation with holes (Q3: port earcut, ISC license, or write one). Walls are quads
  per edge. Heights: `height` > `building:levels × 3 m` > 9 m default; `min_height` lifts the base. Normals and UVs
  are generated (wall UV in metres, so tiling textures look right).
- **Roads:** ribbons around the polyline, half-width per class (motorway 7 m … footway 1 m), mitred joins capped at
  2× width, a small Y offset per layer (road above area above ground) to avoid z-fighting.
- **Areas:** triangulated polygons at ground height + offset.
- **Ground:** one quad per chunk.

### 4.4 Chunks (decision 2)

- A square grid aligned to the city's origin. The chunk side is 256 m by default: a multiple of the REN-28 terrain
  chunk (64 quads × 1 m), so terrain and city chunks line up when REN-28 streaming loads chunks around the camera.
- A feature belongs to the chunk holding its centroid. Large features (long roads) are clipped at chunk borders, so
  each chunk is self-contained.
- Each chunk is one GameObject `Chunk_<x>_<z>`, under a parent `City <name>`, with:
  - `RenderComponent` → `Assets/Models/<city>/chunk_<x>_<z>.glb`, one mesh part per material (§5), so draw calls per
    chunk ≈ number of materials, not number of buildings;
  - its colliders (§4.6).

### 4.5 Simplify (decision 3)

| Level | Effect |
|---|---|
| None | every feature as mapped |
| Medium | Douglas–Peucker on footprints and roads (0.5 m); drop buildings < 15 m², paths shorter than 5 m; merge all features of a material per chunk (already the default) |
| Strong | 1.5 m tolerance; drop buildings < 40 m²; footways and service roads dropped; buildings lower than 4 m dropped |

The dialog shows triangle and object counts per level before generating (computed on the snapshot, cheap).

### 4.6 Colliders (real APIs)

- Buildings, roads, areas and ground are static, so each chunk gets **one** static collider over its walkable and
  solid geometry (buildings + roads + areas + ground), built from the same triangles as the render mesh:
  `ColliderShape.TriangleMesh` through `MeshCollider3D` (static only, exact, already supported by the Bullet and Jolt
  backends; `SimplePhysics3D` has no mesh shapes, Q4).
- Grindable curbs and ledges are not generated in v1.
- **Size problem:** `MeshCollider3D` stores `vertices`/`indices` inline in the scene JSON. A 256 m chunk can have
  50k triangles, megabytes per chunk in the scene. Proposed (Q2): `MeshCollider3D` gets an optional
  `meshRef: String?` (GUID or path of a model). When set, the collider triangles come from that model's mesh at load,
  and `vertices`/`indices` stay empty. The chunk's `.glb` (or a lighter `chunk_<x>_<z>.collision.glb` with Medium
  simplification) is the source.
  - This is a PHY/ECS change (component + loader hook): `NEEDS PHY`.
  - Without it, v1 falls back to `CompoundHullCollider3D` with one hull per building (2N points per footprint, convex
    footprints only; concave ones split by their triangulation) plus a ground `BoxCollider3D`. That is smaller but
    less exact.

### 4.7 Output in the project (AST-26 layout)

```
Assets/Data/<city>.osm.json                 snapshot (decision 4)
Assets/Models/<city>/chunk_<x>_<z>.glb      one per chunk (multi-file model folder rule)
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
- No separate `osm-materials.json` mapping file in v1 (Q5).

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
- ⚠ UNVERIFIED legal reading: a game that shows a city built from OSM is most likely a *Produced Work* under ODbL
  (attribution required, no share-alike on the game). The shipped snapshot and meshes may count as a *Derivative
  Database*, which would be share-alike. Revision 1 stated share-alike flatly. This needs a check before release
  (Q6). Recommended default: the snapshot (`Assets/Data/*.osm.json`) is not packed (nothing references it), so only
  geometry ships.

## 8. Slicing and owners

| Slice | Owner | Contents |
|---|---|---|
| AST-17a | skate-assets | Overpass client on the `StoreHttp` seam, snapshot, parser, projection, link parser, geometry (triangulation, extrusion, ribbons), simplify, chunking, `GlbWriter`, `.skmat`/prefab output, ledger entry. Pure functions + fixtures. |
| AST-17b | skate-editor | The dialog and 2D preview (ImGui draw list), progress/cancel, the undoable import command. |
| PHY | skate-physics | `MeshCollider3D.meshRef` (Q2). |
| Later | REN + AST | Elevation (§6), trees through REN-29 scatter. |

## 9. Tests (unit, fixtures, no sockets)

- Link and coordinate parser; projection round trip; Overpass JSON fixture (a small real extract saved under
  `src/test/resources/osm/`) → expected feature counts.
- Triangulation: squares, L-shapes, polygons with holes (area preserved, no inverted triangles).
- Extrusion heights from tags; road widths per class; simplify counts per level on the fixture.
- Chunk assignment and clipping at borders; every chunk's mesh within its bounds.
- `GlbWriter` output loads through `AssimpLoader` with the same vertex and index counts.
- Ledger entry written with the right license and asset IDs; credits include it.
- The HTTP fake for fetch, cancel and HTTP errors (`StoreHttpException`).

## 10. Open questions (with recommended answers)

1. **Place picking:** recommended for v1: a pasted openstreetmap.org link or "lat, lon", plus place search through
   Nominatim (one request per search, our user agent, inside its usage policy), plus the 2D preview. No tile map. An
   embedded map is a later EDT task if wanted.
2. **Collider size:** recommended: add `MeshCollider3D.meshRef` (PHY task) so city colliders come from the chunk model
   instead of inline vertices. Alternative: the compound-hull fallback of §4.6 only.
3. **Triangulation:** recommended: port earcut (small, ISC license, handles holes) into `engine/assets/osm/` with its
   license header, rather than adding a dependency.
4. **SimplePhysics3D:** it has no mesh shapes. Recommended: cities require Bullet or Jolt; the dialog warns when the
   project's backend is Simple.
5. **Material mapping file:** recommended: none in v1; the generated `.skmat` files are the styling surface.
6. **ODbL reading:** recommended: treat games as Produced Works (attribution through the ledger and credits), do not
   pack the raw snapshot, and get the reading confirmed before a commercial release.
