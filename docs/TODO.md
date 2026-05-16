# 🛹 SkateSim Engine - TODO & Roadmap

## Current Status

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture documentation.

---

## 📋 Phase 2: Core Systems

**Focus:** Advanced rendering, core gameplay mechanics, and core tooling

- [ ] **A46.0.11: Implement Project Window (File System Browser)**
  - **Priority:** High (needed before A46.0.2)
  - **Estimated Effort:** 15-20 hours
  - **Implementation Plan:** See implementation notes below

  **Features:**
  - Tree view of project directories (recursive file/folder display)
  - File type icons (folders, scenes, textures, models, sounds, scripts, configs)
  - Search/filter bar with real-time filtering
  - Breadcrumb navigation showing current project
  - Context menu: New Folder, New File, Rename, Delete, Show in Explorer, Copy Path, Open External
  - Favorites system with persistence (star important folders/files)
  - Double-click actions: Open .scene files in editor, open scripts in external editor
  - Status bar with file count, folder count, total size
  - Undo support for Create, Delete, Rename operations
  - EventSystem integration: FileSystemChangedEvent triggers AssetBrowser refresh
  - Mimics Godot's FileSystem dock behavior

  **New Files:**
  - `editor/windows/ProjectWindow.kt` — Main window implementation
  - `editor/windows/project/FileSystemItem.kt` — Data model (FileSystemItem, FileType enum)
  - `editor/systems/FileSystemScanner.kt` — Directory scanning, favorites management
  - `editor/commands/DeleteFileCommand.kt` — Undoable file deletion
  - `editor/commands/CreateFileCommand.kt` — Undoable file/folder creation
  - `editor/commands/RenameFileCommand.kt` — Undoable file/folder rename
  - `engine/events/FileSystemEvents.kt` — FileSystemChangedEvent, OpenSceneFileEvent

  **Modified Files:**
  - `editor/ui/WindowRegistry.kt` — Add ProjectWindow registration
  - `app/KoinModule.kt` — Add FileSystemScanner and ProjectWindow factories
  - `resources/values/strings.properties` — Add 15+ project window string keys
  - `editor/imgui/data/Icons.kt` — Add FILM, FILE_TEXT, MAGIC icons

- [ ] **A46.0.2: Implement Advanced Lighting Models**
  - Point lights with position, color, intensity
  - Spot lights with adjustable parameters
  - Image-Based Lighting (IBL) with environment maps
  - Correct lighting calculations for all light types

- [ ] **A46.0.3: Develop Post-Processing Stack**
  - Framework for adding and chaining post-processing effects
  - Implement bloom, depth of field, color grading
  - Screen-space shaders using FBOs
  - Enable/disable and configure effects via editor or scripts

- [ ] **A46.0.4: Create Advanced Material System**
  - Standard PBR material model (Metallic-Roughness workflow)
  - Manage material properties (textures, scalars)
  - Integrate with rendering pipeline and shaders
  - Support shader variants based on material properties

- [ ] **A46.0.5: Implement In-Game UI System**
  - Hierarchy of UI elements (Panel, Button, Text, Image)
  - Position, size, and style UI elements
  - User interaction support (clicks, input)
  - Efficient rendering integrated into main scene

- [ ] **A46.0.6: Develop VFX/Particle System**
  - Particle emitter component
  - Particle properties (lifetime, size, color, velocity, texture)
  - Particle behaviors (gravity, drag, collision)
  - Efficient rendering of large particle counts

- [ ] **A47.0.7: Enhance Animation System (Retargeting)**
  - Bone transformation mapping between skeletons
  - Retarget humanoid animations to different rigs
  - Preserve animation feel and intent

- [ ] **A46.0.8: Implement Advanced Physics Constraints**
  - Additional Bullet constraints (Generic6DofConstraint, etc.)
  - API for creating and configuring constraints
  - Stable constraint simulation

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

## ARCH Program (Architecture Remediation)

Reference: `docs/ARCH_REMEDIATION_PLAN.md` (canonical ARCH plan).
Core contracts ADR: `docs/ADR-ARCH-002-core-contracts.md`.
QA Gate 1 report: `docs/ARCH-007-QA-GATE1.md`.

