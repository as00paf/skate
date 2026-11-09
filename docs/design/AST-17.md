# AST-17 — City generation from OpenStreetMap (design note)

Status: **proposal, awaiting human approval**. No code has been written.

## Goal

Evaluate OSM2World as the open-source alternative to commercial OSM city tools for Skate's asset pipeline. The goal is a workflow where users can pick an area on a map, generate a 3D city scene with roads, buildings and terrain, import it into a Skate project with colliders and materials, and handle licensing (ODbL attribution) correctly.

## Decision summary

| Topic | Decision |
|---|---|
| Primary tool | **OSM2World** (Java, Apache-2.0 code, ODbL data). It generates glTF 2.0 from OSM data via a Java-based rendering pipeline. |
| Area picker | A map widget in the editor where users draw a bounding box or polygon on an embedded OSM tile layer; the bounds are sent to OSM2World's Overpass API query. |
| Import format | glTF 2.0 (`.gltf`/`.glb`) — already supported by AST-14's loader. Buildings, roads and terrain come as separate meshes with material names derived from OSM tags. |
| Colliders | Auto-generated convex hulls for buildings; road surfaces get flat box colliders; terrain gets a heightfield (PHY-15). Users can adjust collider density in the import dialog. |
| Materials | Basic PBR materials mapped from OSM tags: `building` → grey concrete, `road` → asphalt, `grass` → green diffuse, `water` → blue reflective. Custom materials can be assigned per-tag in a mapping file. |
| Licensing | ODbL requires attribution. The import wizard shows an attribution dialog; the generated project includes an `ATTRIBUTION.md` with OSM contributors and data date. Exported games include the attribution in a visible credits screen or metadata file. |

## 1. Tool evaluation: OSM2World

### 1.1 Facts checked on 2026-11-07

