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

- [ ] **A34.0.1: Increase grid extent and implement dynamic sizing**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: Fixed 40 lines (±4.0 units) - too small
  - Target: Dynamic extent based on camera distance from grid
  - Formula: `extent = cameraDistance * tan(fov/2) * padding`
  - Ensure grid always fills viewport regardless of camera height
  - Minimum extent: 10 units, Maximum extent: 100+ units

- [ ] **A34.0.2: Implement camera distance-based LOD**
  - Location: `engine/ecs/systems/GridLines.kt`
  - LOD 0 (close < 5 units): Show minor lines (0.1 step) + major lines (1.0 step)
  - LOD 1 (medium 5-20 units): Show minor lines + major lines
  - LOD 2 (far > 20 units): Hide minor lines, show only major lines
  - Smooth transitions between LOD levels to prevent popping
  - Configurable LOD thresholds via constants

- [ ] **A34.0.3: Fix origin axes positioning**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: Axes follow camera (wrong)
  - Target: Axes fixed at world origin (0, 0, 0)
  - X-axis: Red (1, 0.2, 0.2) - already correct
  - Y-axis: Green (0.2, 1, 0.2) - **currently missing!**
  - Z-axis: Blue (0.2, 0.2, 1) - **currently using green, needs fix**
  - Dynamic length based on camera distance (not hardcoded 100 units)
  - Only render axes when camera is within reasonable distance

- [ ] **A34.0.4: Tune grid colors to match Godot style**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current: majorColor = (0.3, 0.3, 0.3), minorColor = (0.15, 0.15, 0.15)
  - Target Godot-style:
    - Major lines: (0.4, 0.4, 0.4) - slightly brighter
    - Minor lines: (0.25, 0.25, 0.25) - lighter for subtlety
  - Consider alpha blending if DebugRenderer supports it
  - Test visibility against various background colors

- [ ] **A34.0.5: Add GridConfig data class for settings**
  - Location: `engine/ecs/systems/GridLines.kt`
    ```kotlin
    data class GridConfig(
        val majorStep: Float = 1.0f,
        val minorStep: Float = 0.1f,
        val majorColor: Vector3f = Vector3f(0.4f, 0.4f, 0.4f),
        val minorColor: Vector3f = Vector3f(0.25f, 0.25f, 0.25f),
        val minExtent: Float = 10.0f,
        val maxExtent: Float = 100.0f,
        val lodClose: Float = 5.0f,
        val lodFar: Float = 20.0f,
        val showGrid: Boolean = true,
        val showOriginAxes: Boolean = true
    )
    ```
  - Pass config via constructor injection
  - Use default values for backward compatibility

- [ ] **A34.0.6: Add ImGui configuration panel**
  - Location: `GridLines.imgui()` method (new)
  - Implement `imgui()` method (follow System pattern from v0.31)
  - Settings to expose:
    - Grid visibility toggle
    - Origin axes visibility toggle
    - Major/minor step size sliders
    - LOD distance sliders
    - Color pickers for major/minor lines
    - Reset to defaults button
  - Register with SystemsWindow (follow DayNightCycleSystem pattern)
  - Add string resources for all labels (strings.properties)

- [ ] **A34.0.7: Optimize line rendering performance**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Profile current per-frame line allocations
  - Reuse Vector3f objects where possible (object pooling)
  - Consider pre-computing grid line endpoints
  - Reduce garbage collection pressure in `editorUpdate()`
  - Add frustum culling: skip lines outside camera view

- [ ] **A34.0.8: Eliminate Z-fighting and visual artifacts**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Current Y offset: -0.001f (may conflict with ground plane)
  - Test various offsets: -0.01f, -0.1f
  - Ensure grid renders above ground but below objects
  - Verify no clipping with terrain or props
  - Add depth bias if DebugRenderer supports it

- [ ] **A34.0.9: Update LevelEditorSceneInitializer**
  - Location: `editor/LevelEditorSceneInitializer.kt`
  - Pass GridConfig to GridLines constructor
  - Configure sensible defaults for level editor
  - Verify grid is visible and properly configured by default

- [ ] **A34.0.10: Add unit tests for grid calculations**
  - Location: `src/test/kotlin/com/pafoid/skate/engine/ecs/systems/`
  - Test LOD distance calculations
  - Test dynamic extent formula
  - Test major/minor line detection (snapping logic)
  - Verify axes positioning at origin
  - Test GridConfig default values

- [ ] **A34.0.11: Documentation and string resources**
  - Update KDoc for GridLines class
  - Document GridConfig properties
  - Add usage examples
  - Add string resources for ImGui labels (strings.properties)
  - Document Godot-style design inspiration

---

## 🔴 v0.35: Advanced Grid Features (Planned)

### Summary

Optional polish features for the grid system after core Godot-style implementation is complete.

### Tasks

- [ ] **A35.0.1: Add grid center marker**
  - Render small crosshair or square at world origin (0, 0, 0)
  - Distinct color (e.g., yellow or white)
  - Helps orient user in world space
  - Only visible when camera is close to origin

- [ ] **A35.0.2: Implement grid fading at edges**
  - Fade grid lines based on distance from camera
  - Creates smoother infinite grid illusion
  - Prevents hard cutoff at grid extent boundary
  - Use alpha blending if available

- [ ] **A35.0.3: Add secondary grid plane toggle**
  - Optional: Render grid at custom Y height (e.g., for ceiling work)
  - User-specified Y value
  - Different color to distinguish from primary grid
  - Useful for multi-level level design

- [ ] **A35.0.4: Grid snapping visualization**
  - Highlight nearest grid intersection under cursor
  - Show snap preview when placing objects
  - Different colors for different snap targets
  - Integrates with MouseControls system

---

## ✅ Completed Versions

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
