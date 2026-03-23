# 🛹 SkateSim Engine - TODO & Roadmap

## Current Status: Code Quality & Performance Complete ✅

The ECS architecture is 100% complete through v0.42.
The EventSystem implementation is complete through v0.43.
Code quality improvements and performance optimizations are complete through v0.44.

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture documentation.

---

## ✅ v0.44: Code Quality & Performance (Complete)

### Summary

Focus on code quality improvements, performance optimization, and technical debt reduction.
This release addresses null safety, resource management, object allocation, and test coverage.

### Completed Tasks

- [x] **A44.0.1: Audit and replace remaining `!!` operators** ✅
  - Used safe calls (`?.`) and Elvis operator (`?:`)
  - Fixed smart cast issues with `?.let {}` pattern
  - **Impact**: Medium - Improve code safety
  - **Location**: `TrickDetector.kt`, `KeyBindingsWindow.kt`

- [x] **A44.0.2: Review resource management for memory leaks** ✅
  - Added `resourceManager.clear()` during scene transitions
  - Prevents accumulation of textures, models, shaders, and sounds
  - **Impact**: High - Prevent memory leaks
  - **Location**: `SceneManager.kt`

- [x] **A44.0.3: Optimize object allocation in hot loops** ✅
  - Added reusable Vector3f instances in SkateboardPhysics
  - Reduced garbage collection pressure in physics update loop
  - **Impact**: Medium - Improve performance
  - **Location**: `SkateboardPhysics.kt`

- [x] **A44.0.4: Increase test coverage for complex systems** ✅
  - Added 18 comprehensive EventSystem tests
  - Tests cover type-safe and string-based subscriptions
  - Tests cover priority ordering and error handling
  - **Impact**: High - Improve code reliability
  - **Location**: `test/.../events/EventSystemTest.kt`

---

## 📋 Phase 1: Foundation (Planned)

**Focus:** Core engine stability, asset pipeline, and essential gameplay foundations

### TASK-001: Enhance Asset Management Pipeline

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Support wider range of asset types (textures, audio, animations)
- [ ] Implement dependency tracking between assets
- [ ] Add caching mechanisms to avoid redundant loading
- [ ] Enable hot-reloading of assets during runtime and editor use
- [ ] Consider plugin-based system for asset loaders
- **Dependencies:** None

### TASK-002: Implement Scene Serialization

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Save scenes to file format (JSON or custom binary)
- [ ] Load saved scenes, reconstructing scene accurately
- [ ] Serialize and deserialize all component data correctly
- [ ] Handle scene hierarchy and object relationships
- **Dependencies:** TASK-001

### TASK-003: Develop Basic Audio System

- [ ] **Priority:** 🔴 High | **Effort:** Medium
- [ ] Load and play audio files (WAV, OGG)
- [ ] Support for 2D audio playback (global sounds)
- [ ] Support for 3D audio playback with spatialization
- [ ] Basic controls for volume, looping, and playback status
- [ ] Research: OpenAL, LWJGL audio bindings
- **Dependencies:** None

### TASK-004: Implement Ragdoll Physics

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Define and create ragdoll skeletons from skeletal data
- [ ] Activate/deactivate ragdolls with animation blending
- [ ] Ragdolls respond to physics forces (gravity, collisions)
- [ ] Integration with physics system and component model
- [ ] Research: Bullet RigidBody, TypedConstraint (HingeConstraint, ConeTwistConstraint)
- **Dependencies:** None

### TASK-005: Integrate Scripting Language (Kotlin Script)

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Set up scripting environment integrated with ECS, input, etc.
- [ ] Write scripts in Kotlin Script
- [ ] Attach scripts to GameObjects as components
- [ ] Scripts can access and manipulate engine systems
- [ ] Manage script execution in engine update loop
- **Dependencies:** None

### TASK-006: Set up Automated Testing Framework

- [ ] **Priority:** 🟡 Medium | **Effort:** Medium
- [ ] Integrate testing framework (JUnit) into build process
- [ ] Unit tests for critical modules (ECS, asset loading, math)
- [ ] Integration tests for major system interactions
- [ ] Incorporate visual assertion tools into test suite
- **Dependencies:** None

### TASK-007: Refactor Renderer to Render Graph System

- [ ] **Priority:** 🟡 Medium | **Effort:** Large
- [ ] Define render graph structure with pass inputs/outputs
- [ ] Dynamic pass compilation into execution order
- [ ] Convert existing passes (Shadow, Picking, Geometry, Debug)
- [ ] Extensible for deferred rendering and post-processing
- **Dependencies:** None

---

## 📋 Phase 2: Core Systems (Planned)

**Focus:** Advanced rendering, core gameplay mechanics, and core tooling

### TASK-010: Implement Advanced Lighting Models

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Point lights with position, color, intensity
- [ ] Spot lights with adjustable parameters
- [ ] Image-Based Lighting (IBL) with environment maps
- [ ] Correct lighting calculations for all light types
- **Dependencies:** TASK-007

### TASK-011: Develop Post-Processing Stack

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Framework for adding and chaining post-processing effects
- [ ] Implement bloom, depth of field, color grading
- [ ] Screen-space shaders using FBOs
- [ ] Enable/disable and configure effects via editor or scripts
- **Dependencies:** TASK-007

