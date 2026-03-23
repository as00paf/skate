# 🛹 SkateSim Engine - TODO & Roadmap

## Current Status

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture documentation.

---

## ✅ v0.45.0.2: Implement Scene Serialization (Complete)

### Summary

Refactored scene serialization to use existing LevelManager, removing duplicate code.
All 15 ECS components are serializable for level persistence and GameObject copy operations.

### Completed Tasks

- [x] **Component serialization support** ✅
  - All 15 components registered for polymorphic serialization
  - Non-serializable fields marked @Transient
  - **Location**: `engine/assets/serialization/Serializer.kt`

- [x] **Level persistence via LevelManager** ✅
  - LevelManager handles save/load with file dialogs
  - Integrated with editor menu (File > Save/Open Level)
  - Uses LevelData (gameObjects + SceneData)
  - **Location**: `game/level/LevelManager.kt`

- [x] **GameObject serialization for clipboard/prefabs** ✅
  - GameObject.copy() uses Serializer for duplication
  - ClipboardService uses copy() for copy/paste
  - **Location**: `engine/ecs/GameObject.kt`, `editor/systems/ClipboardService.kt`

- [x] **Unit tests** ✅
  - 7 GameObject serialization tests
  - Transform, component, file operation tests
  - **Location**: `test/.../ecs/serialization/GameObjectSerializationTest.kt`

### Architecture

- **Level** = Persisted file (LevelData: gameObjects + SceneData)
- **Scene** = Runtime ECS container (not serialized)
- **GameObject** = Serializable entity (for clipboard/prefabs)

---

## ✅ v0.45.0.1: Enhance Asset Management Pipeline (Complete)

### Summary

Enhanced ResourceManager with dependency tracking, LRU caching, and hot-reloading support.

### Completed Tasks

- [x] **Dependency tracking** ✅
  - `modelDependencies` map tracks model→texture relationships
  - `getModelDependencies(path)` - query dependencies
  - `isTextureInUse(path)` - safe unload checking
  - **Location**: `engine/assets/ResourceManager.kt`

- [x] **LRU cache with memory limits** ✅
  - `lruQueue` for access ordering
  - `currentTextureMemory` tracking
  - Auto-eviction at 256MB limit (configurable)
  - Memory estimation: `width * height * 4 bytes * 1.33 (mipmaps)`
  - **Location**: `engine/assets/ResourceManager.kt`

- [x] **Hot-reloading (editor)** ✅
  - `watchService` - Java NIO WatchService
  - `pollHotReload()` - call periodically in editor
  - `invalidateAsset()` - removes from cache
  - `enableHotReload` constructor param (default false)
  - **Location**: `engine/assets/ResourceManager.kt`

---

## 📋 Phase 1: Foundation (Planned)

**Focus:** Core engine stability, asset pipeline, and essential gameplay foundations

- [x] **A45.0.1: Enhance Asset Management Pipeline** ✅
  - Support wider range of asset types (textures, audio, animations)
  - Implement dependency tracking between assets
  - Add caching mechanisms to avoid redundant loading
  - Enable hot-reloading of assets during runtime and editor use
  - Consider plugin-based system for asset loaders
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** None
  - **Location**: `engine/assets/`

- [ ] **A45.0.2: Implement Scene Serialization**
  - Save scenes to file format (JSON or custom binary)
  - Load saved scenes, reconstructing scene accurately
  - Serialize and deserialize all component data correctly
  - Handle scene hierarchy and object relationships
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A45.0.1
  - **Location**: `engine/ecs/scene/`

- [ ] **A45.0.3: Develop Basic Audio System**
  - Load and play audio files (WAV, OGG)
  - Support for 2D audio playback (global sounds)
  - Support for 3D audio playback with spatialization
  - Basic controls for volume, looping, and playback status
  - **Priority:** 🔴 High | **Effort:** Medium
  - **Dependencies:** None
  - **Location**: `engine/audio/`

- [ ] **A45.0.4: Implement Ragdoll Physics**
  - Define and create ragdoll skeletons from skeletal data
  - Activate/deactivate ragdolls with animation blending
  - Ragdolls respond to physics forces (gravity, collisions)
  - Integration with physics system and component model
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** None
  - **Location**: `engine/physics3d/`