### Execution Policy

- Default policy: **single active ARCH task at a time**.
- Parallel execution is allowed only for tasks marked parallel-safe in the remediation plan.

### Milestone Gates

- **M1 Stabilization:** through ARCH-007
- **M2 Undo/Async correctness:** through ARCH-012
- **M3 ECS + consolidation:** through ARCH-016 and ARCH-014
- **M4 DI/layer + localization + guardrails:** through ARCH-020
- **M5 Release readiness + closure:** ARCH-021 to ARCH-023

### Dependency / Order Summary

1. ARCH-001 -> ARCH-002
2. ARCH-003 + ARCH-004 -> ARCH-005 -> ARCH-007
3. ARCH-008 -> (ARCH-009 + ARCH-010) -> ARCH-011 -> ARCH-012
4. ARCH-020 -> ARCH-021 -> ARCH-022 -> ARCH-023
5. Additional prerequisite chain: ARCH-012 -> ARCH-013/015/016; ARCH-017 -> ARCH-018/019

### ARCH Task Status Registry (ARCH-001..ARCH-023)

| ID | Title | Owner | Status |
|---|---|---|---|
| ARCH-001 | Bootstrap ARCH tracking in docs | documentation-engineer | done |
| ARCH-002 | ADR pack for core contracts | tech-lead | done |
| ARCH-003 | UI conformance pass A | software-engineer | done |
| ARCH-004 | UI conformance pass B | software-engineer | done |
| ARCH-005 | Play-mode mutation gate integration | software-engineer | done |
| ARCH-006 | Quick consistency fixes | software-engineer | done |
| ARCH-007 | QA Gate 1 (UI + play boundary) | qa-engineer | blocked (compile passed; full test suite failing with 14 failures) |
| ARCH-008 | UndoRedo core refactor | software-engineer | done |
| ARCH-009 | Retrofit sync command semantics | software-engineer | done |
| ARCH-010 | Async command lifecycle hardening | software-engineer | done |
| ARCH-011 | QA Gate 2 (undo + async) | qa-engineer | done |
| ARCH-012 | Reviewer architecture gate | reviewer | pending |
| ARCH-013 | ECS invalidation implementation | physics-engineer | pending |
| ARCH-014 | QA Gate 3 (ECS invalidation) | qa-engineer | pending |
| ARCH-015 | Duplicate-object flow consolidation | software-engineer | pending |
| ARCH-016 | Scene traversal consolidation | software-engineer | pending |
| ARCH-017 | DI/layering decision checkpoint | tech-lead | pending |
| ARCH-018 | DI/layering implementation | software-engineer | pending |
| ARCH-019 | Localization completion sweep | software-engineer | pending |
| ARCH-020 | Guard tests + async test fixtures | qa-engineer | pending |
| ARCH-021 | QA Gate 4 full checkpoint | qa-engineer | pending |
| ARCH-022 | Final reviewer gate | reviewer | pending |
| ARCH-023 | Documentation closure | documentation-engineer | pending |

---

### ARCH Status Transition Log (M1 run 2026-05-12)

| ID | Owner | Transition |
|---|---|---|
| ARCH-002 | tech-lead | pending -> in_progress -> done |
| ARCH-003 | software-engineer | pending -> in_progress -> done |
| ARCH-004 | software-engineer | pending -> in_progress -> done |
| ARCH-005 | software-engineer | pending -> in_progress -> done |
| ARCH-006 | software-engineer | pending -> in_progress -> done |
| ARCH-007 | qa-engineer | pending -> in_progress -> blocked (compile passed; full test suite failed: 252 run, 14 failed) |
| ARCH-008 | software-engineer | pending -> in_progress -> done |
| ARCH-009 | software-engineer | pending -> in_progress -> done |
| ARCH-010 | software-engineer | pending -> in_progress -> blocked (pending implementation); blocked -> in_progress -> done |
| ARCH-011 | qa-engineer | pending -> in_progress -> blocked (depends on ARCH-010; ready for scoped QA execution); blocked -> in_progress -> done (2026-05-16: compile + targeted ARCH-011 tests passed) |

---

## End of TODO
