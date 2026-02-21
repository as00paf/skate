# SkateSim Engine Changelog

This document tracks the development history and major milestones of the SkateSim skateboarding simulation engine.

---

## [Unreleased] - v0.18: Fix Test Compilation After Camera Refactoring

### Fixed

- **CameraTest**: Updated test to match refactored Camera API
  - Removed unused Koin/MockK setup (Camera no longer has dependencies)
  - Changed `desiredDistance` to `zoom` property
  - Updated CameraPreset constructor to use `zoom` parameter
  - Test simplified from 73 lines to 37 lines

- **BootManagerTest**: Updated test to match refactored Renderer API
  - Changed `renderer.initFrameBuffer()` to `renderer.initialize()`
  - Removed `renderer.loadShaders()` verification (method removed in A12)
  - Removed unused mocks: `mouseListener`, `debugRenderer`
  - Removed unused imports: `MouseListener`, `DebugRenderer`

- **BoardRigTest**: Fixed private property access
  - Added `PlayerStateManager` component to test GameObject
  - Changed direct property access to proper component retrieval
  - Added import for `PlayerStateManager`

- **PlayerControllerTest**: Updated for PlayerController location change
  - Changed controller retrieval from skateboard to Skater GameObject
  - Updated `stateManager` access to use component retrieval
  - Added comment explaining PlayerController is now on Skater

- **TrickDetectionTest**: Fixed private property access
  - Added `PlayerStateManager` import
  - Added `PlayerStateManager` component to test GameObject
  - Changed direct property access to proper component retrieval

---

## [Previous] - v0.17: Camera Architecture Refactoring

### New Features

- **GameCamera Class**: New gameplay third-person camera controller
  - Gamepad right stick rotation for third-person view
  - Mouse rotation when cursor disabled (gameplay mode)
  - Physics-based clipping to prevent camera from going through walls
  - Spring arm with configurable distance and offset
  - Wraps/composes base Camera instance
  - Location: `src/main/kotlin/com/pafoid/skate/game/camera/GameCamera.kt`

### Architecture Changes

- **Three-Tier Camera Architecture**: Separated camera concerns into distinct components
  - **Camera**: Pure engine component with NO input dependencies
    - Projection/view matrix creation
    - Camera state (position, pitch, yaw, roll, fov, zoom)
    - Preset interpolation
    - Ray casting from screen coordinates
    - Forward/right vector calculation
  - **GameCamera**: Gameplay third-person controller
    - Gamepad and mouse input handling
    - Physics clipping (raycast against scene)
    - Spring arm mechanics
  - **EditorCamera**: Editor free-fly navigation (already existed, now properly separated)
    - WASD + Space/Shift movement
    - RMB rotation for FPS-style look
    - MMB orbit, scroll zoom, Home reset

- **Camera Class Refactored**: Stripped to pure engine component
  - Removed all input dependencies (`IInputProvider`, `KeyListener`, `MouseListener`, `SceneManager`)
  - Removed gameplay-specific properties (`speed`, `target`, `desiredDistance`, `targetOffset`)
  - Removed `updateThirdPerson()` and `handleClipping()` methods
  - Simplified `update()` to only handle preset interpolation
  - Added comprehensive KDoc explaining proper usage

- **CameraPreset Updated**: Changed `distance` parameter to `zoom` for consistency

### Usage Updates

- **Scene.kt**: Simplified to `Camera()` with no parameters
- **ThumbnailCache.kt**: Removed input dependencies, uses `Camera(position = ...)`
- **BootManager.kt**: Removed input dependencies from constructor and Scene creation
- **KoinModule.kt**: Updated `ThumbnailCache` and `BootManager` definitions with fewer parameters

---

## [Previous] - v0.16: Dependency Injection Consistency (Rendering Pipeline)

### Changed

- **Constructor Injection Standardization**: Removed field injection (`by inject()`) from rendering components
  - `ModelRenderer`: Removed `: KoinComponent`, added `debugRenderer` to constructor
  - `DebugRenderer`: Removed `: KoinComponent`, added `resourceManager`, `logger`, `sceneManager` to constructor
  - `PickingRenderer`: Removed `: KoinComponent`, added `resourceManager`, `logger`, `sceneManager` to constructor
  - `Camera`: Removed `: KoinComponent`, added input dependencies to constructor (later refactored in v0.17)
  - Removed unused `org.koin.core.component` imports

- **RenderResourcesFactory**: Updated to inject explicit dependencies
  - `ModelRenderer` now receives `debugRenderer` parameter
  - `PickingRenderer` now receives `resourceManager`, `logger`, `sceneManager` parameters

