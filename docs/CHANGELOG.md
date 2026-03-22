# SkateSim Engine Changelog

This document tracks the development history and major milestones of the SkateSim skateboarding simulation engine.

---

## [v0.36] - 2026-03-22: ECS Environment System

### Summary

Migrated all environment settings (sky, fog, atmosphere) from hardcoded `SceneData` properties to a proper ECS system.
The new `EnvironmentSystem` follows the established pattern from `DayNightCycleSystem` and `DirectionalLightSystem`
for cleaner architecture and better separation of concerns.

### Added

- **EnvironmentConfig data class**: Centralized environment configuration (`engine/ecs/config/EnvironmentConfig.kt`)
    - `skyColor: Vector3f = (0.6, 0.7, 0.9)` - Clear sky color
    - `skyTint: Vector3f = (1.0, 1.0, 1.0)` - Sky tint multiplier
    - `skyExposure: Float = 1.0f` - Sky exposure/brightness
    - `skyRotation: Float = 0.0f` - Sky rotation in degrees
    - `fogColor: Vector3f = (0.8, 0.8, 0.8)` - Fog color
    - `fogDensity: Float = 0.0f` - Fog density (0 = no fog)
    - `fogGradient: Float = 1.5f` - Fog gradient falloff
    - `reset()` method for restoring default values
    - `applyPreset()` method with 5 environment presets
    - **Impact**: High - Single source of truth for environment state

- **Environment presets**: Quick configuration for common environments (`engine/ecs/config/EnvironmentConfig.kt`)
    - `CLEAR_DAY` - Light blue sky with minimal fog (0.0008 density)
    - `CLOUDY` - Gray overcast sky with moderate fog (0.02 density)
    - `FOGGY` - Dense atmospheric fog (0.05 density)
    - `SUNSET` - Warm orange/red sunset colors
    - `NO_FOG` - Clear visibility with no fog
    - **Impact**: Medium - Quick environment setup

- **EnvironmentSystem ECS**: Proper ECS system for environment management (`engine/ecs/systems/EnvironmentSystem.kt`)
    - Extends `System` with `ExecutionPriority.EARLY`
    - Constructor injection for `StringManager`
    - Owns `EnvironmentConfig` instance
    - Full ImGui interface with:
        - 5 environment preset buttons
        - Sky configuration section (color, tint, exposure, rotation)
        - Fog configuration section (color, density, gradient)
        - Reset to defaults button
    - **Impact**: High - Proper ECS ownership of environment state

- **ImGui string resources**: 17 new localized strings (`values/strings.properties`, `values/strings_fr.properties`)
    - lbl.environment_system.header, lbl.environment_system.presets
    - lbl.environment_system.preset_clear_day, preset_cloudy, preset_foggy, preset_sunset, preset_no_fog
    - lbl.environment_system.sky_header, sky_color, sky_tint, sky_exposure, sky_rotation
    - lbl.environment_system.fog_header, fog_color, fog_density, fog_gradient
    - lbl.environment_system.reset_to_defaults
    - **Impact**: Low - Full localization support

- **Unit tests**: 23 comprehensive tests for environment system
    - `EnvironmentConfigTest.kt` (12 tests) - Default values, reset, presets, property isolation
    - `EnvironmentSystemTest.kt` (11 tests) - Initialization, presets, reset, config access
    - **Impact**: High - Ensures environment system correctness

### Changed

- **SceneData cleaned up**: Removed environment properties (`engine/ecs/scene/SceneData.kt`)
    - Removed: `skyColor`, `skyTint`, `skyExposure`, `skyRotation`
    - Removed: `fogColor`, `fogDensity`, `fogGradient`
    - Kept: `timeOfDay`, `useAmbient`, `ambientLight` (for DayNightCycleSystem integration)
    - Updated KDoc to note environment settings moved to EnvironmentSystem
    - **Impact**: High - Clean separation of concerns, no deprecated code

- **EnvironmentWindow refactored**: Delegates to EnvironmentSystem (`editor/windows/EnvironmentWindow.kt`)
    - Gets `EnvironmentSystem` from `scene.systemManager`
    - Replaced direct SceneData manipulation with `system.imgui()` call
    - Environment controls now in dedicated collapsing header
    - Added KDoc explaining window responsibilities
    - **Impact**: High - Proper ECS integration

- **LevelEditorSceneInitializer updated**: Adds EnvironmentSystem (`editor/LevelEditorSceneInitializer.kt`)
    - Removed hardcoded environment setup (previously lines 71-74)
    - Added `scene.addSystem(EnvironmentSystem(stringManager))`
    - Environment now initialized through proper ECS channel
    - **Impact**: Medium - Remove hardcoded initialization

- **SkyDomeRenderer updated**: Reads from EnvironmentSystem (`engine/render/renderer/SkyDomeRenderer.kt`)
    - Gets `EnvironmentSystem` from scene
    - Reads skyRotation, skyTint, skyExposure from `system.config`
    - Reads fogColor, fogDensity, fogGradient from `system.config`
    - Falls back to default values when system unavailable
    - **Impact**: High - Rendering pipeline uses ECS system

- **LightingUniformsLoader updated**: Added environmentConfig parameter (
  `engine/render/renderer/LightingUniformsLoader.kt`)
    - Added `environmentConfig: EnvironmentConfig? = null` parameter
    - Reads fog settings from environmentConfig with defaults fallback
    - Maintains backwards compatibility with optional parameter
    - **Impact**: High - Rendering pipeline uses ECS system