- [ ] **A45.0.5: Set up Automated Testing Framework**
  - Integrate testing framework (JUnit) into build process
  - Unit tests for critical modules (ECS, asset loading, math)
  - Integration tests for major system interactions
  - Incorporate visual assertion tools into test suite
  - **Priority:** 🟡 Medium | **Effort:** Medium
  - **Dependencies:** None
  - **Location**: `test/`

- [ ] **A45.0.6: Refactor Renderer to Render Graph System**
  - Define render graph structure with pass inputs/outputs
  - Dynamic pass compilation into execution order
  - Convert existing passes (Shadow, Picking, Geometry, Debug)
  - Extensible for deferred rendering and post-processing
  - **Priority:** 🟡 Medium | **Effort:** Large
  - **Dependencies:** None
  - **Location**: `engine/render/`

---

## 📋 Phase 2: Core Systems (Planned)

**Focus:** Advanced rendering, core gameplay mechanics, and core tooling

- [ ] **A46.0.1: Implement Advanced Lighting Models**
  - Point lights with position, color, intensity
  - Spot lights with adjustable parameters
  - Image-Based Lighting (IBL) with environment maps
  - Correct lighting calculations for all light types
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A45.0.6
  - **Location**: `engine/render/`

- [ ] **A46.0.2: Develop Post-Processing Stack**
  - Framework for adding and chaining post-processing effects
  - Implement bloom, depth of field, color grading
  - Screen-space shaders using FBOs
  - Enable/disable and configure effects via editor or scripts
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A45.0.6
  - **Location**: `engine/render/postprocess/`

- [ ] **A46.0.3: Create Advanced Material System**
  - Standard PBR material model (Metallic-Roughness workflow)
  - Manage material properties (textures, scalars)
  - Integrate with rendering pipeline and shaders
  - Support shader variants based on material properties
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A45.0.6
  - **Location**: `engine/render/materials/`

- [ ] **A46.0.4: Implement In-Game UI System**
  - Hierarchy of UI elements (Panel, Button, Text, Image)
  - Position, size, and style UI elements
  - User interaction support (clicks, input)
  - Efficient rendering integrated into main scene
  - **Priority:** 🔴 High | **Effort:** Medium
  - **Dependencies:** None
  - **Location**: `engine/ui/`

- [ ] **A46.0.5: Develop VFX/Particle System**
  - Particle emitter component
  - Particle properties (lifetime, size, color, velocity, texture)
  - Particle behaviors (gravity, drag, collision)
  - Efficient rendering of large particle counts
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** None
  - **Location**: `engine/vfx/`

- [ ] **A46.0.6: Implement Advanced Physics Constraints**
  - Additional Bullet constraints (Generic6DofConstraint, etc.)
  - API for creating and configuring constraints
  - Stable constraint simulation
  - **Priority:** 🟡 Medium | **Effort:** Medium
  - **Dependencies:** A45.0.4
  - **Location**: `engine/physics3d/`

- [ ] **A46.0.7: Enhance Animation System (Retargeting)**
  - Bone transformation mapping between skeletons
  - Retarget humanoid animations to different rigs
  - Preserve animation feel and intent
  - **Priority:** 🟡 Medium | **Effort:** Large
  - **Dependencies:** None
  - **Location**: `engine/animation/`

- [ ] **A46.0.8: Improve Editor Scene Manipulation Tools**
  - More responsive and visually clear gizmos
  - Grid and object snapping options
  - Improved camera controls for scene view
  - Tools for duplicating and grouping objects
  - **Priority:** 🟡 Medium | **Effort:** Medium
  - **Dependencies:** A45.0.2, A45.0.6
  - **Location**: `editor/`

---

## 📋 Phase 3: Polish & Tooling (Planned)

**Focus:** Game-specific features, optimization, and user experience

- [ ] **A47.0.1: Develop Skateboarding Physics Mechanics**
  - Realistic skateboard physics (mass, center of gravity, rotation)
  - Ollie mechanics and board aerial control
  - Grinding on rails and ledges
  - Accurate physics response during landings and impacts
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A45.0.4, A46.0.6
  - **Location**: `game/skateboard/`

- [ ] **A47.0.2: Implement Character Controller & State Machine**
  - State machine for player actions (standing, skating, jumping, grinding)
  - Seamless state transitions driven by input and physics
  - Integration with animation system
  - Nuanced skateboarding movement control
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A46.0.7
  - **Location**: `game/player/`