- **Koin Module**: Updated definitions for new constructor signatures
  - `DebugRenderer`: `single { DebugRenderer(get(), get(), get()) }`
  - `PickingRenderer`: `single { PickingRenderer(get(), get(), get()) }`
  - `ThumbnailCache`: Updated with proper dependencies
  - `BootManager`: Updated with proper dependencies
  - Reordered modules: `inputModule` now defined before `engineModule` for proper dependency order

### Related Changes

- **Scene.kt**: Updated constructor to accept and forward input dependencies to `Camera`
- **BootManager.kt**: Updated to accept and forward input dependencies to `Scene`
- **ThumbnailCache.kt**: Removed `KoinComponent`, now uses constructor injection

---

## [Previous] - v0.15: Rendering Pipeline Fixes

### Fixed

- **Lambda Capture for Window Dimensions** (Severe):
  - Lambdas `{ width }` and `{ height }` captured initial values at factory creation time
  - After window resize, PickingPass and GeometryPass used stale dimensions
  - Removed `getWindowWidth`/`getWindowHeight` lambdas from `PickingPass` and `GeometryPass`
  - Made `PickingTexture.width/height` public (was private)
  - Passes now read dimensions directly from `frameBuffer.width/height` and `pickingTexture.width/height`

- **Resize Propagation to RenderPasses** (High):
  - `PickingPass.resize()` existed but was never called
  - Updated `Renderer.resize()` to propagate to `renderResources.renderPasses.picking.resize()`

### Performance Optimizations

- **Consolidated Camera Viewport Updates**:
  - Camera viewport was set twice per frame (once in each pass)
  - Moved viewport update to `Renderer.render()` before executing any passes
  - Removed redundant assignments from `PickingPass.execute()` and `GeometryPass.execute()`

### Documentation

- **Picking Skip Behavior**: Added comprehensive KDoc explaining intentional optimization
  - Documented when picking runs vs. when it's skipped
  - Explained benefits: GPU draw call savings, CPU iteration savings, prevents accidental selection
  - Added "## Technical Details" section explaining the encoding mechanism

- **OpenGL Context Requirement**: Added KDoc to `RenderResourcesFactory.create()`
  - Warning that method requires active OpenGL context
  - "## Initialization Order" section with detailed steps
  - "## Usage" section explaining when/where to call
  - `@throws IllegalStateException` documentation

- **Shader Buffer Thread Safety**: Added "## Thread Safety" section to Shader.kt buffer KDoc
  - Clearly states buffers are NOT thread-safe
  - Explains corruption risk if multiple threads call upload methods concurrently
  - Documented current single-threaded usage pattern
  - Added migration options for future multi-threaded rendering

---

## [Previous] - v0.14: Render Pipeline Review & Code Quality

### Documentation

- **VAOLoader KDoc**: Comprehensive documentation for asset loading pipeline
  - Vertex attribute layout and enabled attributes bitmask
  - VAO/VBO/IBO lifecycle management
- **Coordinate Systems**: Screen space vs OpenGL texture space documentation
  - Y-axis inversion logic with usage examples
  - PickingTexture and Renderer.readPixel() documentation

### Performance Optimizations

- **Camera Matrix Caching**: Reuse Matrix4f instances instead of allocating per-frame
  - Reduces GC pressure in rendering pipeline
- **Shader Uniform Location Caching**: Cache glGetUniformLocation results
  - Eliminates expensive repeated OpenGL queries in hot paths
- **Buffer Reuse**: Reusable FloatBuffer instances in Shader class
  - Reduces allocations and frame stutter
- **Hardware Texture Limits**: Query GL_MAX_TEXTURE_IMAGE_UNITS at runtime
  - Better hardware compatibility
- **OpenGL State Tracking**: GLStateTracker for redundant call elimination
  - Tracks blend, depth mask, cull face, depth func state
  - Only calls gl functions when state changes
- **Entity ID Encoding**: Centralized encoder with Float types for GPU compatibility

### Architecture Improvements

- **Render Pass Interface**: Extracted PickingPass, GeometryPass, DebugPass
  - Better separation of concerns, easier testing
- **Z-Index for 2D Sprites**: Proper layering with batch grouping
  - Batches rendered in z-index order (lowest to highest)
- **Texture Slot Constants**: Named constants for PBR texture binding
  - BASE_COLOR, NORMAL, METALLIC_ROUGHNESS, AO, EMISSIVE
- **Aspect Ratio Handling**: Dynamic viewport-based calculation
  - Correct rendering on all aspect ratios (ultrawide, 4:3, 16:9)
