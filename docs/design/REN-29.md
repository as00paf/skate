# Design Specification for REN-29: Vegetation and Water
**Task ID:** REN-29
**Priority:** P2
**Dependencies:** REN-27 (Environment reflections)

## 1. Goal
To implement realistic, performant, and visually appealing environmental elements: dense vegetation (trees/grass), and dynamic water surfaces within the Skate engine's scene rendering pipeline. This aims to greatly enhance the realism of the Feature Showcase area.

## 2. Core Components & Systems

### A. Vegetation System (`skate-animation`, `skate-render`, ECS)
1.  **Instancing/LOD:** Must use GPU instancing for high density (e.g., grass patches, small shrubs). Large objects like trees should employ Level of Detail (LOD) techniques based on camera distance from the entity's center point.
2.  **Wind Simulation:** A global or per-area wind direction and speed must be defined (potentially linked to weather/time-of-day system). This force vector should drive subtle animation for all vegetation meshes via bone transforms or vertex shaders.
3.  **Biome Mapping:** Vegetation density, species type, and height scale must be driven by a biome map generated during the asset setup (related to REN-28).

### B. Water Surface Shader (`skate-render`, `skate-vulkan`)
1.  **Reflection/Refraction:** The water body surface must calculate accurate reflections of surrounding geometry and skybox data, leveraging the IBL results from REN-27. This will require a dedicated refraction pass (distortion map) combined with reflection sampling.
2.  **Shader Pass:** A specific `WaterPass` needs to be added to the render graph *after* opaque rendering but *before* post-processing effects like bloom, ensuring it reads the environment data correctly.
3.  **Foam/Shoreline:** Implement procedural foam generation where water meets terrain (shorelines) or where two bodies of water meet (river confluence). This should be texture-driven and localized.

### C. Physics Integration (`skate-physics`, ECS)
1.  **Buoyancy & Drag:** For character controllers and other physics objects: when the object's center point is within a designated water volume collider, apply upward buoyant force (proportional to submerged volume) and continuous drag force opposing movement.
2.  **Water Volume Collider:** A specialized `WaterVolumeCollider` component that defines the boundaries (e.g., mesh or box intersection) of the liquid body, feeding into the physics query system.

## 3. Technical Considerations & Assumptions
*   **Performance:** All high-density elements must prioritize performance using instancing and LOD.
*   **Coordination:** The wind force applied in the animation/rendering pass should be correlated with any potential weather systems (to prevent visual mismatch).
*   **Feature Showcase Integration:** A designated area within the Feature Showcase will demonstrate these elements, allowing for clear unit testing of visibility and interaction.

## 4. Acceptance Criteria
1.  A test scene loads a water volume that correctly reflects surrounding geometry.
2.  Skater character/debug cube can enter the water, triggering realistic buoyancy forces and drag simulation (passing collision tests).
3.  Vegetation assets load, display LOD based on distance, and react visibly to an applied wind vector.
4.  The entire system is modularly defined so that different vegetation types or water materials can be swapped out easily.