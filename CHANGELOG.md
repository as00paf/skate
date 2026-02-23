# SkateSim Engine Changelog

This document tracks the development history and major milestones of the SkateSim skateboarding simulation engine.

---

## [v0.23] - 2026-02-22: Input System Code Quality

### Summary

Completed technical debt cleanup for the input system architecture, improving consistency, maintainability, and
configurability.

### Added

- **EditorInputMappings**: Dedicated configuration for editor bindings (`engine/input/EditorInputMappings.kt`)
  - Separate from gameplay `InputMappings` for clear separation of concerns
  - Configurable bindings for gizmo tools (translate, rotate, scale, select)
  - Configurable bindings for editor tools (measure, deselect)
  - `getAllBindings()` method for UI display
  - `resetToDefaults()` method for resetting to default key bindings
  - Integrated into `SystemSettings` as `editorInputMappings` property

- **Editor Input State**: Extended `EditorInputStateComponent` with gizmo tool inputs
  - `gizmoTranslatePressed`, `gizmoRotatePressed`, `gizmoScalePressed`, `gizmoSelectPressed`
  - `measureToolPressed`, `deselectAllPressed`
  - All properties reset in `reset()` method
  - Configurable via `EditorInputMappings`

### Changed

- **checkButtonBindingBeginPress() Fixed**: Proper rising edge detection (`engine/ecs/systems/InputSystem.kt`)
  - Added `previousButtons` field to track previous frame button states
  - Updated `init()` to initialize `previousButtons` to null
  - Updated `update()` to store button states at end of each frame
  - Function now compares current vs previous state for true "begin press" detection
  - **Impact**: High - Trick inputs and one-frame actions now work correctly

- **Consistent inverted Flag**: Primary declarations match `resetToDefaults()` (`engine/input/InputMapping.kt`)
  - `moveUp` primary declaration now includes `inverted = true`
  - `cameraLookY` primary declaration now includes `inverted = true`
  - Ensures consistent behavior regardless of config load order
  - **Impact**: Medium - Saved configs now have consistent inversion behavior

- **getAxisFromBinding() Refactored**: Uses `InputBinding.inverted` flag
  - Removed hardcoded `if (axisIndex == 1 || axisIndex == 3)` check
  - Now respects `positiveBinding.inverted` property
  - Updated KDoc to explain inversion behavior
  - **Impact**: Low - Configuration-driven inversion instead of hardcoded logic

- **InputSystem Updated**: Uses `EditorInputMappings` for editor inputs
  - Added `editorMappings` property getter from settings
  - `pollEditorKeyboardInput()` now reads gizmo bindings from `editorMappings`
  - Camera movement (WASD) remains hardcoded (not rebindable)
  - **Impact**: Low - Editor bindings now configurable via UI (future feature)

### Removed

- **InputProvider.getMovementVector()**: Removed unused function
  - Function had hardcoded deadzone (0.15f) that bypassed `InputSettings`
  - Never called anywhere in the codebase
  - **Migration**: Use `InputSystem` with `InputStateComponent` instead
  - **Impact**: None - Function was unused

### Architecture

- **Editor Input Separation**: Clear separation between gameplay and editor inputs
  - `InputMappings` for gameplay actions (movement, tricks, camera, game state)
  - `EditorInputMappings` for editor actions (gizmos, tools)
  - Both stored in `SystemSettings` for unified save/load
  - Enables independent configuration and UI for each category

- **Button State Tracking**: Previous frame state for edge detection
  - `InputSystem.previousButtons` stores previous frame gamepad state
  - Enables proper "begin press" detection for all gamepad buttons
  - Pattern consistent with `GamepadListener` internal state tracking

---

## [v0.24] - 2026-02-23: Lighting & Shadowing Quality Improvements

### Summary

Completed Phase 7 quality improvements for the shadow mapping system, adding professional-grade features including
dynamic resolution, configurable coverage, anti-shimmering, and per-object shadow control.

### Added