- [ ] **A47.0.3: Optimize Rendering Performance**
  - Identify rendering bottlenecks through profiling
  - Implement batching, culling (frustum, occlusion)
  - Efficient shader usage
  - Meet target frame rates on target hardware
  - **Priority:** 🟡 Medium | **Effort:** Medium
  - **Dependencies:** A46.0.1, A46.0.2
  - **Location**: `engine/render/`

- [ ] **A47.0.4: Optimize Physics Performance**
  - Identify physics bottlenecks through profiling
  - Optimize physics world settings, collision detection, solver iterations
  - Ensure performance for target game complexity
  - Tune solver iterations and broadphase settings
  - **Priority:** 🟡 Medium | **Effort:** Medium
  - **Dependencies:** A45.0.4, A46.0.6
  - **Location**: `engine/physics3d/`

- [ ] **A47.0.5: Integrate Scripting Language (TypeScript)**
  - Create scripting abstraction layer for multiple language support
  - Implement TypeScript scripting engine as first language
  - Scripts can be attached to GameObjects as components
  - Scripts can access and manipulate engine systems via safe API
  - Manage script execution in engine update loop
  - Design abstraction to support future languages (Lua, Python, etc.)
  - **Priority:** 🔴 High | **Effort:** Large
  - **Dependencies:** A45.0.6
  - **Location**: `engine/scripting/`

- [ ] **A47.0.6: Develop Sample Skate Game Project**
  - Playable mini-game demonstrating core skateboarding mechanics
  - Utilize most key engine features (rendering, physics, animation, UI, scripting)
  - Provide practical example of engine usage
  - **Priority:** 🟡 Medium | **Effort:** Large
  - **Dependencies:** All previous except A47.0.7
  - **Location**: `samples/`

- [ ] **A47.0.7: Refine Editor Workflow & UX**
  - Collect and analyze user feedback
  - Address common pain points in editor workflow
  - Improve editor performance and responsiveness
  - Implement minor UI/UX improvements
  - **Priority:** 🟢 Low | **Effort:** Medium
  - **Dependencies:** A46.0.8
  - **Location**: `editor/`

- [ ] **A47.0.8: Comprehensive Documentation & Tutorials**
  - Generate API documentation
  - Create getting started guides for new users
  - Tutorials for scene setup, scripting, animation, physics
  - Well-organized and searchable documentation
  - **Priority:** 🟢 Low | **Effort:** Large
  - **Dependencies:** All previous tasks
  - **Location**: `docs/`

- [ ] **A47.0.9: Integrate Networking for Multiplayer**
  - Basic client-server architecture
  - Core game state synchronization (positions, actions)
  - Handle network latency and packet loss
  - Simple multiplayer example
  - **Priority:** 🟡 Medium | **Effort:** Large
  - **Dependencies:** A47.0.5
  - **Location**: `engine/networking/`

---

## Architecture Reference

### Current ECS Architecture (v0.42)

**Components (15 total):**

- Core: Transform, RenderComponent, RigidBody3D, PhysicsComponent
- Input: InputStateComponent, EditorInputStateComponent
- Animation: SkeletonComponent, Animator
- Environment: EnvironmentComponent, TimeComponent, LightingStateComponent, LightingComponent
- Editor: NonPickable, ModularTile, SpriteRenderer
- Special: Component (base class)

**Systems (12 total):**

- ECS Infrastructure: System, SystemManager, GameObjectManager
- Gameplay: InputSystem, PhysicsSystem, AnimationSystem, DayNightCycleSystem
- Environment: EnvironmentSystem, DirectionalLightSystem, GridLines
- Editor: GizmoSystem, MouseControls

**ECS Pattern Compliance: 100%** ✅

### Event-Driven Architecture (v0.43+)

**Event Categories:**

- `input.*` - Input events (jump_pressed, movement, trick_input)
- `physics.*` - Physics events (landing, takeoff, grounded_changed, collision)
- `trick.*` - Trick events (detected, completed, cancelled)
- `game.*` - Game state events (state_changed, score_changed)
- `ui.*` - UI events (button_clicked, menu_opened)

**Benefits:**

- Decoupled systems (no direct component queries)
- Testable systems (mock events instead of full ECS)
- Flexible reactions (multiple listeners for same event)
- Clear data flow (events document system interactions)

---

## End of TODO
