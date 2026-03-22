# 🛹 SkateSim - 3D Grid System (Godot-style)

## Goal

Refactor the grid system to work like Godot Engine's 3D editor viewport grid. The current implementation is close but
needs improvements to achieve the polished, infinite-scrolling grid behavior that Godot is known for.

---

## 🔴 v0.34: Godot-style Grid Implementation (Planned)

### Summary

The current `GridLines` system already renders a horizontal X-Z plane grid that follows the camera. However, it needs
refinements to match Godot's polished grid behavior: larger extent, proper LOD, configurable settings, and correct
origin axes positioning.

### Godot Grid Characteristics

**Visual Style:**

- Single horizontal grid plane (X-Z at Y=0)
- Dark gray grid lines with major/minor distinction
- Origin axes (X=red, Y=green, Z=blue) fixed at world origin (0,0,0)
- Clean, minimal appearance

**Behavior:**

- Grid follows camera but snaps to major step intervals (infinite scrolling illusion)
- Grid extent is large enough to always fill the viewport
- LOD adjusts grid density based on camera distance
- No Z-fighting or popping artifacts

### Tasks

- [x] **A34.0.1: Increase grid extent and implement dynamic sizing**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: Fixed 40 lines (±4.0 units) - too small
  - Target: Dynamic extent based on camera distance from grid
  - Formula: `extent = cameraDistance * tan(fov/2) * padding`
  - Ensure grid always fills viewport regardless of camera height
  - Minimum extent: 10 units, Maximum extent: 100+ units

- [x] **A34.0.2: Implement camera distance-based LOD with smooth transitions**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: Basic LOD (minor lines hidden when > 20 units)
  - Target: Multi-level LOD with smooth fading
  - LOD 0 (close < 5 units): Show all minor + major lines
  - LOD 1 (medium 5-20 units): Show minor + major lines (current)
  - LOD 2 (far > 20 units): Hide minor lines, show only major lines
  - Add smooth alpha fading between LOD levels to prevent popping
  - Configurable LOD thresholds via constants
  - **Status**: Implemented with smoothstep interpolation for smooth transitions ✅

- [x] **A34.0.3: Fix origin axes positioning**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: Axes follow camera (wrong)
  - Target: Axes fixed at world origin (0, 0, 0)
  - X-axis: Red (1, 0.2, 0.2) - already correct
  - Y-axis: Green (0.2, 1, 0.2) - **currently missing!**
  - Z-axis: Blue (0.2, 0.2, 1) - **currently using green, needs fix**
  - Dynamic length based on camera distance (not hardcoded 100 units)
  - Only render axes when camera is within reasonable distance

- [x] **A34.0.4: Tune grid colors to match Godot style**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: majorColor = (0.3, 0.3, 0.3), minorColor = (0.15, 0.15, 0.15)
  - Target Godot-style:
    - Major lines: (0.4, 0.4, 0.4) - slightly brighter
    - Minor lines: (0.25, 0.25, 0.25) - lighter for subtlety
  - Consider alpha blending if DebugRenderer supports it
  - Test visibility against various background colors

- [x] **A34.0.5: Add GridConfig data class for settings**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Implemented mutable `GridConfig` data class with all configuration options
  - Added `resetToDefaults()` method for restoring default values
  - All properties are mutable vars for ImGui editing
  - Default values: majorStep=1.0, minorStep=0.1, gridYOffset=-0.1f
  - **Status**: Complete ✅

- [x] **A34.0.6: Add ImGui configuration panel**
  - Location: `GridLines.imgui()` method
  - Implemented full ImGui panel with:
    - Visibility toggles (Show Grid, Show Origin Axes)
    - Grid spacing sliders (Major Step, Minor Step)
    - LOD distance sliders (Close/Far)
    - Extent settings (Min/Max)
    - Color pickers (Major/Minor line colors)
    - Z-fighting offset slider
    - Reset to Defaults button
  - All strings localized (strings.properties, strings_fr.properties)
  - Added stringManager dependency via constructor injection
  - **Status**: Complete ✅
    - Color pickers for major/minor lines
    - Reset to defaults button
  - Register with SystemsWindow (follow DayNightCycleSystem pattern)
  - Add string resources for all labels (strings.properties)