- **GeometryPass updated**: Reads skyColor from EnvironmentSystem (`engine/render/renderer/passes/GeometryPass.kt`)
    - Gets `EnvironmentSystem` from scene
    - Uses `system.config.skyColor` for framebuffer clear color
    - Passes `environmentConfig` to `lightingUniformsLoader.loadLightingUniforms()`
    - Falls back to default sky color when system unavailable
    - **Impact**: High - Clear color from ECS system

### Architecture

- **ECS Environment Pattern**: Follows established system architecture
    1. EnvironmentConfig owns all environment state
    2. EnvironmentSystem provides ECS integration and ImGui UI
    3. Rendering systems read from EnvironmentSystem.config
    4. Sensible defaults when system unavailable
    5. 5 presets for quick environment setup

- **Clean SceneData**: Separation of concerns
    - SceneData: Core scene data (time, ambient, gravity, level path)
    - EnvironmentSystem: Sky, fog, atmosphere settings
    - DayNightCycleSystem: Time progression, sun direction, auto-ambient
    - DirectionalLightSystem: Shadow mapping, light direction

### Removed

- **Deprecated environment properties from SceneData**: Clean migration
    - All environment settings now in EnvironmentSystem.config
    - No backwards compatibility shims needed
    - Rendering code uses defaults when EnvironmentSystem unavailable
    - **Impact**: High - No technical debt from migration

### Verified

- **v0.36 Integration**: Full verification
    - ✅ EnvironmentSystem initializes with correct defaults
    - ✅ 5 presets apply correct values
    - ✅ ImGui interface functional with all controls
    - ✅ SkyDomeRenderer reads from EnvironmentSystem
    - ✅ LightingUniformsLoader uploads fog uniforms correctly
    - ✅ GeometryPass clears with correct sky color
    - ✅ String localization (English/French)
    - ✅ 23/23 unit tests passing
    - ✅ Build successful with no deprecation warnings

---

## [v0.35] - 2026-03-22: Advanced Grid Features

### Summary

Completed advanced grid features including center marker, edge fading, secondary grid plane, and snap visualization.
These enhancements provide better spatial orientation and visual polish for the level editor.

### Added

- **Center marker crosshair**: Yellow marker at world origin (`engine/ecs/systems/GridLines.kt`)
  - `showCenterMarker: Boolean = true` - Toggle visibility
  - `centerMarkerColor: Vector3f = (1.0, 1.0, 0.0)` - Yellow by default
  - `centerMarkerDistance: Float = 30.0` - Max camera distance to show marker
  - Dynamic size scaling (0.5-2.0 units based on camera distance)
  - Renders as X-Z crosshair at world origin (0, 0, 0)
  - **Impact**: Medium - Helps users orient in world space

- **Edge fading**: Smooth grid fade at extent boundaries
  - `edgeFadeEnabled: Boolean = true` - Toggle edge fading
  - `edgeFadeStart: Float = 0.7f` - Normalized distance (0-1) where fading starts
  - Smoothstep interpolation for smooth fade transition
  - Prevents hard cutoff at grid extent boundary
  - Creates smoother infinite grid illusion
  - **Impact**: Medium - Improved visual polish

- **Secondary grid plane**: Additional grid at custom Y height
  - `secondaryGridEnabled: Boolean = false` - Toggle secondary grid
  - `secondaryGridY: Float = 2.0f` - Y position of secondary grid
  - `secondaryGridColor: Vector3f = (0.0, 0.8, 0.8)` - Cyan by default
  - 50% alpha for visual distinction from primary grid
  - Useful for multi-level level design and ceiling work
  - **Impact**: Low - Niche feature for complex scenes

- **Snap visualization**: Green marker at nearest grid intersection
  - `snapVisualizationEnabled: Boolean = true` - Toggle snap marker
  - `snapMarkerColor: Vector3f = (0.0, 1.0, 0.0)` - Bright green
  - Calculates snap point from camera X-Z position
  - Only visible when camera is close to grid (< 20 units)
  - Renders as small cross at snap point
  - **Impact**: Low - Visual aid for object placement

- **ImGui advanced features section**: New controls in GridLines.imgui()
  - Center marker: visibility, color, distance sliders
  - Edge fading: enable toggle, fade start slider
  - Secondary grid: enable toggle, Y position, color picker
  - Snap visualization: enable toggle, color picker
  - All controls grouped under "Advanced Features" header
  - **Impact**: Medium - Full runtime control of advanced features

- **String resources**: 11 new localized strings (`values/strings.properties`, `values/strings_fr.properties`)
  - lbl.grid.advanced, lbl.grid.show_center_marker, lbl.grid.center_marker_color
  - lbl.grid.center_marker_distance, lbl.grid.edge_fade_enabled
  - lbl.grid.edge_fade_start, lbl.grid.secondary_grid_enabled
  - lbl.grid.secondary_grid_y, lbl.grid.secondary_grid_color
  - lbl.grid.snap_visualization_enabled, lbl.grid.snap_marker_color
  - **Impact**: Low - Full localization for new controls

- **Unit tests**: 6 new tests for A35 features (`test/.../GridLinesTest.kt`)
  - GridConfig A35 default values test
  - calculateEdgeFade inside fade start test
  - calculateEdgeFade at edge test
  - calculateEdgeFade in fade zone test
  - calculateEdgeFade disabled test
  - GridConfig resetToDefaults restores A35 values test
  - **Impact**: High - Ensures edge fading logic is correct

### Changed

- **GridConfig extended**: Added 10 new properties for A35 features
  - Center marker: showCenterMarker, centerMarkerColor, centerMarkerDistance
  - Edge fading: edgeFadeEnabled, edgeFadeStart
  - Secondary grid: secondaryGridEnabled, secondaryGridY, secondaryGridColor
  - Snap visualization: snapVisualizationEnabled, snapMarkerColor
  - Updated resetToDefaults() to restore all new properties
  - **Impact**: High - Expanded configuration options