- **Dynamic Shadow Map Resolution**: GPU-aware resolution selection (`engine/render/ShadowMap.kt`)
    - `getMaxShadowMapResolution()` queries `GL_MAX_TEXTURE_SIZE`
    - `createWithBestResolution(desiredResolution)` auto-selects best supported resolution
    - Default target: 4096x4096 (up to GPU maximum, typically 8192-32768)
    - Logs actual resolution at startup for debugging

- **Configurable Shadow Distance**: Per-light shadow coverage control (
  `engine/ecs/components/DirectionalLightComponent.kt`)
    - `shadowDistance: Float = 50f` - Maximum shadow rendering distance (10-200m range)
    - `autoCalculateBounds: Boolean = true` - Auto-calculate orthographic bounds from distance
    - Smaller distance = higher quality in smaller area
    - Larger distance = more coverage at lower resolution

- **Shadow Stabilization**: Texel snapping to eliminate shimmering (`engine/ecs/systems/DirectionalLightSystem.kt`)
    - `stabilizeProjection: Boolean = true` - Enable/disable stabilization
    - Snaps camera position to texel-sized grid in light space
    - Prevents shadow map crawling as camera moves
    - **Impact**: High - Eliminates distracting shadow shimmering artifacts

- **Depth Bias Controls**: Shadow acne prevention (`engine/ecs/components/DirectionalLightComponent.kt`)
    - `depthBias: Float = 0.005f` - Constant depth bias
    - `slopeScaledBias: Float = 0.01f` - Multiplier for steep angles
    - Shader implements slope-scaled bias calculation
    - **Impact**: High - Eliminates shadow acne without peter-panning

- **RenderComponent Shadow Flags**: Per-object shadow control (`engine/ecs/components/RenderComponent.kt`)
    - `castShadow: Boolean = true` - Controls whether object casts shadows
    - `receiveShadow: Boolean = true` - Controls whether object receives shadows
    - Enables optimization (skip shadows for transparent/background objects)
    - **Impact**: Medium - Fine-grained shadow control per object

### Changed

- **Shadow Map Initialization**: Uses dynamic resolution (`engine/render/RenderResourcesFactory.kt`)
    - Changed from `ShadowMap()` to `ShadowMap.createWithBestResolution(4096)`
    - Logs shadow map resolution: "Shadow map resolution: 4096x4096"
    - Passes actual resolution to GeometryPass for PCF texel size calculation

- **DirectionalLightSystem.updateLightSpaceMatrix()**: Added stabilization logic
    - Snaps camera position to texel grid when `stabilizeProjection = true`
    - Recalculates light view matrix with snapped position
    - Auto-calculates orthographic bounds from `shadowDistance`
    - Supports manual bounds override when `autoCalculateBounds = false`

- **Shader Shadow Sampling**: Slope-scaled bias implementation (`assets/shaders/shader_3d_default.glsl`)
    - Added `uShadowDepthBias` and `uShadowSlopeScaledBias` uniforms
    - Updated `calculateShadow()` to accept `normal` and `lightDir` parameters
    - Calculates bias as: `uShadowDepthBias + (uShadowSlopeScaledBias * (1.0 - NdotL))`
    - **Impact**: Steep surfaces get more bias, flat surfaces get minimal bias

- **GeometryPass**: Uploads shadow bias uniforms
    - Uploads `SHADOW_MAP_TEXEL_SIZE` as `1.0 / shadowMapResolution`
    - Uploads `SHADOW_DEPTH_BIAS` from light component
    - Uploads `SHADOW_SLOPE_SCALED_BIAS` from light component

- **DirectionalLightSystem ImGui**: Added quality controls
    - Shadow Distance slider (10-200m)
    - Auto Calculate Bounds checkbox
    - Stabilize Projection checkbox
    - Depth Bias slider (0-0.1, 4 decimal precision)
    - Slope-Scaled Bias slider (0-0.1, 3 decimal precision)
    - Displays effective shadow coverage in meters

### Architecture