### TASK-012: Create Advanced Material System

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Standard PBR material model (Metallic-Roughness workflow)
- [ ] Manage material properties (textures, scalars)
- [ ] Integrate with rendering pipeline and shaders
- [ ] Support shader variants based on material properties
- **Dependencies:** TASK-007

### TASK-013: Implement In-Game UI System

- [ ] **Priority:** 🔴 High | **Effort:** Medium
- [ ] Hierarchy of UI elements (Panel, Button, Text, Image)
- [ ] Position, size, and style UI elements
- [ ] User interaction support (clicks, input)
- [ ] Efficient rendering integrated into main scene
- **Dependencies:** None

### TASK-014: Develop VFX/Particle System

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Particle emitter component
- [ ] Particle properties (lifetime, size, color, velocity, texture)
- [ ] Particle behaviors (gravity, drag, collision)
- [ ] Efficient rendering of large particle counts
- [ ] Consider GPU particle simulation
- **Dependencies:** None

### TASK-015: Implement Advanced Physics Constraints

- [ ] **Priority:** 🟡 Medium | **Effort:** Medium
- [ ] Additional Bullet constraints (Generic6DofConstraint, etc.)
- [ ] API for creating and configuring constraints
- [ ] Stable constraint simulation
- **Dependencies:** TASK-004

### TASK-016: Enhance Animation System (Retargeting)

- [ ] **Priority:** 🟡 Medium | **Effort:** Large
- [ ] Bone transformation mapping between skeletons
- [ ] Retarget humanoid animations to different rigs
- [ ] Preserve animation feel and intent
- [ ] Research: Inverse kinematics techniques
- **Dependencies:** None

### TASK-017: Improve Editor Scene Manipulation Tools

- [ ] **Priority:** 🟡 Medium | **Effort:** Medium
- [ ] More responsive and visually clear gizmos
- [ ] Grid and object snapping options
- [ ] Improved camera controls for scene view
- [ ] Tools for duplicating and grouping objects
- **Dependencies:** TASK-002, TASK-007

---

## 📋 Phase 3: Polish & Tooling (Planned)

**Focus:** Game-specific features, optimization, and user experience

### TASK-020: Develop Skateboarding Physics Mechanics

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] Realistic skateboard physics (mass, center of gravity, rotation)
- [ ] Ollie mechanics and board aerial control
- [ ] Grinding on rails and ledges
- [ ] Accurate physics response during landings and impacts
- [ ] Custom physics logic beyond standard rigid bodies
- **Dependencies:** TASK-004, TASK-015

### TASK-021: Implement Character Controller & State Machine

- [ ] **Priority:** 🔴 High | **Effort:** Large
- [ ] State machine for player actions (standing, skating, jumping, grinding)
- [ ] Seamless state transitions driven by input and physics
- [ ] Integration with animation system
- [ ] Nuanced skateboarding movement control
- **Dependencies:** TASK-005, TASK-016

### TASK-022: Integrate Networking for Multiplayer

- [ ] **Priority:** 🟡 Medium | **Effort:** Large
- [ ] Basic client-server architecture
- [ ] Core game state synchronization (positions, actions)
- [ ] Handle network latency and packet loss
- [ ] Simple multiplayer example
- [ ] Research: Netcode for GameObjects, Netty/Kryo
- **Dependencies:** TASK-005

### TASK-023: Optimize Rendering Performance

- [ ] **Priority:** 🟡 Medium | **Effort:** Medium
- [ ] Identify rendering bottlenecks through profiling
- [ ] Implement batching, culling (frustum, occlusion)
- [ ] Efficient shader usage
- [ ] Meet target frame rates on representative hardware
- **Dependencies:** TASK-010, TASK-011

### TASK-024: Optimize Physics Performance

- [ ] **Priority:** 🟡 Medium | **Effort:** Medium
- [ ] Identify physics bottlenecks through profiling
- [ ] Optimize physics world settings, collision detection, solver iterations
- [ ] Ensure performance for target game complexity
- [ ] Tune solver iterations and broadphase settings
- **Dependencies:** TASK-004, TASK-015

### TASK-025: Develop Sample Skate Game Project

- [ ] **Priority:** 🟡 Medium | **Effort:** Large
- [ ] Playable mini-game demonstrating core skateboarding mechanics
- [ ] Utilize most key engine features (rendering, physics, animation, UI, scripting)
- [ ] Provide practical example of engine usage
- **Dependencies:** All previous tasks

### TASK-026: Refine Editor Workflow & UX

- [ ] **Priority:** 🟢 Low | **Effort:** Medium
- [ ] Collect and analyze user feedback
- [ ] Address common pain points in editor workflow
- [ ] Improve editor performance and responsiveness
- [ ] Implement minor UI/UX improvements
- **Dependencies:** TASK-017

### TASK-027: Comprehensive Documentation & Tutorials

- [ ] **Priority:** 🟢 Low | **Effort:** Large
- [ ] Generate API documentation
- [ ] Create getting started guides for new users
- [ ] Tutorials for scene setup, scripting, animation, physics
- [ ] Well-organized and searchable documentation
- **Dependencies:** All previous tasks

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