- **renderGridLines refactored**: Extracted grid rendering to dedicated method
  - New private method renderGridLines() with parameters for Y position and colors
  - Supports rendering multiple grid planes (primary + secondary)
  - Applies edge fading per line using withAlpha() extension
  - **Impact**: Medium - Cleaner code organization

- **editorUpdate refactored**: Modular rendering pipeline
  - Separate method calls: renderGridLines(), renderCenterMarker(), renderOriginAxes(), renderSnapVisualization()
  - Clear separation of concerns for each visual feature
  - Easier to maintain and extend
  - **Impact**: Medium - Improved code maintainability

### Architecture

- **withAlpha() extension**: Vector3f alpha simulation
  - Scales RGB values by alpha factor
  - Simulates alpha blending for debug line rendering
  - Used for edge fading and secondary grid transparency
  - **Impact**: Low - Utility function for color manipulation

- **Modular rendering**: Separated rendering concerns
  - renderGridLines(): Core grid line rendering with edge fade
  - renderCenterMarker(): World origin crosshair
  - renderOriginAxes(): RGB axis lines at origin
  - renderSnapVisualization(): Snap point marker
  - calculateEdgeFade(): Edge fade alpha calculation
  - **Impact**: Medium - Better code organization

### Verified

- **A35 Features**: Full integration verification
  - ✅ Center marker renders at origin with correct color
  - ✅ Edge fading smooth with smoothstep interpolation
  - ✅ Secondary grid renders at custom Y with 50% alpha
  - ✅ Snap visualization shows nearest grid intersection
  - ✅ ImGui controls functional for all new features
  - ✅ String localization (English/French)
  - ✅ 26/26 unit tests passing (20 original + 6 new)

---

## [v0.34] - 2026-03-22: Godot-style Grid Implementation

### Summary

Completed the Godot-style 3D grid system with dynamic extent, LOD transitions, ImGui configuration, and performance
optimizations.
The grid now follows the camera with an infinite scrolling effect, proper major/minor line distinction, and origin axes
fixed at world origin.

### Added

- **GridConfig data class**: Mutable configuration for grid system (`engine/ecs/systems/GridLines.kt`)
  - `majorStep: Float = 1.0f` - Spacing between major grid lines
  - `minorStep: Float = 0.1f` - Spacing between minor grid lines
  - `majorColor: Vector3f = (0.4, 0.4, 0.4)` - Godot-style dark gray
  - `minorColor: Vector3f = (0.25, 0.25, 0.25)` - Lighter gray for subtlety
  - `minExtent: Float = 10.0f` - Minimum grid extent when camera is close
  - `maxExtent: Float = 100.0f` - Maximum grid extent when camera is far
  - `lodCloseDistance: Float = 5.0f` - Distance where minor lines start fading
  - `lodFarDistance: Float = 20.0f` - Distance where minor lines are fully hidden
  - `showGrid: Boolean = true` - Toggle grid visibility
  - `showOriginAxes: Boolean = true` - Toggle origin axes visibility
  - `gridYOffset: Float = -0.1f` - Y offset to prevent Z-fighting
  - `resetToDefaults()` method for restoring default values
  - **Impact**: High - Full runtime configuration of grid system

- **GridLines.imgui()**: Complete ImGui configuration panel
  - Visibility toggles (Show Grid, Show Origin Axes)
  - Grid spacing sliders (Major Step, Minor Step)
  - LOD distance sliders (Close/Far)
  - Extent settings (Min/Max)
  - Color pickers (Major/Minor line colors)
  - Z-fighting offset slider
  - Reset to Defaults button
  - **Impact**: High - Real-time grid tuning without code changes

- **String resources**: 20+ localized strings for grid UI (`values/strings.properties`, `values/strings_fr.properties`)
  - lbl.grid.header, lbl.grid.show_grid, lbl.grid.show_origin_axes
  - lbl.grid.spacing, lbl.grid.major_step, lbl.grid.minor_step
  - lbl.grid.lod_settings, lbl.grid.lod_close, lbl.grid.lod_far
  - lbl.grid.extent_settings, lbl.grid.min_extent, lbl.grid.max_extent
  - lbl.grid.colors, lbl.grid.major_color, lbl.grid.minor_color
  - lbl.grid.z_fighting, lbl.grid.y_offset, lbl.grid.reset_to_defaults
  - **Impact**: Low - Full localization support for grid UI

- **Unit tests**: 20+ comprehensive tests (`test/.../GridLinesTest.kt`)
  - Test dynamic extent calculation (6 tests)
  - Test LOD alpha interpolation (5 tests)
  - Test major/minor line detection (3 tests)
  - Test smoothstep interpolation (3 tests)
  - Test GridConfig default values, reset, and custom values (3 tests)
  - **Impact**: High - Ensures grid calculations are correct and stable

### Changed

- **GridLines performance optimizations**: Reduced per-frame allocations (`engine/ecs/systems/GridLines.kt`)
  - Added cached Vector3f objects (lineStart, lineEnd) to reuse instead of allocate
  - Cached constants: tanHalfFov, padding, axis colors (xAxisColor, yAxisColor, zAxisColor)
  - Pre-computed line endpoints (xMin, xMax, zMin, zMax) to avoid repeated calculations
  - Added frustum culling: skips lines outside camera view (extent * 1.2f margin)
  - Optimized calculateGridExtent() and calculateMinorLineAlpha() to use config directly
  - **Impact**: Medium - Reduced GC pressure in editorUpdate()