- **Shadow Quality Pipeline**: Multi-stage quality control
    1. Resolution selection based on GPU capabilities
    2. Coverage control via shadow distance
    3. Stabilization via texel snapping
    4. Anti-acne via slope-scaled depth bias
    5. Per-object control via RenderComponent flags

- **Shader-Component Integration**: Tight coupling between component and shader
    - Component properties uploaded as uniforms each frame
    - Shader implements physics-correct calculations (slope-scaled bias)
    - ImGui provides real-time tuning with immediate feedback

---

## [Previous] - v0.23: Input System Code Quality

### Summary

Building on v0.20's input layer foundation, v0.21 completes the input mapping and configuration system with fully
rebindable controls, configurable sensitivities/deadzones, and proper architecture compliance across all camera systems.

### Added

- **Extended InputStateComponent**: Added comprehensive input state support (
  `engine/ecs/components/InputStateComponent.kt`)
    - Trick inputs: `flipLeftPressed`, `flipRightPressed`, `kickflipPressed`, `heelflipPressed`, `grabPressed`,
      `manualPressed`
    - Game state inputs: `pausePressed`, `resetPressed`, `cameraResetPressed`, `stanceChangePressed`
    - Crouch input: `crouchPressed` for manual setup and low-speed balance
    - Properties organized into logical sections (Movement, Jump, Tricks, Camera, Game State, Physics)
    - Comprehensive KDoc with usage examples

- **InputMappings Data Structure**: Complete input binding configuration (`engine/input/InputMapping.kt`)
    - `InputBinding` data class with `keyboardKey`, `gamepadButton`, `gamepadAxis`, `inverted` properties
    - 26 configurable bindings for all gameplay actions:
        - Movement (6): moveUp/Down/Left/Right, sprint, crouch
        - Jump (1): jump
        - Tricks (6): flipLeft/Right, kickflip, heelflip, grab, manual
        - Camera (3): cameraLookX/Y, cameraReset
        - Game State (4): pause, reset, stanceChange/Right
        - Editor (6): gizmo modes, measure, deselect
    - Helper methods: `getAllBindings()`, `resetToDefaults()`, `getDescription()`
    - Companion object factory methods: `keyboard()`, `gamepadButton()`, `gamepadAxis()`

- **InputSettings Data Structure**: Comprehensive input configuration (`editor/data/SystemSettings.kt`)
    - Deadzone configuration: leftStick, rightStick, trigger thresholds
    - Sensitivity configuration: mouse, controller
    - Movement thresholds: movement, sprint
    - Physics configuration: jumpImpulse, walkSpeed, runSpeed, rotationSpeed, takeOffTime, inputSmoothing
    - `validate()` method for range clamping

- **Input Testing Window**: Debug tool for visualizing input state (`editor/windows/InputTestingWindow.kt`)
    - Raw gamepad axis values with deadzone visualization
    - Interactive deadzone indicator showing stick position
    - Button state grid with color indicators
    - Processed InputStateComponent values display
    - Adjustable input settings with immediate feedback
    - Input bindings reference display
    - Access: View → Windows → Input Testing

- **Settings Window**: User-facing configuration UI (`editor/windows/SettingsWindow.kt`)
    - Tabbed interface (Input, Physics, Display)
    - Input tab: Deadzones, sensitivities, thresholds with sliders
    - Physics tab: Jump impulse, movement speeds, rotation, input smoothing
    - Display tab: Fullscreen, V-Sync, borderless window options
    - Save/Reset to Defaults functionality
    - Unsaved changes indicator
    - Access: Settings → Settings...

### Changed

- **InputSystem Refactored**: Full integration of configurable input mappings (`engine/ecs/systems/InputSystem.kt`)
    - Injected `SettingsManager` and `MouseListener` via constructor
    - Replaced all hardcoded keys with configurable `InputMappings`
    - Implemented mouse look integration for gameplay camera control
    - Made deadzones and thresholds configurable from `InputSettings`
    - Added helper methods: `getAxisFromBinding()`, `checkButtonBindingActive()`, `checkBindingActive()`
    - Gamepad axis inversion for Y-axis (GLFW coordinate system)
    - Editor input separated into dedicated `EditorInputStateComponent`

