# 🛹 SkateSim Obstacle Library

This document outlines the prefabs available in the Level Editor, categorized by geometry type and interaction physics.

---

## 🏙️ Street Category

*Architecture-based elements with linear edges and flat surfaces.*

### Ledges & Platforms

- **Manual Pad**: A low-profile platform (4–10" height).
    - *Physics:* High friction top, grindable edges.
- **Standard Ledge**: Knee-high concrete block.
- **Hubba**: A ledge that follows the slope of a stair set.
- **Picnic Table**: A high-level obstacle with a top surface and two side benches.
- **Jersey Barrier**: Steep concrete traffic barrier used for "wallies" or grinds.

### Rails

- **Flat Rail**: A horizontal metal bar (Round or Square profile).
- **Handrail**: A slanted rail used for descending stairs or banks.
- **Kinked Rail**: A rail with height changes (e.g., Flat-Down-Flat).
- **Pole Jam**: A short rail angled steeply out of the ground.

### Gaps & Sets

- **Stair Set**: Configurable count (3-stair, 5-stair, etc.).
- **Euro Gap**: A bank that gaps up to a higher platform.
- **Flat Gap**: Two platforms separated by a void.

---

## 🛹 Transition Category

*Curved surfaces designed for vertical momentum and "flow" lines.*

### Ramps

- **Quarter Pipe**: Single curved transition from flat to vertical.
- **Half-Pipe**: Two quarter pipes facing each other with a flat bottom.
- **Vert Ramp**: Massive transition reaching a true 90-degree vertical extension.
- **Mini-Ramp**: Smaller half-pipe (no vertical section).
- **Bank**: A flat incline (no curve).
- **Kicker**: Small launch ramp for horizontal distance.

### Complex Transition

- **Bowl**: Enclosed, multi-sided transition pit.
- **Spine**: Two quarter pipes placed back-to-back with no deck.
- **Hip**: A corner where two transition walls meet at an angle.

---

## 🛠️ Hybrid Structures

*Multi-part prefabs for park centerpieces.*

- **Funbox**: Central platform with banks, a rail, and a ledge.
- **Pyramid**: A four-sided bank allowing multi-directional approaches.
- **A-Frame**: Two banks leaning against each other with a rail/ledge on top.
- **Wallride**: A vertical surface positioned near a kicker or bank.

---

## 🎨 Asset Metadata Tags

Use these tags in the `SceneSerializer` to define physics behavior:

| Tag         | Behavior                                              |
|:------------|:------------------------------------------------------|
| `GRINDABLE` | Allows the board trucks/deck to lock onto the edge.   |
| `SLIPPERY`  | Reduced friction (e.g., metal rails, waxed ledges).   |
| `WALLRIDE`  | Triggers specific sticky-physics for vertical riding. |
| `STICKY`    | High-friction surfaces (e.g., rubber, soft dirt).     |
---

## 📦 Prefab library (GPL-04)

The editor's **Prefabs** tab (Asset Browser) lists every entry of `game/prefabs/PrefabCatalog.kt` as a thumbnail tile.
Tiles are grouped as **Player**, **Street**, **Transition** and **Generic**. The same catalog drives the viewport's
right-click **Create Prefab** menu, prefab search and drag-and-drop. Click a tile to spawn at the origin, or drag it
onto the viewport to spawn at the drop point. The **Material** combo sets the surface. Rails stay metal, and generic
prefabs keep their own look.

| Section    | Prefabs                                                                                    |
|:-----------|:-------------------------------------------------------------------------------------------|
| Street     | Ledge, Hubba, Manual Pad, Grind Box, Funbox, Flat Rail, Handrail, Kinked Rail, 3-Stair, 5-Stair |
| Transition | Kicker, Bank, Quarter Pipe, Half Pipe, Bowl                                                |
| Generic    | Point Light, Spot Light, Crate, Barrel, Bench, Trigger Volume, Spawn Point                 |

Conventions:
- Sizes are in metres. Each footprint is centred on the object's origin, with the bottom at y = 0.
- Riding faces and down-slopes point toward **+Z**, the direction a rider rolls in with the GPL-14 orientation.
- Handrail and Hubba follow the 5-stair's step noses. Spawn them at the same Z as a 5-stair with x ≈ ±1.05.
- Obstacles are static bodies. Curved and stepped shapes use one convex-hull collider (`CustomCollider3D`) per
  convex piece, built from the same profile as the mesh.
- Rails, the hubba and the grind box are tagged `GRINDABLE`. Spawn points are tagged `SpawnPoint`; their transform
  gives the spawn position and the +Z heading.
- A trigger volume is an invisible 2 m static box with `isTrigger` set.
- Collider sizes use the model's own units, like the mesh. Bullet applies the transform scale to every collider shape,
  so a scaled prop (crate 0.4, barrel 0.3) keeps its collider and mesh in step. `PrefabColliderBoundsTest` checks that
  each library prop's collider bounds match its mesh bounds. This includes rail posts, which collide.

Generated meshes:
- `SkateparkShapes` (skate obstacles), `SkateboardShape` (the board) and the engine's `PropShapes` (bench, spawn
  marker) build meshes with `engine/assets/procedural/MeshBuilder`. The meshes are committed as OBJ files in `assets/obj/` and copied into
  every new project under `EngineDefaults/Models`.
- `SkateparkShapesTest` fails when a committed OBJ no longer matches its generator. After changing a shape,
  regenerate the files with:

```bash
cmd.exe /c "set SKATE_REGEN_OBSTACLES=1&& gradlew.bat :test --tests *SkateparkShapesTest*"
```

The skateboard (GPL-16) is a game-ready mesh of about 2.7K triangles, replacing an 858K-triangle download.
- **Parts:** a 0.81 m × 0.20 m deck with concave, kicktails and round nose and tail, two trucks, and four 60 mm
  wheels.
- **Frame:** origin at the wheels' ground contact, nose toward +Z.
- **Colours:** parts are coloured with vertex colours written into the OBJ (`v x y z r g b`): grip tape, maple edge,
  red graphic, metal and urethane. It needs no texture.
- **Fit:** the deck top (`DECK_TOP` = 0.096 m) matches the rider's `rideHeight`. The prefab's box collider spans from
  the ground to the deck top, between the kicktails.

To add a prefab:
1. Add a `PrefabType`.
2. Add its catalog entry.
3. Add a shape, if it is generated.
4. Add `Assets.Models`/`Assets.Bundled` paths.
5. Add the `prefab.<type>` string to both editor string files.
6. Regenerate the OBJ files.