- **Z-fighting offset**: Changed default from -0.001f to -0.1f
  - Grid now renders further below world origin to avoid ground plane conflicts
  - Configurable via ImGui (-1.0 to 0.0 range)
  - Added KDoc explaining Z-fighting prevention
  - **Impact**: Medium - Eliminates grid/ground Z-fighting artifacts

- **GridLines constructor**: Added stringManager dependency
  - Updated LevelEditorSceneInitializer to pass stringManager
  - Follows pattern from other systems (DayNightCycleSystem, InputSystem, etc.)
  - **Impact**: Low - Required for ImGui localization

- **Origin axes colors**: Fixed to match Godot convention
  - X-axis: Red (1, 0.2, 0.2) - already correct
  - Y-axis: Green (0.2, 1, 0.2) - was missing, now rendered vertically
  - Z-axis: Blue (0.2, 0.2, 1) - was using green, now correct
  - Axes fixed at world origin (0, 0, 0), not following camera
  - Dynamic length based on camera distance (5-20 units, fades at 50 units)
  - **Impact**: High - Correct spatial orientation for users

### Architecture

- **Grid System Pattern**: Complete Godot-style implementation
  1. Dynamic extent: `extent = cameraDistance * tan(fov/2) * padding`
  2. Infinite scrolling: grid center snaps to major step intervals
  3. LOD system: smoothstep interpolation for minor line alpha
  4. Origin axes: fixed at (0,0,0) with dynamic length
  5. ImGui integration: full configuration panel in SystemsWindow
  6. Performance: cached vectors, frustum culling, pre-computed endpoints

### Verified

- **Grid Rendering**: Full integration verification
  - ✅ Dynamic extent fills viewport at any camera height
  - ✅ LOD transitions smooth (no popping) with smoothstep interpolation
  - ✅ Origin axes fixed at world origin with correct colors (RGB = XYZ)
  - ✅ Godot-style colors (major: 0.4, minor: 0.25)
  - ✅ Z-fighting offset prevents ground plane conflicts
  - ✅ ImGui panel functional with all controls
  - ✅ String localization (English/French)
  - ✅ 20/20 unit tests passing

---

## [v0.31] - 2026-02-25: Systems ImGui Integration & ImGuiLayer Refactoring

### Summary

Completed comprehensive ImGui integration for ECS systems and refactored ImGuiLayer for cleaner window management.
Created centralized SystemsWindow for system debugging, implemented imgui() for all major systems, and extracted menu
bar logic into dedicated class.

### Added

- **SystemsWindow**: Centralized window for system debugging and control (`editor/windows/SystemsWindow.kt`)
    - Auto-discovers all systems via SystemManager
    - Displays system enabled status with visual indicator (grayed out when disabled)
    - Shows system execution priority in header
    - Calls each system's imgui() method inside collapsing headers
    - Context menu for enabling/disabling systems
    - Supports runtime system toggling
    - **Impact**: High - All system controls now discoverable in one place

- **System UI Metadata**: Added displayName property to base System class (`engine/ecs/systems/System.kt`)
    - `open val displayName: String` defaults to javaClass.simpleName
    - Can be overridden for custom display names
    - Used by SystemsWindow for header labels
    - **Impact**: Low - Enables flexible system naming in UI

- **DayNightCycleSystem.imgui()**: Full day/night cycle controls (`engine/ecs/systems/DayNightCycleSystem.kt`)
    - Time of day slider (0-24 hours)
    - Day duration slider (60-600 seconds)
    - Auto-ambient toggle
    - Read-only displays: sun direction, sun color, ambient color, intensities
    - Current phase display (Night/Dawn/Day/Dusk)
    - **Impact**: High - Real-time day/night tuning without code changes

- **InputSystem.imgui()**: Complete input configuration and debugging (`engine/ecs/systems/InputSystem.kt`)
    - Deadzone settings (left stick, right stick, trigger threshold)
    - Sensitivity settings (mouse, controller)
    - Movement thresholds (movement, sprint)
    - Physics settings (jump impulse, walk/run speed, rotation speed, take off time, input smoothing)
    - Live gamepad state debug (axes, buttons)
    - **Impact**: High - Real-time input tuning and debugging

- **AnimationSystem.imgui()**: Animation state visualization (`engine/ecs/systems/AnimationSystem.kt`)
    - Animated object count display
    - Cache dirty status
    - Global speed multiplier (0-3x) for slow-motion effects
    - Per-object animation state (name, time, playing, blending)
    - **Impact**: Medium - Animation debugging and tuning

- **DirectionalLightSystem.imgui()**: Shadow quality controls (`engine/ecs/systems/DirectionalLightSystem.kt`)
    - Shadow distance slider (10-200m)
    - Auto calculate bounds toggle
    - Manual orthographic bounds (left/right/bottom/top)
    - Shadow quality settings (stabilize projection, depth bias, slope-scaled bias)
    - **Impact**: High - Real-time shadow quality tuning

### Changed

- **ImGuiLayer Window Management**: Complete refactor for cleaner architecture (`editor/imgui/ImGuiLayer.kt`)
    - Created IWindow and IWindowWithScene interfaces for type-safe window handling
    - Created EditorWindow data class for window metadata (name, instance, visibility, requiresScene)
    - Created editorWindows registry list for centralized window management
    - Refactored window rendering: 10 if statements → single forEach loop
    - Refactored View menu: 10 checkbox calls → single forEach loop
    - Refactored dock builder: hardcoded → dynamic based on registry
    - Added getHoveredGameObject() public method for Engine access
    - SystemsWindow default visibility: false → true for better discoverability
    - **Impact**: High - ~200 lines reduction, easier to add/remove windows