- [x] **A34.0.7: Optimize line rendering performance**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Added cached Vector3f objects (lineStart, lineEnd) to reduce allocations
  - Cached constants: tanHalfFov, padding, axis colors
  - Pre-computed line endpoints (xMin, xMax, zMin, zMax)
  - Added frustum culling: skips lines outside camera view
  - Optimized calculateGridExtent() and calculateMinorLineAlpha() to use config directly
  - **Status**: Complete ✅

- [x] **A34.0.8: Eliminate Z-fighting and visual artifacts**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Changed default Y offset from -0.001f to -0.1f
  - Added KDoc explaining Z-fighting prevention
  - Configurable gridYOffset via ImGui (-1.0 to 0.0 range)
  - Grid renders below world origin to avoid ground plane conflicts
  - **Status**: Complete ✅

- [x] **A34.0.9: Update LevelEditorSceneInitializer**
  - Location: `editor/LevelEditorSceneInitializer.kt`
  - Updated GridLines constructor to pass stringManager
  - GridConfig passed with default values
  - Grid visible and properly configured by default
  - **Status**: Complete ✅

- [x] **A34.0.10: Add unit tests for grid calculations**
  - Location: `src/test/kotlin/com/pafoid/skate/engine/ecs/systems/GridLinesTest.kt`
  - Test LOD distance calculations (calculateMinorLineAlpha)
  - Test dynamic extent formula (calculateGridExtent)
  - Test major/minor line detection (isMajorLine)
  - Test smoothstep interpolation
  - Test GridConfig default values, resetToDefaults(), and custom values
  - Added 20+ unit tests covering all grid calculations
  - **Status**: Complete ✅

- [x] **A34.0.11: Documentation and string resources**
  - Updated KDoc for GridLines class with feature list
  - Documented GridConfig properties with detailed KDoc
  - Added 20+ string resources for ImGui labels (strings.properties, strings_fr.properties)
  - Documented Godot-style design inspiration in TODO.md
  - Updated CHANGELOG.md with v0.34 summary
  - **Status**: Complete ✅

---

## 🔴 v0.35: Advanced Grid Features (Planned)

### Summary

Optional polish features for the grid system after core Godot-style implementation is complete.

### Tasks

- [x] **A35.0.1: Add grid center marker**
  - Render small crosshair at world origin (0, 0, 0)
  - Yellow color (configurable) for visibility
  - Helps orient user in world space
  - Only visible when camera is close to origin (< 30 units by default)
  - Size dynamically scales with camera distance
  - **Status**: Complete ✅

- [x] **A35.0.2: Implement grid fading at edges**
  - Fade grid lines based on distance from grid center
  - Creates smoother infinite grid illusion
  - Prevents hard cutoff at grid extent boundary
  - Configurable fade start position (0.0-1.0, default 0.7)
  - Uses smoothstep interpolation for smooth fade
  - Toggle via ImGui (edgeFadeEnabled)
  - **Status**: Complete ✅

- [x] **A35.0.3: Add secondary grid plane toggle**
  - Optional secondary grid at custom Y height
  - User-specified Y value (-10 to 10 units)
  - Cyan color (configurable) to distinguish from primary grid
  - 50% alpha for visual distinction
  - Useful for multi-level level design and ceiling work
  - Toggle via ImGui (secondaryGridEnabled)
  - **Status**: Complete ✅

- [x] **A35.0.4: Grid snapping visualization**
  - Highlight nearest grid intersection from camera position
  - Green cross marker at snap point
  - Only visible when camera is close to grid (< 20 units)
  - Helps users visualize snap points for object placement
  - Toggle via ImGui (snapVisualizationEnabled)
  - **Status**: Complete ✅

---

## ✅ Completed Versions

### v0.35: Advanced Grid Features (Complete) ✅

- **A35.0.1**: Grid center marker with yellow crosshair at origin ✅
- **A35.0.2**: Edge fading with smoothstep interpolation ✅
- **A35.0.3**: Secondary grid plane with configurable Y position ✅
- **A35.0.4**: Snap visualization showing nearest grid intersection ✅
- **A35.0.5**: Extended ImGui panel with advanced features section ✅
- **A35.0.6**: Additional unit tests for edge fading and A35 features ✅
- **A35.0.7**: String resources for new ImGui controls (English/French) ✅

### v0.34: Godot-style Grid Implementation (Complete) ✅