- **Resource Cleanup**: Comprehensive destroy() methods
  - RenderBatch, Renderer2D, SkyboxRenderer, SkyDomeRenderer
  - FrameBuffer, PickingTexture cleanup

### Code Quality

- **ModelRenderer Consolidation**: Merged render() and renderSimple() methods
  - Extracted common mesh rendering logic
  - Unified texture binding pattern
- **Lighting Uniforms Loader**: Dedicated class for shader uniform uploads
  - Sun, moon, ambient, fog, camera position uniforms
- **OpenGL State Helpers**: Extension functions for VAO/texture binding
  - bindVAO, unbindVAO, bindTexture, withDepthFunc
- **Renderer2D Reorganization**: Improved separation of concerns
  - Constants moved to companion object
  - Dedicated vertex property loading method

### Tests

- **Render Pipeline Unit Tests**: 18 comprehensive tests
  - EntityIdEncoder encode/decode round-trip
  - Camera projection matrix tests (multiple aspect ratios)
  - GLStateTracker state getter consistency

---

## [Previous] - v0.13: Debug Rendering & Documentation

### Fixed

- **Physics Debug Colliders**: Fixed debug rendering broken during renderer refactoring
  - DebugRenderer singleton now properly shared between BulletPhysics3D and DebugPass
  - Debug pass now renders to FBO before unbind (correct render order)

### Added

- **Coordinate System Documentation**: Comprehensive KDoc for picking texture coordinate systems
  - Screen space vs OpenGL texture space documentation
  - Y-axis inversion logic explained with examples
  - Usage examples for Renderer.readPixel() and PickingTexture.readPixel()

### Tests

- **Render Pipeline Unit Tests**: 18 new tests in RenderPipelineTest.kt
  - EntityIdEncoder encode/decode round-trip tests
  - Camera projection matrix tests (multiple aspect ratios, orthographic/perspective)
  - GLStateTracker state getter consistency tests

---

## [Previous] - v0.12: Renderer Architecture Refactoring

### Changed

- **Renderer Architecture**: Major refactoring of rendering pipeline
  - Introduced RenderResourcesFactory for centralized resource creation
  - Separated render passes (Picking, Geometry, Debug) into dedicated classes
  - Created Renderers package with specialized renderer implementations
  - Eliminated 10+ lateinit vars from Renderer class
  - Applied Single Responsibility Principle

### Added

- **RenderResources Data Classes**: Shaders, Renderers, RenderPasses, RenderResources containers
- **RenderResourcesFactory**: Factory pattern for render resource creation
- **VAOLoader Injection**: Proper dependency injection for factory

### Fixed

- **Window Resize Handling**: Proper resize() method for framebuffer and picking texture
- **BootManager Integration**: Lazy initialization with error handling

---

## [Previous] - v0.11: Animation System & Character Components

### Added

- **Component-Based Architecture**: ECS refactoring
  - TransformComponent extracted from GameObject
  - RenderComponent for textured models
  - SkeletonComponent for skeletal animation
  - Deprecated Entity class in favor of GameObject

- **Animation Pipeline**: Full skeletal animation support
  - AssimpLoader integration for FBX animation import
  - AnimationSystem with blending and cross-fade support
  - Root motion support for locomotion
  - CharacterModel class for animated characters

- **Localization System**: i18n infrastructure
  - StringManager for property-based string lookup
  - Runtime language switching support
  - String formatting and pluralization

### Fixed

- **Null Safety**: Removed !! operators across codebase
  - SkateboardPhysics, RenderBatch, Camera, Physics3D, ThumbnailCache, AnimationSystem

- **Documentation**: Enhanced KDoc coverage
  - Physics3D, RigidBody3D, SkeletonMath, AnimationSampler, Interpolation

---

## [Previous] - v0.10: Mixamo Animation Pipeline

### Added

- **Asset Import**: FBX animation loading via Assimp
- **Bone Mapping**: Mixamo bone name standardization utility
- **Coordinate Conversion**: Y-up to engine coordinate system with 0.01 scale factor
- **Animation Sampling**: 60fps sampling to match physics timestep

---

## [Previous] - v0.9: Editor UX & Interaction

### Added

- **Selection System**: GizmoSystem with selection, translation, rotation, scale tools
- **Viewport Toolbar**: Icon-based toggle buttons for gizmos
- **Measure Tool**: Integrated as gizmo with proper scaling
- **Key Bindings**: Configurable input system settings
- **Deselect**: ESC key to deselect objects

---

## [Previous] - v0.8: Code Quality & Architecture

### Changed