- **EditorMenuBar Extracted**: Menu bar logic moved to dedicated class (`editor/imgui/EditorMenuBar.kt`)
    - ~150 lines moved from ImGuiLayer to EditorMenuBar
    - Four private methods: buildFileMenu(), buildEditMenu(), buildSettingsMenu(), buildViewMenu()
    - All dependencies injected via constructor
    - ImGuiLayer reduced to lifecycle management only
    - **Impact**: Medium - Better separation of concerns, improved testability

- **StringManager Localization**: All system UI text externalized
    - DayNightCycleSystem: 15 string keys
    - InputSystem: 22 string keys
    - AnimationSystem: 10 string keys
    - DirectionalLightSystem: 13 string keys
    - SystemsWindow: 5 string keys
    - Total: 70+ new string keys in strings.properties and strings_fr.properties
    - **Impact**: High - Full localization support for system UI

- **Constructor Injection**: Systems receive StringManager via constructor
    - DayNightCycleSystem: Added stringManager constructor parameter
    - InputSystem: Added stringManager constructor parameter (4th param)
    - AnimationSystem: Changed to primary constructor with stringManager
    - DirectionalLightSystem: Added stringManager constructor parameter
    - Updated LevelEditorSceneInitializer to pass StringManager
    - Updated KoinModule.kt for InputSystem (4 params)
    - **Impact**: Medium - Cleaner dependency injection, no KoinComponent in systems

- **Window Interfaces**: All 10 dockable windows implement interfaces
    - IWindow (no Scene): PropertiesWindow, GameViewWindow, AssetBrowserWindow, ProfilerWindow, ConsoleWindow
    - IWindowWithScene (requires Scene): SceneHierarchyWindow, EnvironmentWindow, PhysicsTunerWindow,
      InputTestingWindow,
      SystemsWindow
    - **Impact**: Low - Type-safe window rendering

### Architecture

- **Systems ImGui Pattern**: Complete implementation
    1. Systems implement imgui() with custom controls
    2. SystemsWindow auto-discovers via SystemManager
    3. Each system displayed in collapsing header
    4. Enabled/disabled state visible and toggleable
    5. Context menu for quick enable/disable

- **Window Registry Pattern**: Centralized management
    1. EditorWindow data class holds metadata
    2. editorWindows list is single source of truth
    3. Rendering, menus, and docking all use registry
    4. Easy to add/remove windows (one line)

- **Menu Bar Separation**: Clean architecture
    1. EditorMenuBar owns all menu logic
    2. ImGuiLayer owns lifecycle (init, update, destroy)
    3. Dependencies injected via constructor
    4. Each menu in dedicated method

---

## [v0.30] - 2026-02-25: Shadow Pipeline Polish & Systems ImGui Plan

### Summary

Completed final shadow pipeline improvements and identified ImGui architecture improvements for system UI management.

### Added

- **Shadow Peter-Panning Fix**: Reduced shadow detachment artifacts (`engine/ecs/config/DirectionalLightConfig.kt`)
    - `depthBias: Float = 0.001f` - Reduced from 0.005 (was causing visible detachment)
    - `slopeScaledBias: Float = 0.002f` - Reduced from 0.01
    - **Impact**: High - Shadows now appear properly attached to casters while preventing acne

### Changed

- **GeometryPass Shadow Bias Defaults**: Updated fallback values to match new defaults
    - Fallback depthBias: 0.005 → 0.001
    - Fallback slopeScaledBias: 0.01 → 0.002
    - Ensures consistency when light system is unavailable

### Architecture

- **Systems ImGui Pattern Identified**: Current system UI architecture has gaps
    - `System.imgui()` method exists but is unused by ImGuiLayer
    - `Component.imgui()` has reflection-based auto-UI, systems require manual windows
    - Multiple scattered windows (EnvironmentWindow, PhysicsTunerWindow, ProfilerWindow) instead of unified system
      inspector
    - **Future**: Create SystemsWindow for centralized system UI discovery and display

---

## [v0.29] - 2026-02-25: Shadow Quality Improvements

### Summary

Completed robust shadow frustum fitting with bounding sphere calculation and stabilization features for
production-quality shadow rendering.

### Added

- **Robust Shadow Frustum Fitting**: Bounding sphere-based shadow map coverage (
  `engine/ecs/systems/DirectionalLightSystem.kt`)
    - Calculates camera view frustum corners limited by shadowDistance
    - Computes bounding sphere center and radius from frustum corners
    - Rotation-invariant shadow map coverage (no more rotating shadow bounds)
    - Rounds radius to nearest texel for stabilization
    - **Impact**: High - Shadows now maintain consistent coverage regardless of camera rotation

- **Shadow Stabilization Logic**: Texel snapping to eliminate shimmering (
  `engine/ecs/systems/DirectionalLightSystem.kt`)
    - `stabilizeProjection: Boolean = true` - Enable/disable stabilization
    - Snaps light target to texel-sized grid in light space
    - Prevents shadow map crawling as camera moves subtly
    - **Impact**: High - Eliminates distracting shadow shimmering artifacts

- **High-Noon Up-Vector Fix**: Dynamic up-vector selection (`engine/ecs/systems/DirectionalLightSystem.kt`)
    - Detects when sun direction Y > 0.99 (near overhead)
    - Switches up vector from (0,1,0) to (0,0,1) to prevent lookAt failure
    - **Impact**: Medium - Prevents shadow map corruption at high noon

