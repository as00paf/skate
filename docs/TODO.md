# 🛹 SkateSim Engine - TODO & Roadmap

## Current Status

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture documentation.

---

## 📋 Phase 1: Foundation (Planned)

**Focus:** Core engine stability, asset pipeline, and essential gameplay foundations

- [ ] **A45.0.4: Implement Ragdoll Physics**
  - Define and create ragdoll skeletons from skeletal data
  - Activate/deactivate ragdolls with animation blending
  - Ragdolls respond to physics forces (gravity, collisions)
  - Integration with physics system and component model

- [ ] **A45.0.5: Set up Automated Testing Framework**
  - Integrate testing framework (JUnit) into build process
  - Unit tests for critical modules (ECS, asset loading, math)
  - Integration tests for major system interactions
  - Incorporate visual assertion tools into test suite

- [ ] **A45.0.6: Refactor Renderer to Render Graph System**
  - Define render graph structure with pass inputs/outputs
  - Dynamic pass compilation into execution order
  - Convert existing passes (Shadow, Picking, Geometry, Debug)
  - Extensible for deferred rendering and post-processing

---

## 📋 Phase 2: Core Systems (Planned)

**Focus:** Advanced rendering, core gameplay mechanics, and core tooling

- [ ] **A46.0.1: Implement Advanced Lighting Models**
  - Point lights with position, color, intensity
  - Spot lights with adjustable parameters
  - Image-Based Lighting (IBL) with environment maps
  - Correct lighting calculations for all light types

- [ ] **A46.0.2: Develop Post-Processing Stack**
  - Framework for adding and chaining post-processing effects
  - Implement bloom, depth of field, color grading
  - Screen-space shaders using FBOs
  - Enable/disable and configure effects via editor or scripts

- [ ] **A46.0.3: Create Advanced Material System**
  - Standard PBR material model (Metallic-Roughness workflow)
  - Manage material properties (textures, scalars)
  - Integrate with rendering pipeline and shaders
  - Support shader variants based on material properties

- [ ] **A46.0.4: Implement In-Game UI System**
  - Hierarchy of UI elements (Panel, Button, Text, Image)
  - Position, size, and style UI elements
  - User interaction support (clicks, input)
  - Efficient rendering integrated into main scene

- [ ] **A46.0.5: Develop VFX/Particle System**
  - Particle emitter component
  - Particle properties (lifetime, size, color, velocity, texture)
  - Particle behaviors (gravity, drag, collision)
  - Efficient rendering of large particle counts

- [ ] **A46.0.6: Implement Advanced Physics Constraints**
  - Additional Bullet constraints (Generic6DofConstraint, etc.)
  - API for creating and configuring constraints
  - Stable constraint simulation

- [ ] **A46.0.7: Enhance Animation System (Retargeting)**
  - Bone transformation mapping between skeletons
  - Retarget humanoid animations to different rigs
  - Preserve animation feel and intent

- [ ] **A46.0.8: Improve Editor Scene Manipulation Tools**
  - More responsive and visually clear gizmos
  - Grid and object snapping options
  - Improved camera controls for scene view
  - Tools for duplicating and grouping objects

---

## 📋 Phase 3: Polish & Tooling (Planned)

**Focus:** Game-specific features, optimization, and user experience

- [ ] **A47.0.1: Develop Skateboarding Physics Mechanics**
  - Realistic skateboard physics (mass, center of gravity, rotation)
  - Ollie mechanics and board aerial control
  - Grinding on rails and ledges
  - Accurate physics response during landings and impacts

- [ ] **A47.0.2: Implement Character Controller & State Machine**
  - State machine for player actions (standing, skating, jumping, grinding)
  - Seamless state transitions driven by input and physics
  - Integration with animation system
  - Nuanced skateboarding movement control

- [ ] **A47.0.3: Optimize Rendering Performance**
  - Identify rendering bottlenecks through profiling
  - Implement batching, culling (frustum, occlusion)
  - Efficient shader usage
  - Meet target frame rates on target hardware

- [ ] **A47.0.4: Optimize Physics Performance**
  - Identify physics bottlenecks through profiling
  - Optimize physics world settings, collision detection, solver iterations
  - Ensure performance for target game complexity
  - Tune solver iterations and broadphase settings

- [ ] **A47.0.5: Integrate Scripting Language (TypeScript)**
  - Create scripting abstraction layer for multiple language support
  - Implement TypeScript scripting engine as first language
  - Scripts can be attached to GameObjects as components
  - Scripts can access and manipulate engine systems via safe API
  - Manage script execution in engine update loop
  - Design abstraction to support future languages (Lua, Python, etc.)

- [ ] **A47.0.6: Develop Sample Skate Game Project**
  - Playable mini-game demonstrating core skateboarding mechanics
  - Utilize most key engine features (rendering, physics, animation, UI, scripting)
  - Provide practical example of engine usage

- [ ] **A47.0.7: Refine Editor Workflow & UX**
  - Collect and analyze user feedback
  - Address common pain points in editor workflow
  - Improve editor performance and responsiveness
  - Implement minor UI/UX improvements

- [ ] **A47.0.8: Comprehensive Documentation & Tutorials**
  - Generate API documentation
  - Create getting started guides for new users
  - Tutorials for scene setup, scripting, animation, physics
  - Well-organized and searchable documentation

- [ ] **A47.0.9: Integrate Networking for Multiplayer**
  - Basic client-server architecture
  - Core game state synchronization (positions, actions)
  - Handle network latency and packet loss
  - Simple multiplayer example

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
