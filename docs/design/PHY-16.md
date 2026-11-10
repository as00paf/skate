# docs/design/PHY-16.md

## Task: Implement Water Volumes and Buoyancy
## Based on: REN-29

This document outlines the design for implementing the water volume and buoyancy system within the Skate Physics framework.

### Goal
Implement buoyancy and drag calculations for water volumes, following the design specified in REN-29.

### Implementation Details
1.  **Water Volume Representation:** Define a spatial volume structure representing water bodies. This volume should be defined by surface geometry and depth.
2.  **Buoyancy Calculation:** Implement a buoyancy force calculation based on the submerged volume of the rigid body within the water volume. This requires accurate volume intersection testing between the body's shape and the water volume.
3.  **Drag Calculation:** Implement dynamic drag forces acting on the rigid body, proportional to the submerged velocity and the water's density/viscosity.
4.  **Integration:** These forces must be integrated into the rigid body's physics simulation loop, affecting its linear and angular momentum, and must be handled via the `PhysicsSystem`.
5.  **Surface Interaction:** Define the interaction model for the water surface (e.g., handling surface tension, foam generation, and surface reflections/refractions, which ties into the `REN-27` rendering goals).

### Acceptance Criteria
*   **Buoyancy Accuracy:** Rigid bodies placed in water float according to Archimedes' principle with high accuracy.
*   **Drag Realism:** Drag forces are realistic based on water density and velocity.
*   **Test Coverage:** Unit tests must verify the buoyancy and drag calculations for various body shapes and water depths.

###Dependencies
*   REN-29 (Vegetation and water design)
*   REN-27 (Rendering goals for water surface)
*   PHY-15 (Heightfield collider, for accurate immersion testing)