- **Shadow Clipping Fix**: Increased buffer and centering (`engine/ecs/systems/DirectionalLightSystem.kt`)
    - Increased shadow caster buffer from default to 500m
    - Centers shadow map on visible frustum rather than camera position
    - **Impact**: High - Eliminates shadow clipping for objects near camera

### Changed

- **DirectionalLightSystem.updateLightSpaceMatrix()**: Complete rewrite for robust shadows
    - Replaced manual ortho bounds with auto-calculated bounding sphere
    - Added texel snapping for stabilization
    - Added dynamic up-vector selection
    - Increased buffer distance for caster coverage
    - Optimized near/far planes for orthographic projection

### Architecture

- **Shadow Frustum Pipeline**: Multi-stage robust shadow calculation
    1. Calculate frustum corners from camera projection
    2. Compute bounding sphere (center + radius)
    3. Snap center to texel grid for stability
    4. Apply dynamic up-vector for edge cases
    5. Center orthographic bounds on sphere
    6. Add buffer for off-frustum casters

---

## [v0.28] - 2026-02-25: Shadow Quality & Robustness

### Summary

Completed comprehensive shadow pipeline improvements including VAO binding fixes, alpha masking support, gizmo viewport
fixes, and skater shadow rendering.

### Added

- **Alpha Masking in Shadow Pass**: Transparent object shadow support (`assets/shaders/shadow.glsl`)
    - Vertex shader: Passes texture coordinates to fragment shader
    - Fragment shader: Samples base color texture, discards fragments with alpha < cutoff
    - Uniforms: `uBaseColorTexture`, `uAlphaMode`, `uAlphaCutoff`, `uHasBaseColorTexture`
    - `ShadowRenderer`: Binds base color texture and uploads alpha uniforms for MASK mode
    - OPAQUE and BLEND modes render depth-only (all fragments)
    - **Impact**: Medium - Foliage and cutout objects now cast proper shadows

- **Framebuffer Completeness Check**: Runtime validation (`engine/render/ShadowMap.kt`)
    - Replaced `assert()` with `IllegalStateException`
    - Throws exception with GL framebuffer status code
    - Works in both debug and release builds
    - **Impact**: High - Catches shadow map initialization failures early

- **Shadow Pass Logging**: Diagnostic logging for skipped passes (`engine/render/renderer/passes/ShadowPass.kt`)
    - Uses `LoggerService.logEngine()` instead of println
    - Logs when light system is null or castShadows is false
    - Format: `[ShadowPass] Skipped: lightSystem=true/false, castShadows=true/false`
    - **Impact**: Low - Helps debug shadow rendering issues

- **Out-of-Bounds Shadow Fix**: Early exit for fragments beyond shadow map (`assets/shaders/shader_3d_default.glsl`)
    - Checks projCoords outside [-1, 1] range for x, y, z
    - Returns 0.0 (no shadow) instead of sampling border color
    - **Impact**: Medium - Eliminates shadow artifacts at screen edges

- **Shader Constant Extraction**: `SHADOW_TEXTURE_UNIT` constant (`engine/utils/ShaderConst.kt`)
    - `Uniforms.SHADOW_TEXTURE_UNIT = 5` - Centralized shadow map texture unit
    - Updated `LightingUniformsLoader.kt` and `GeometryPass.kt` to use constant
    - **Impact**: Low - Prevents texture unit conflicts

### Changed

- **ShadowRenderer VAO Binding**: Fixed skinned mesh shadow rendering (`engine/render/renderer/ShadowRenderer.kt`)
    - Changed from `vaoId.bind()` to `vaoId.bindVAO(rawModel.enabledAttributes)`
    - Properly enables attributes 6,7 (joints/weights) for skinned meshes
    - Uses `glBindVertexArray(0)` instead of `unbindVAO()` to preserve attribute state
    - **Impact**: High - Skinned character shadows now render correctly

- **ShadowRenderer Depth Testing**: Enabled depth write for shadow map (`engine/render/renderer/ShadowRenderer.kt`)
    - Added `glEnable(GL_DEPTH_TEST)` at start of render()
    - Added `glDepthMask(true)` to enable depth writes
    - **Impact**: Critical - Shadow map now receives depth values

- **Uniform Name Mismatch Fix**: Skater shadow rendering (`assets/shaders/shadow.glsl`)
    - Changed `uniform bool uHasSkin` to `uniform bool u_HasSkin`
    - Matches `ShaderConst.HAS_SKIN = "u_HasSkin"` constant
    - **Impact**: Critical - Skinning now works in shadow pass

- **TranslateGizmo Viewport Fix**: Dynamic viewport size (`editor/gizmos/TranslateGizmo.kt`)
    - Line 100: `screenToRay()` now uses `viewportSize.x, viewportSize.y`
    - Lines 159-160: `worldToScreen()` now uses dynamic viewport
    - Pattern: `val viewportSize = mouseListener.getGameViewportSize()`
    - **Impact**: High - Gizmo works correctly at non-1080p resolutions

- **RotationGizmo Viewport Fix**: Dynamic viewport size (`editor/gizmos/RotationGizmo.kt`)
    - Line 63: `screenToRay()` now uses `viewportSize.x, viewportSize.y`
    - **Impact**: High - Gizmo works correctly at non-1080p resolutions

- **ScaleGizmo Viewport Fix**: Dynamic viewport size (`editor/gizmos/ScaleGizmo.kt`)
    - Line 81: `screenToRay()` now uses `viewportSize.x, viewportSize.y`
    - Lines 162-163: `worldToScreen()` now uses dynamic viewport
    - **Bonus**: Added NaN guard (`axisScreen.lengthSquared() < 0.0001f`)
    - **Bonus**: Added scale clamping (min 0.01) to prevent physics crashes
    - **Impact**: High - Gizmo works correctly at non-1080p resolutions