- **GameCamera Fixed**: Architecture compliance (`game/camera/GameCamera.kt`)
    - Removed direct `MouseListener` instantiation
    - Removed direct `IInputProvider` polling
    - Reads camera look from `InputStateComponent.cameraLook` (combines gamepad + mouse)
    - Made sensitivity configurable from `InputSettings.controllerSensitivity`
    - Updated `update()` signature to accept `InputStateComponent` parameter
    - Added time-based movement (`dt * 60f`) for frame-rate independence

- **PlayerController Extended**: Trick input handling (`game/player/PlayerController.kt`)
    - Added `handleTrickInputs()` method for processing trick inputs from `InputStateComponent`
    - Added trick input tracking: `flipLeftHeld`, `flipRightHeld` for combination detection
    - Added trick combination detection logic (kickflip, heelflip, grab, manual)
    - Added trick input logging for debugging
    - Made `desiredMoveDirection` public for `PlayerStateManager` access
    - Made `isJumping` public for state manager access

- **EditorCamera Fixed**: Proper input architecture (`editor/EditorCamera.kt`)
    - Created `EditorInputStateComponent` for editor-specific inputs
    - Removed direct `KeyListener` and `MouseListener` dependencies
    - InputSystem populates `EditorInputStateComponent` with keyboard and mouse data
    - Editor camera reads from component instead of polling hardware directly

- **KoinModule Updated**: Dependency injection for new systems
    - Registered `InputSystem` with `SettingsManager` and `MouseListener` dependencies
    - Updated `ThumbnailCache` and `BootManager` with proper dependencies

- **LevelEditorSceneInitializer Updated**: Scene integration
    - Added `InputSystem` injection and registration to scene systems
    - Creates editor input entity with `EditorInputStateComponent`
    - InputSystem runs first due to `EARLY` priority

### Fixed

- **PlayerStateManager Speed Calculation**: Proper velocity magnitude handling (`game/player/PlayerStateManager.kt`)
    - Changed from `max(linearVelocity.x, linearVelocity.z)` to vector magnitude calculation
    - Fixed animation triggering for left/up movement directions
    - Character now correctly transitions to WALKING/RUNNING state in all directions

- **Gamepad Deadzone Handling**: Removed rescaling logic (`engine/ecs/systems/InputSystem.kt`)
    - Values above deadzone now returned as-is (no normalization)
    - Fixed animation threshold detection for small stick movements
    - Fixed stick overshoot on release (no backward movement)

### Architecture

- **Input Layer Separation**: Clean ECS architecture for input handling
    - Raw Input Layer: `GamepadListener`, `KeyListener`, `MouseListener` → `IInputProvider`
    - Input Mapping Layer: `InputSystem` converts raw inputs to gameplay state using `InputMappings`
    - Gameplay State Layer: `InputStateComponent` stores gameplay inputs
    - Gameplay Logic Layer: `PlayerController`, `GameCamera` read `InputStateComponent`

- **Editor Input Separation**: Dedicated component for editor inputs
    - `EditorInputStateComponent` for editor-specific state (WASD, mouse look, orbit, scroll)
    - Separate from gameplay `InputStateComponent`
    - Allows independent configuration and processing

- **Execution Order**:
    1. `InputSystem` (EARLY) - Poll hardware, write `InputStateComponent` / `EditorInputStateComponent`
    2. `PlayerController` (DEFAULT) - Read `InputStateComponent`, apply physics
    3. `PlayerStateManager` (DEFAULT) - Read physics state, update animation state
    4. `Animator` (DEFAULT) - Read `PlayerStateManager`, select animation
    5. `AnimationSystem` (DEFAULT) - Apply animation to skeleton
    6. `EditorCamera` (DEFAULT) - Read `EditorInputStateComponent`, update camera