| Property | Value |
|---|---|
| Repository | [osm2world/osm2world](https://github.com/osm2world/osm2world) |
| License (code) | Apache-2.0 |
| Data license | ODbL (Open Database License) — users must attribute OpenStreetMap contributors |
| Language | Java 8+ |
| Output formats | glTF 2.0, OBJ, Collada (.dae), SVG (2D map) |
| Rendering engine | Custom Java-based renderer (not Three.js or Babylon); uses LWJGL-like OpenGL concepts internally but is a standalone tool |
| Build | Gradle; `gradlew.bat shadowJar` produces a single JAR |
| CLI | `java -jar osm2world.jar -bounds <minLon>,<minLat>,<maxLon>,<maxLat> -o <outputDir>` |
| Overpass API | Uses Overpass Turbo to fetch OSM data for the given bounds; configurable via a template file |

### 1.2 Strengths

- **Java-based**: easy to embed or invoke from the Skate editor (same JVM, no native deps).
- **glTF output**: directly consumable by AST-14's loader without format conversion.
- **Active development**: regular releases; large community.
- **Configurable**: building height rules, road classification, terrain generation are all tunable via configuration files.

### 1.3 Weaknesses / Risks

- **Large output**: a medium city (e.g., 5 km × 5 km) can produce thousands of meshes and hundreds of MB of textures. Requires LOD/instancing strategies (REN-28).
- **ODbL compliance**: attribution is mandatory; users may not understand the requirement. Need clear UX for this.
- **No built-in collider generation**: OSM2World outputs geometry only; colliders must be generated in Skate post-import.
- **Texture quality**: default textures are procedural and low-resolution. Users will likely want to replace them with PBR materials (AST-16 can provide some).

## 2. Workflow design

### 2.1 Editor integration

```
┌─────────────────────────────────────────────┐
│  Generate City from OSM                     │
│                                             │
│  Map preview:                               │
│  ┌───────────────────────────┐              │
│  │                           │              │
│  │   [draw bounding box]     │              │
│  │                           │              │
│  └───────────────────────────┘              │
│                                             │
│  Bounds: -74.0, 40.7, -73.9, 40.8           │
│  (Manhattan example)                        │
│                                             │
│  Options:                                   │
│  [x] Include buildings                      │
│  [x] Include roads                          │
│  [x] Include terrain                        │
│  [ ] Include vegetation                     │
│  Building height: Default / Custom (m)      │
│  Collider density: Low / Medium / High      │
│                                             │
│  Attribution preview:                       │
│  "Data © OpenStreetMap contributors"        │
│                                             │
│  ┌──────────┐ ┌──────────┐                 │
│  │ Cancel   │ │ Generate →│                 │
│  └──────────┘ └──────────┘                 │
└─────────────────────────────────────────────┘
```

### 2.2 Steps

1. **User opens "Generate City" dialog** in the editor (Asset Browser context menu or File > Import > City from OSM).
2. **Map widget** shows an embedded OSM tile layer; user draws a bounding box.
3. **OSM data fetch**: query Overpass API for the selected bounds, download `.osm.pbf`.
4. **Invoke OSM2World**: run `java -jar osm2world.jar` with the `.osm.pbf`, output directory, and options (building height rules, terrain generation). This may take 1–5 minutes depending on area size.
5. **glTF import**: scan the output for `.gltf`/`.glb` files; import each as a Skate asset using AST-14's loader.
6. **Collider generation**: post-process imported meshes to generate colliders based on user-selected density (Low = one collider per building, Medium = simplified convex hulls, High = per-face triangulation).
7. **Material mapping**: apply default material mappings from OSM tags; allow the user to customize via a tag-to-material lookup table.
8. **Attribution**: generate `ATTRIBUTION.md` with OSM data date and contributor list; prompt user to confirm attribution placement in their project.

### 2.3 Configuration file (OSM2World)

```json
{
  "buildingHeight": {
    "mode": "default",
    "customHeight": null,
    "useAveHeight": true
  },
  "terrain": {
    "enabled": true,
    "resolution": 64,
    "heightScale": 1.0
  },
  "roads": {
    "includePaths": true,
    "roadMaterials": ["asphalt", "concrete"]
  },
  "vegetation": {
    "enabled": false,
    "density": 0.5
  }
}
```

## 3. Collider generation strategy

### 3.1 Building colliders

- **Low density**: one bounding-box collider per building mesh (fastest, least accurate).
- **Medium density**: convex hull of each building's vertices (better fit, moderate cost).
- **High density**: triangulated mesh collider (most accurate, slowest to generate and collide against).

### 3.2 Road colliders

Roads are flattened to a single plane; each road segment gets a box collider matching its width and length. Adjacent segments can be merged into compound shapes for performance.

### 3.3 Terrain collider

Terrain is exported as a heightmap by OSM2World (if enabled). PHY-15's heightfield shape is used directly. Resolution matches the terrain chunk size from REN-28.

## 4. Material mapping

Default tag-to-material mappings:

| OSM tag | Material type | Diffuse | Roughness | Notes |
|---|---|---|---|---|
| `building` | Concrete | Grey (#808080) | 0.9 | Flat shading; height from OSM `height` or `levels` tags |
| `highway=road` | Asphalt | Dark grey (#333333) | 0.8 | Road surface with lane markings if available |
| `natural=grass` | Grass | Green (#4a7c3f) | 1.0 | Terrain layer |
| `water` | Water | Blue (#2980b9) | 0.1 | Reflective; transparent |
| `landuse=residential` | Residential ground | Light grey (#d5d5d5) | 0.9 | Default for untagged ground |

Users can override mappings via a project-level `osm-materials.json`:

```json
{
  "building": { "material": "assets/mat/brick-concrete.mat" },
  "highway=residential": { "material": "assets/mat/residential-road.mat" }
}
```

## 5. Licensing and attribution

### 5.1 ODbL requirements

- **Attribution**: must credit OpenStreetMap contributors.
- **Share-alike**: derivative works (the generated city scene) must be shared under ODbL or compatible license.
- **No restriction on use**: commercial use is allowed.

### 5.2 UX for attribution

1. During import, show an **Attribution Summary** panel:
   - "This city data is from OpenStreetMap (ODbL). Contributors: [list of top contributors for this area]."
   - "Data date: YYYY-MM-DD"
   - Checkbox: "I understand and agree to include attribution in my project."

2. Generate `ATTRIBUTION.md` in the project root:
   ```markdown
   # OpenStreetMap Attribution

   This project uses city data generated from OpenStreetMap (OSM).

   © OpenStreetMap contributors (ODbL 1.0)
   Data date: 2026-11-07

   Generated by Skate Engine's OSM City Generator.
   ```

3. For exported games, include attribution in the credits screen or a `credits.txt` file bundled with the export.

## 6. Test plan

1. **Bounds query**: draw a small bounding box (e.g., 0.1° × 0.1°), verify OSM data is fetched correctly and contains expected features.
2. **OSM2World invocation**: run OSM2World with default options; verify glTF output exists and loads in the editor without errors.
3. **Collider generation**: import a test city with each density setting; verify collider count and accuracy (visual inspection + physics tests).
4. **Material mapping**: verify that building meshes get concrete materials, roads get asphalt, etc. Test custom material overrides.
5. **Attribution**: generate attribution for a known area; verify `ATTRIBUTION.md` contains correct contributor names and data date.
6. **Large area performance**: test with a 10 km × 10 km area; measure generation time and output size; verify the editor handles it without freezing (use progress bar).

## 7. Risks

- **OSM2World JAR size**: ~50 MB; embedding it in the editor increases install size. Consider downloading on first use or bundling as an optional module.
- **Overpass API rate limits**: large queries may hit rate limits. Add caching (store downloaded `.osm.pbf` files per-bounds) and a "download only" mode for manual processing.
- **Mesh count explosion**: a dense city can produce 10,000+ meshes. Must implement instancing/LOD early to avoid performance issues.
- **ODbL compliance confusion**: users may not understand attribution requirements. Provide clear documentation and automated checks (warn if `ATTRIBUTION.md` is missing at export time).

## 8. Decisions

Human decisions recorded on 2026-11-07:

1. **Other OSM tools**: OSM2World was just a suggestion; prioritize the most frictionless, user-friendly approach. The tool choice may need to be reconsidered based on this criterion.
2. **Single asset vs chunks**: Split generated city into chunks (like REN-28 terrain).
3. **Simplify option**: Yes — add a mesh count reduction option during generation.
4. **OSM data updates**: No update mechanism; static snapshots only.

## 9. Open questions

_No remaining open questions._