- **Dependency Injection**: Koin integration for Renderer2D, InputBuffer, VAOLoader, SplashScreenManager
- **Null Safety**: Systematic !! removal across Animator, AssimpLoader, RigidBody3D, Window, TrickDetector
- **Code Structure**:
  - Line3D, Triangle3D, TransformCommand, Ray extracted to dedicated files
  - FQN imports replaced with proper import statements
  - Naming improvements (texSlots → textureSlots, vertexArray → lineVertexData)

### Fixed

- **Logic Bugs**:
  - Animator.updateBlended timing management
  - PlayerController.handleCatch pitch/roll torque correction
  - MeasureTool mouse position consistency
  - VAOLoader cleanup for splash screen

### Optimized

- **Performance**:
  - Animator.visualizeJoint allocation reduction
  - RenderBatch.loadVertexProperties Matrix4f reuse
  - PickingDraw.draw mesh batching
  - ScreenshotUtils large screenshot handling
  - Physics3D.add rigid body property setup

---

## [Previous] - v0.7: Physics Test Suite & Fixes

### Fixed

- **Friction Propagation**: RigidBody3D friction correctly applied to Bullet rawBody
- **Rolling Resistance**: linearDamping implementation for realistic decay
- **Steering Geometry**: Local-space steering forces for board lean-to-yaw translation
- **Fixed Timestep**: Accumulator-based loop for deterministic physics

### Tests

- **Physics Unit Tests**: Comprehensive test suite for skateboard physics
  - Mass & inertia tensor validation
  - Center of mass verification
  - Hooke's Law suspension testing
  - Pop mechanics and trick detection logic
  - High-speed stability and frame-rate independence

---

## [Previous] - v0.6: Editor Workspace Overhaul

### Changed

- **View Menu**: Reorganized with Windows sub-menu for toggling panels
- **Asset Browser**: Multi-tab browser (Models, Textures, Prefabs) replacing Create menu
- **Console**: Dual-tab system (Engine logs, Editor actions)
- **Profiler Window**: Real-time RAM, CPU/GPU frame times, draw calls, physics duration

### Added

- **Viewport Toolbar**: Centered icon-only buttons with tooltips
  - Reset Scene, Pause, Screenshot, Physics Debug Toggle
  - F12 Maximize Viewport toggle
- **Camera Controls**: Middle-mouse pan, mouse wheel zoom
- **Editor Shortcuts**: Ctrl+C/V/X (copy/paste/cut), Ctrl+Z/Y (undo/redo)

---

## [Previous] - v0.5: Core Infrastructure Modernization

### Changed

- **Dependency Management**: Migrated to libs.versions.toml for centralized versioning
- **Resource Management**: Centralized loading, caching, and unloading system
- **Dependency Injection**: Koin integration for component lifecycle management
- **Serialization**: kotlinx.serialization for save states and configurations

---

## [Previous] - v0.4: Trick Detection & Posing

### Added

- **Trick Detection System**: Real-time rotation accumulation monitoring and UI overlay
- **Procedural Pose Editor**: Bone tree UI, local override system, JSON pose persistence
- **Mirror Pose Tool**: Bilateral pose editing for stance creation

---

## [Previous] - v0.3: Character Controller & Locomotion

### Added

- **Walking Mode**: Gamepad state toggle, camera-relative movement, jump mechanic
- **Orbital Camera**: RS yaw/pitch control with smooth LERP presets
- **Riding Integration**: Physics locking, stability logic, procedural lean, push mechanic
- **State Manager**: PlayerState system (IDLE, RIDING, PUSHING)

---

## [Previous] - v0.2: Skeletal Animation Pipeline

### Added

- **Animation Samplers**: LINEAR, STEP, CUBIC_SPLINE interpolation
- **GPU Skinning**: Matrix palette shader with 4-bone influence
- **Animation Debugger**: ImGui clip selection, timeline scrubbing, bone visualizer

---

## [Previous] - v0.1: Foundation & Physics

### Added

- **Skateboard Physics**: Raycast suspension, procedural pop, stance system, input mapping
- **Atmosphere System**: Dynamic sky dome, directional light sync, fog system
- **Real-World Scaling**: 1.0m = 1.0 unit standard, physics calibration, metric grid
- **glTF 2.0 Support**: Geometry, PBR materials, hierarchy, skinning data
- **Level Editor**: Pro Dark UI, prefab system, JSON serialization, gizmo suite
- **Testing Infrastructure**: JUnit 5 + MockK, graphics regression testing

---

## Legacy Versions (Historical Reference)

### v0.0.x: Alpha Development

Initial prototype phase including engine boot, asset extraction, GPU sync, and obstacle library.

---

*For current development priorities, see TODO.md*
