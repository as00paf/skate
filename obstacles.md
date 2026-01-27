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

| Tag | Behavior |
| :--- | :--- |
| `GRINDABLE` | Allows the board trucks/deck to lock onto the edge. |
| `SLIPPERY` | Reduced friction (e.g., metal rails, waxed ledges). |
| `WALLRIDE` | Triggers specific sticky-physics for vertical riding. |
| `STICKY` | High-friction surfaces (e.g., rubber, soft dirt). |