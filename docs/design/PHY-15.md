# docs/design/PHY-15.md

## Task: Implement Heightfield Terrain Collider
## Based on: REN-28

This document outlines the design for implementing the heightfield terrain collider within the Skate Physics system.

### Goal
Implement a heightfield collider shape across all physics backends, ensuring it correctly interacts with rigid bodies and character geometry based on the terrain heightmap.

### Implementation Details
1.  **Collider Shape:** The collider shape must be derived from the heightfield data, which represents the terrain elevation. This typically involves creating a complex, potentially non-convex, mesh or a set of planar/triangular primitives representing the terrain surface.
2.  **Backend Abstraction:** The `Physics3DFactory` must be extended to handle this terrain-based collider geometry across all supported backends (e.g., Bullet via libbulletjme). The geometry must be represented in a way that is efficiently handled by the underlying physics engine.
3.  **Collision Logic:** The collision logic must determine the intersection between a rigid body/collider and the heightfield surface. This involves checking the body's position against the heightfield elevation at the point of contact and defining the collision response (e.g., floor friction, ground reaction forces).
4.  **Dependencies:** This implementation relies on the ECS-14 deterministic RNG service for terrain seeding and the asset management system (`AST`) for loading the heightmap data.
5.  **Integration:** The terrain collider must be integrated into the `PhysicsSystem` queries and collision detection pipeline.

### Acceptance Criteria
*   **Shape Fidelity:** The collider accurately represents the terrain geometry provided by the heightfield.
*   **Collision Accuracy:** Rigid bodies and the character correctly collide with the terrain (e.g., standing on the ground, falling through the terrain).
*   **Test Coverage:** Unit tests must verify the collider's geometric properties and collision responses against various heightfield configurations.

### Dependencies
*   REN-28 (Terrain and biomes design)
*   ECS-14 (Deterministic seeding)
*   AST (Asset loading)