- **A34.0.1**: Dynamic grid extent with camera-based sizing ✅
- **A34.0.2**: LOD system with smoothstep transitions ✅
- **A34.0.3**: Origin axes fixed at world origin with correct colors ✅
- **A34.0.4**: Godot-style grid colors ✅
- **A34.0.5**: GridConfig data class for settings ✅
- **A34.0.6**: ImGui configuration panel with full controls ✅
- **A34.0.7**: Optimized line rendering with caching and frustum culling ✅
- **A34.0.8**: Z-fighting elimination with configurable offset ✅
- **A34.0.9**: LevelEditorSceneInitializer updated ✅
- **A34.0.10**: Comprehensive unit tests (20+ tests) ✅
- **A34.0.11**: Documentation and string resources (English/French) ✅

### v0.33: Code Quality & Technical Debt (In Progress)

- See previous TODO.md items for planned work

### v0.32: ImGui Refactor Cleanup (Planned)

- See previous TODO.md items for planned work

### v0.31: Systems ImGui Integration & ImGuiLayer Refactoring (Complete) ✅

- SystemsWindow created for centralized system debugging and control
- System UI metadata (displayName) added to base System class
- DayNightCycleSystem.imgui() implemented with full day/night controls
- InputSystem.imgui() implemented with deadzones, sensitivity, and debug
- AnimationSystem.imgui() implemented with per-object state display
- DirectionalLightSystem.imgui() implemented with shadow quality controls
- All hardcoded strings replaced with StringManager localization (70+ keys)
- ImGuiLayer refactored with EditorWindow registry pattern
- IWindow and IWindowWithScene interfaces created for type safety
- EditorMenuBar extracted for menu bar logic (~150 lines moved)
- All 10 dockable windows updated to implement interfaces
- ~200 lines of code reduction through eliminated duplication
- SystemsWindow default visibility changed to true

### v0.30: Shadow Pipeline Polish (Complete)

- Shadow peter-panning fix (reduced bias defaults)
- Systems ImGui architecture identified

### v0.29: Shadow Quality Improvements (Complete)

- Robust shadow frustum fitting (bounding spheres)
- Shadow stabilization (texel snapping)
- High-noon up-vector fix
- Shadow clipping fix (increased buffer)

### v0.28: Shadow Quality & Robustness (Complete)

- ShadowRenderer VAO attribute binding fix
- Alpha masking support in shadow pass
- Framebuffer completeness check
- Shadow pass logging
- Out-of-bounds shadow artifact fix
- SHADOW_TEXTURE_UNIT constant extraction
- Skater shadow uniform name + depth testing fix
- Translate/Rotation/Scale gizmo viewport fixes
- Full shadow pipeline verification
- Day/night cycle integration verification

---

## Technical Notes

### Current GridLines.kt Analysis

**File Location:** `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/GridLines.kt`

**Current Behavior:**

- ✅ Renders X-Z plane grid (horizontal)
- ✅ Grid follows camera with major step snapping
- ✅ Major/minor line distinction
- ✅ Origin axes rendering (but wrong colors and positioning)

**Issues to Fix:**

- ❌ Grid extent too small (±4 units)
- ❌ No LOD system
- ❌ Origin axes follow camera (should be fixed at world origin)
- ❌ Missing Y-axis (green) at origin
- ❌ Z-axis uses green instead of blue
- ❌ Hardcoded axis length (100 units)
- ❌ No configuration options
- ❌ Fixed grid size regardless of camera distance

### Godot Grid Reference

**Key Characteristics:**

- Single horizontal grid plane (X-Z)
- Infinite scrolling via camera-follow + snap
- Large extent (fills viewport at any distance)
- LOD removes minor lines when camera is far
- Origin axes fixed at (0, 0, 0) with X=red, Y=green, Z=blue
- Clean, subtle appearance

**Coordinate System:**

- **X-axis**: Red (right/left)
- **Y-axis**: Green (up/down)
- **Z-axis**: Blue (forward/back)

---

## Historical Summary

**All v0.28, v0.29, v0.30, and v0.31 items are COMPLETE** ✅

See CHANGELOG.md for detailed implementation notes.

**Upcoming:**
- **v0.32**: ImGui Refactor Cleanup (remove EnvironmentWindow duplication)
- **v0.33**: Code Quality & Technical Debt (!! operators, resource management, animation, performance)
- **v0.34**: Godot-style Grid Implementation (new - this document)
- **v0.35**: Advanced Grid Features (new - this document)