### Verified

- **Shadow Rendering Pipeline**: Full integration verification
    - ✅ Shadow pass renders to ShadowMap (ShadowPass.kt executes before GeometryPass)
    - ✅ Geometry pass samples ShadowMap with correct uniforms (texture bound to unit 5)
    - ✅ PCF filtering uses correct texel size (1.0f / shadowMapResolution uploaded)
    - ✅ Shadow map texture binding uses constant (Uniforms.SHADOW_TEXTURE_UNIT = 5)
    - ✅ Alpha masking supported for transparent objects (shadow.glsl samples base color)
    - ✅ Skinned meshes render correctly to shadow map (VAO attributes enabled)

- **Day/Night Cycle Integration**: Full lighting verification
    - ✅ Sun direction updates from DayNightCycleSystem (trigonometry in updateSunDirection())
    - ✅ Sun color interpolates through day phases (dawn → noon → dusk → night)
    - ✅ Sun intensity interpolates (0.0 at night, 1.0 at day)
    - ✅ Ambient light interpolates with day/night (nightAmbient.lerp(dayAmbient, sunIntensity))
    - ✅ DirectionalLightSystem reads from DayNightCycleSystem.config each frame
    - ✅ LightingUniformsLoader uploads sun direction, color, intensity to shader
    - ✅ Environment Window time slider syncs with DayNightCycleSystem.getCycleTime()/setCycleTime()

- **Shadow Quality Settings**: All controls functional
    - ✅ Shadow distance slider affects coverage (DirectionalLightConfig.shadowDistance)
    - ✅ Stabilize projection reduces shimmering (texel snapping in updateLightSpaceMatrix())
    - ✅ Depth bias eliminates acne without peter-panning (bias uploaded to shader)

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

## [v0.27] - 2026-02-24: Shadow Pipeline Integration & Hover Highlighting Fix

### Summary

Integrated the shadow rendering system into the render pipeline and fixed critical hover highlighting issues.
The shadow pass now renders before the geometry pass, and hover detection works correctly for skinned characters.

### Added

- **ShadowPass Integration**: Dedicated render pass for shadow mapping (`engine/render/renderer/passes/ShadowPass.kt`)
    - Executes before geometry pass in render pipeline
    - Retrieves directional light from scene system
    - Respects `castShadows` configuration flag
    - Proper framebuffer bind/clear/unbind lifecycle

- **ShadowRenderer in RenderResources**: Shadow renderer instantiated and managed (`engine/render/RenderResources.kt`)
    - Added to `Renderers` data class
    - Created in `RenderResourcesFactory.createRenderers()`
    - Properly cleaned up in `Renderer.destroy()`

- **Render Pipeline Order**: Correct pass execution sequence (`engine/render/renderer/Renderer.kt`)
  -
    1. Shadow Pass - renders depth to shadow map

    -
        2. Picking Pass - renders object IDs for mouse selection
    -
        3. Geometry Pass - renders full scene with PBR shading
    -
        4. Debug Pass - renders debug visualization

### Fixed

- **Shadow Map Texture Binding**: GeometryPass now binds shadow map texture (
  `engine/render/renderer/passes/GeometryPass.kt`)
    - `glActiveTexture(GL_TEXTURE0 + 4)` before binding
    - `glBindTexture(GL_TEXTURE_2D, shadowMapTextureId)` to bind texture
    - Uploads texel size, depth bias, and slope-scaled bias uniforms
    - Shadows now correctly sampled in fragment shader

- **PickingPass FBO Binding**: Rebinds picking FBO after ShadowPass (`engine/render/renderer/passes/PickingPass.kt`)
    - ShadowPass unbinds to default FBO 0
    - PickingPass now explicitly rebinds its FBO before rendering
    - Prevents picking texture writes to wrong framebuffer

- **PickingPass Early Return**: Removed optimization that broke hover detection (
  `engine/render/renderer/passes/PickingPass.kt`)
    - Previously skipped when `activeGameObject != null`
    - Caused stale picking texture data when object was selected
    - Hover highlighting now works correctly during selection

- **VAO Attribute Binding in renderMeshPartSimple()**: Fixed skinning for picking pass (
  `engine/render/renderer/ModelRenderer.kt`)
    - `unbindVAO()` was disabling vertex attributes 6 and 7 (joints/weights)
    - Subsequent draws had disabled skinning attributes
    - Skater mesh rendered at wrong positions in picking texture
    - Changed to `glBindVertexArray(0)` to preserve attribute state
    - **Impact**: Critical - hover highlighting now works correctly for skinned characters

### Changed

- **GizmoSystem.getHoveredGameObject()**: Added accessor for SelectionGizmo (`engine/ecs/systems/GizmoSystem.kt`)
    - SelectionGizmo is inside GizmoSystem, not registered as separate system
    - `GameViewWindow` now calls `GizmoSystem.getHoveredGameObject()`
    - Cleaner encapsulation of gizmo subsystem

- **GameViewWindow.getHoveredObject()**: Updated to use GizmoSystem (`editor/windows/GameViewWindow.kt`)
    - Removed direct `SelectionGizmo` system lookup
    - Calls `GizmoSystem.getHoveredGameObject()` instead
    - Proper dependency injection pattern

### Architecture

- **Render Pass Separation**: ShadowPass extracted as dedicated class
    - Owns shadow map framebuffer lifecycle
    - Encapsulates directional light system access
    - Clean separation from geometry rendering