- **Configuration Flow**:
    1. `SettingsWindow` / `SettingsManager` → `InputSettings` / `InputMappings`
    2. `InputSystem` reads settings and applies deadzones/thresholds
    3. `InputStateComponent` receives processed input values
    4. Gameplay systems read component state

### Known Issues (Tracked for v0.23)

- `checkButtonBindingBeginPress()` returns "held" instead of "begin press" - needs previous state tracking
- `inverted` flag inconsistency between primary declarations and `resetToDefaults()`
- `getAxisFromBinding()` uses hardcoded axis inversion instead of `inverted` flag
- `InputProvider.getMovementVector()` has duplicate deadzone logic with hardcoded threshold
- Editor camera uses hardcoded keys instead of dedicated mappings

---

## [Previous] - v0.20: Input Layer Refactoring

### Summary

Successfully refactored the input layer to separate raw hardware polling from gameplay logic, establishing a clean ECS
architecture for input handling.

### Added

- **InputStateComponent**: New ECS component for gameplay input state (`engine/ecs/components/InputStateComponent.kt`)
  - Stores normalized movement direction (`moveDirection: Vector2f`)
  - Jump state tracking (`jumpPressed`, `jumpHeld`)
  - Sprint modifier (`sprintPressed`)
  - Camera look input (`cameraLook: Vector2f`)
  - Grounded state synced from physics (`isGrounded`)
  - `reset()` method for frame-by-frame state clearing
  - Serialization support with `@Contextual` annotations for Vector2f
  - Comprehensive KDoc with usage examples

- **InputSystem**: New ECS system for raw input → gameplay state conversion (`engine/ecs/systems/InputSystem.kt`)
  - Runs at `ExecutionPriority.EARLY` to ensure input readiness
  - Polls `IInputProvider` for gamepad and keyboard inputs
  - Deadzone handling for analog sticks (configurable thresholds)
  - Jump state machine (pressed → held → released)
  - Writes gameplay state to `InputStateComponent` on player entities
  - Keyboard input overrides gamepad for movement
  - Input mapping:
    - Move: Left Stick / WASD
    - Jump: A Button / Space
    - Sprint: Left Trigger / Left Shift
    - Camera Look: Right Stick (gamepad only, mouse TODO)

### Changed

- **PlayerController Refactored**: Separated input polling from physics logic
  - Removed `IInputProvider` dependency injection
  - Now reads gameplay input from `InputStateComponent` instead of polling hardware
  - Updated `update()` method to use `inputState.moveDirection` and `inputState.sprintPressed`
  - Updated `handleJumping()` to use `inputState.jumpPressed` (one-frame pulse)
  - Cleaned up unused smoothing variables (`smoothedInput`, `rawInput`, `smoothing`)
  - Fixed `getDesiredMoveDirection()` to accept `Vector2f` instead of `Vector3f`
  - Added comprehensive KDoc explaining responsibilities
  - **Impact**: PlayerController now focuses on physics, not input polling

- **KoinModule Updated**: Added `InputSystem` to dependency injection
  - Registered `InputSystem` as singleton in `engineModule`
  - Proper constructor injection with `IInputProvider`
  - Note: `GamepadListener` naming was already correct (no rename needed)

- **LevelEditorSceneInitializer Updated**: Integrated `InputSystem` into scene
  - Added `InputSystem` injection and registration to scene systems
  - Runs first due to `EARLY` priority, before `PlayerController`
  - Ensures input state is ready before gameplay systems execute

### Architecture

- **Input Layer Separation**: Clean ECS architecture for input handling
  - Raw Input Layer: `GamepadListener`, `KeyListener`, `MouseListener` → `InputProvider`
  - Input Mapping Layer: `InputSystem` converts raw inputs to gameplay state
  - Gameplay State Layer: `InputStateComponent` stores gameplay inputs
  - Gameplay Logic Layer: `PlayerController` reads `InputStateComponent`, applies physics