- **VAO State Management**: Attributes preserved between draws
    - `bindVAO()` enables attributes
    - Unbind with `glBindVertexArray(0)` only (don't disable)
    - Pattern applied to `renderMeshPartSimple()` and `renderMeshPart()`

---

## [v0.26] - 2026-02-24: Lighting Architecture Refactor

### Summary

Refactored lighting systems to own their configuration directly, eliminating artificial GameObjects and improving
architecture.

### Changed

- **DayNightCycleSystem**: Now owns configuration directly
    - Removed `DayNightCycleComponent`
    - System stores `DayNightCycleConfig` internally
    - Direct property access instead of entity lookup

- **DirectionalLightSystem**: Now owns configuration directly
    - Removed `DirectionalLightComponent`
    - System stores `DirectionalLightConfig` internally
    - Cleaner separation of concerns

- **LevelEditorSceneInitializer**: Updated initialization
    - Creates systems with config directly
    - No artificial GameObjects in scene hierarchy

- **EnvironmentWindow**: Updated to use system configs
    - Reads/writes from `DayNightCycleSystem.config`
    - Reads/writes from `DirectionalLightSystem.config`

### Architecture

- **Config Data Classes**: `DayNightCycleConfig` and `DirectionalLightConfig`
    - `@Serializable` for save/load support
    - All lighting parameters in one place
    - Systems own logic, configs own data

---

## [v0.25] - 2026-02-23: Lighting Integration

### Summary

Integrated the lighting and shadow systems (v0.24) into scene initialization, environment UI, and gameplay prefabs.
All lighting systems now properly initialized and configurable through the Environment window.

### Added

- **DayNightCycleSystem Integration**: Automatic day/night cycle in scene (`editor/LevelEditorSceneInitializer.kt`)
    - System registered in scene initialization pipeline
    - `DayNightCycleComponent` entity created with synced initial time
    - Day duration set to 300 seconds (5 minutes per full cycle)
    - Runs at EARLY priority before lighting calculations

- **DirectionalLightSystem Integration**: Automatic light updates (`editor/LevelEditorSceneInitializer.kt`)
    - System registered in scene initialization pipeline
    - `DirectionalLightComponent` entity created with shadow settings
    - Configured with default shadow distance (50m) and quality settings
    - Reads from DayNightCycleSystem for sun direction/color

- **Environment Window Light Controls**: Full lighting configuration (`editor/windows/EnvironmentWindow.kt`)
    - Time of day slider synced with DayNightCycleSystem
    - Sun direction/color/intensity controls (reads from DirectionalLightComponent)
    - Shadow settings section with all quality controls:
        - Shadow Distance (10-200m range)
        - Auto Calculate Bounds toggle
        - Stabilize Projection toggle (reduces shimmering)
        - Depth Bias (0-0.1, 4 decimal precision)
        - Slope-Scaled Bias (0-0.1, 3 decimal precision)
    - Removed duplicate `updateEnvironment()` logic (now handled by systems)

- **DayNightCycleSystem API**: Public time control methods (`engine/ecs/systems/DayNightCycleSystem.kt`)
    - `getCycleTime(): Float` - Get current time in hours (0-24)
    - `setCycleTime(time: Float)` - Set time of day
    - Enables UI and external systems to control day/night cycle

- **Shadow Flags on Prefabs**: Per-object shadow control (`game/prefabs/`, `editor/systems/PrefabsGenerator.kt`)
    - Skater: `castShadow = true`, `receiveShadow = true`
    - Rails, Ledges, Kickers, Banks, Quarter Pipes: Full shadow participation
    - Floor tiles: `castShadow = false`, `receiveShadow = true` (optimization)

### Changed

- **LevelEditorSceneInitializer**: Lighting system initialization
    - Added DayNightCycleSystem and DirectionalLightSystem registration
    - Created DayNightCycleComponent entity synced with `scene.sceneData.timeOfDay`
    - Created DirectionalLightComponent entity with default shadow configuration
    - Fixed light consistency issue (before vs after first play)

- **EnvironmentWindow**: Refactored to use system data
    - Time slider reads from `DayNightCycleSystem.getCycleTime()`
    - Time slider writes to both `scene.sceneData.timeOfDay` AND system
    - Sun controls read/write from `DirectionalLightComponent`
    - Removed ~60 lines of duplicate day/night interpolation code
    - Cleaner separation: systems handle logic, UI handles display

- **PrefabsGenerator**: Shadow flags on all spawned objects
    - Rail, Ledge, Kicker, ManualPad, Bank, QuarterPipe
    - All configured with `castShadow = true`, `receiveShadow = true`
    - Consistent shadow behavior across all environment objects

- **Tile**: Optimized shadow configuration
    - Floor tiles set to receive-only shadows
    - Large surfaces don't need to cast (performance optimization)
    - Still receives shadows from characters and objects

### Architecture

- **System-Component Pattern**: Lighting systems follow ECS pattern
    - Systems own the logic and computation
    - Components store configuration and computed state
    - UI reads/writes through component properties
    - Clean separation of concerns

- **Day/Night Flow**: Clear data flow through systems
    1. EnvironmentWindow sets time via slider
    2. DayNightCycleSystem advances time and computes sun state
    3. DirectionalLightSystem reads sun state and updates light
    4. LightingUniformsLoader uploads to shader
    5. Shader applies lighting and shadows to scene

- **Shadow Configuration Hierarchy**: Centralized shadow settings
    - DirectionalLightComponent stores all shadow parameters
    - EnvironmentWindow provides UI for tuning
    - DirectionalLightSystem applies settings to light space matrix
    - GeometryPass uploads uniforms to shader

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