- **Execution Order**:
  1. `InputSystem` (EARLY) - Poll hardware, write `InputStateComponent`
  2. `PlayerController` (DEFAULT) - Read `InputStateComponent`, apply physics
  3. `PlayerStateManager` (DEFAULT) - Read physics state, update animation state
  4. `Animator` (DEFAULT) - Read `PlayerStateManager`, select animation
  5. `AnimationSystem` (DEFAULT) - Apply animation to skeleton

### Known Issues (Addressed in v0.21)

- Mouse look not implemented in `InputSystem.pollMouseInput()` (has TODO comment)
- Key bindings hardcoded in `InputSystem` (WASD, Space, Shift instead of using settings)
- `EditorCamera` directly polls `KeyListener`/`MouseListener` (bypasses `InputSystem`)
- `GameCamera` creates its own `MouseListener` and polls `IInputProvider` directly
- `InputStateComponent` limited to move/jump/sprint (no trick inputs, pause, reset, etc.)
- No configurable input mappings, deadzones, or sensitivities
- `SettingsManager.keyBindings` only covers editor gizmo controls

---

## [Previous] - v0.19: ECS Systems Code Quality & Architecture

### Fixed

- **AnimationSystem Double Update Bug**: Removed duplicate `animation.update()` call
    - Animation was being updated twice per frame when `blendTime <= 0`
    - Eliminated wasted computation and potential animation state corruption

- **Animation Blending Skeleton Collapse**: Rewrote blending logic to properly snapshot poses
    - Previous implementation corrupted skeleton transforms during cross-fading
    - Now applies each animation separately, snapshots the result, then blends between snapshots
    - Added `blendTransforms()` helper for proper position/rotation/scale interpolation (lerp/slerp)

### Architecture Changes

- **GizmoSystem Refactored**: Gizmos now owned directly instead of registered as separate systems
    - Previously: All 5 gizmos registered with Scene, all ran every frame (wasted computation)
    - Now: GizmoSystem owns gizmos privately, only active gizmo updated each frame
    - Removed `scene.addSystem()` calls for individual gizmos
    - Added KDoc explaining ownership model
    - **Performance**: 5x reduction in gizmo update overhead (only 1 gizmo updated per frame)

- **AnimationSystem Query Optimization**: Added caching for animated GameObjects
    - Previously: O(n) filter + list allocation every frame
    - Now: O(n) cache rebuild only when dirty, O(1) iteration otherwise
    - Added `animatedObjects` cache and `cacheDirty` flag
    - Added `invalidateCache()` method for external cache invalidation
    - **Performance**: ~60,000 filter operations/second → ~1-2 cache rebuilds/minute

### Code Quality

- **AnimationSystem Redundant Methods**: `update()` and `editorUpdate()` now share logic
    - Both methods had identical filtering and update logic
    - Reduced code duplication and maintenance burden

- **System Execution Order**: Added `ExecutionPriority` enum to `System` base class
  - Three priority levels: `EARLY`, `DEFAULT`, `LATE`
  - `SystemManager` sorts systems by priority before each update cycle
  - Uses lazy sorting (only sorts when systems are added/removed)
  - Changed `getSystem()` from inline reified to regular function with Class parameter
  - **Impact**: Deterministic execution order for systems with dependencies

- **Koin Field Injection Removed from Systems**: Converted all Systems to constructor injection
  - Affected: `GizmoSystem`, `MouseControls`, `EditorCamera`, `GridLines`
  - Removed `: KoinComponent` from all System subclasses
  - Removed `by inject()` property delegates
  - Updated `LevelEditorSceneInitializer` to inject and pass dependencies
  - Updated Koin module to register systems with constructor parameters
  - Assigned priorities:
    - `MouseControls`: EARLY (input processing)
    - `EditorCamera`: EARLY (input processing)
    - `AnimationSystem`: DEFAULT (physics/animation)
    - `GridLines`: LATE (rendering)
    - `GizmoSystem`: LATE (UI/tools)
  - **Impact**: Consistent with v0.16 pattern, clearer dependencies, better testability

---

## [Previous] - v0.18: Fix Test Compilation After Camera Refactoring

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
