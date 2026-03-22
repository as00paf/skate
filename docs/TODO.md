# 🛹 SkateSim - 3D Grid System Refactoring

## Goal

Refactor the grid system from a 2D plane-based implementation to a proper 3D grid that renders all three axes (X, Y, Z).
The current implementation only renders horizontal grid lines (X-Z plane), which is insufficient for a 3D skateboarding
simulation where vertical positioning and obstacles are important.

---

## 🔴 v0.34: 3D Grid System Refactoring (Planned)

### Summary

The current `GridLines` system was designed for a 2D engine and only renders grid lines on the X-Z plane (horizontal
ground). This version tracks the refactoring to create a proper 3D grid system that visualizes all three axes,
supporting vertical level design for ramps, rails, and multi-level skate parks.

### Background

**Current Implementation Issues:**

- Only renders X-Z plane (horizontal grid at Y = -0.001f)
- Origin axes are hardcoded and don't scale with camera distance
- No Y-axis grid lines for vertical reference
- Fixed grid size doesn't adapt to camera zoom/distance
- No configuration options for grid density or visibility

**Target Implementation:**

- Full 3D grid with X-Y, Y-Z, and X-Z plane lines
- Configurable grid density and visibility per axis
- Dynamic LOD based on camera distance
- Proper axis-aligned rendering with consistent major/minor line patterns
- Editor configuration via ImGui

### Tasks

- [ ] **A34.0.1: Analyze current GridLines implementation**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Document current rendering approach
  - Identify 2D-specific assumptions
  - Profile performance impact of current implementation
  - Create baseline for comparison

- [ ] **A34.0.2: Design 3D grid architecture**
  - Define grid configuration data structure
  - Plan LOD system for grid density based on camera distance
  - Design major/minor line pattern for all 3 planes (X-Y, Y-Z, X-Z)
  - Specify color scheme for each axis plane

- [ ] **A34.0.3: Implement GridConfig data class**
  - Location: `engine/ecs/systems/GridLines.kt` (new file or same)
  - Properties:
    - `gridSize: Float` (total extent in each direction)
    - `majorStep: Float` (spacing for major lines)
    - `minorStep: Float` (spacing for minor lines)
    - `showXZPlane: Boolean` (ground grid)
    - `showXYPlane: Boolean` (side grid, X-axis view)
    - `showYZPlane: Boolean` (side grid, Z-axis view)
    - `lodDistances: List<Float>` (LOD thresholds)
    - `axisColors: Map<Axis, Vector3f>` (color configuration)
  - Default values matching current behavior for X-Z plane

- [ ] **A34.0.4: Refactor GridLines to render 3D grid**
  - Location: `engine/ecs/systems/GridLines.kt`
  - Implement X-Z plane rendering (existing, with improvements)
  - Implement X-Y plane rendering (vertical grid along X-axis)
  - Implement Y-Z plane rendering (vertical grid along Z-axis)
  - Add proper line depth sorting to avoid Z-fighting
  - Ensure lines snap to major steps for visual consistency
  - Optimize line generation to reduce per-frame allocations

- [ ] **A34.0.5: Implement camera-based LOD system**
  - Calculate camera distance from grid center
  - Adjust grid density based on distance thresholds
  - Smooth transitions between LOD levels
  - Prevent popping artifacts during LOD changes
  - Test with various camera distances (close-up to far view)

- [ ] **A34.0.6: Add ImGui configuration panel**
  - Location: `GridLines.imgui()` method (new)
  - Grid visibility toggles (per plane)
  - Grid density sliders (major/minor step)
  - Grid size slider (total extent)
  - Color pickers for axis colors
  - LOD configuration
  - Reset to defaults button
  - Integrate with SystemsWindow registry

- [ ] **A34.0.7: Improve origin axes rendering**
  - Replace hardcoded 100-unit axes with dynamic length
  - Scale axes based on camera distance
  - Add axis labels (X, Y, Z) using debug text rendering
  - Ensure axes are always visible regardless of grid visibility
  - Add arrow heads or other indicators for axis direction

- [ ] **A34.0.8: Add grid center indicator**
  - Render a visual marker at world origin (0, 0, 0)
  - Optional: Render marker at grid center (snapped position)
  - Use distinct color or pattern to differentiate from grid lines
  - Consider 3D crosshair or sphere marker

- [ ] **A34.0.9: Optimize rendering performance**
  - Profile line rendering cost with full 3D grid
  - Implement line batching where possible
  - Reduce object allocations in `editorUpdate()` loop
  - Consider frustum culling for grid lines

- [ ] **A34.0.10: Update LevelEditorSceneInitializer**
  - Location: `editor/LevelEditorSceneInitializer.kt`
  - Pass GridConfig to GridLines constructor
  - Configure default grid settings for level editor
  - Ensure grid is visible in editor scene by default

- [ ] **A34.0.11: Add unit tests for grid calculations**
  - Location: `src/test/kotlin/com/pafoid/skate/engine/ecs/systems/`
  - Test major/minor line detection logic
  - Test LOD distance calculations
  - Test grid snapping mathematics
  - Test plane visibility combinations
  - Verify no Z-fighting at plane intersections

---

## 🔴 v0.35: 3D Grid Polish & Features (Planned)

### Summary

Additional features and polish for the 3D grid system after core refactoring is complete.

### Tasks

- [ ] **A35.0.1: Add grid snapping visualization**
  - Highlight grid lines when objects are being placed
  - Show snap point under cursor
  - Different colors for different snap targets

- [ ] **A35.0.2: Implement infinite grid illusion**
  - Fade grid lines at edges based on distance
  - Create seamless infinite grid effect
  - Smooth grid center transitions as camera moves

- [ ] **A35.0.3: Add perspective grid lines**
  - Optional perspective guide lines converging at vanishing point
  - Helpful for level design and camera positioning

- [ ] **A35.0.4: Grid presets system**
  - Save/load grid configurations
  - Preset for "ground only", "full 3D", "minimal", etc.
  - Quick-switch between presets in ImGui

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

- Renders grid lines only on X-Z plane (Y = -0.001f)
- Grid follows camera but snaps to major step intervals
- Major lines: 1.0f spacing, dark gray (0.3, 0.3, 0.3)
- Minor lines: 0.1f spacing, darker gray (0.15, 0.15, 0.15)
- Grid extent: 40 lines in each direction (±4.0f from camera)
- Hardcoded origin axes: 100 units, red (X) and green (Z)

**Key Code Sections:**

```kotlin
// Current rendering is 2D-only
val centerX = (floor(camPos.x / majorStep) * majorStep)
val centerZ = (floor(camPos.z / majorStep) * majorStep)
// Y is hardcoded to -0.001f for all lines
```

**Required Changes:**

1. Add Y-axis grid center calculation
2. Render 3 sets of grid planes (X-Y, Y-Z, X-Z)
3. Implement proper depth handling to avoid Z-fighting
4. Make grid extent and density configurable
5. Add camera distance-based LOD

### Coordinate System Reference

- **X-axis**: Right/Left (Red)
- **Y-axis**: Up/Down (Green) - *Note: Currently origin uses green for Z, verify convention*
- **Z-axis**: Forward/Back (Blue) - *Note: Currently origin uses green for Z, verify convention*

**Planes to Render:**

- **X-Z Plane** (Y = 0): Ground plane - horizontal grid
- **X-Y Plane** (Z = 0): Side plane along X-axis - vertical grid
- **Y-Z Plane** (X = 0): Side plane along Z-axis - vertical grid

---

## Historical Summary

**All v0.28, v0.29, v0.30, and v0.31 items are COMPLETE** ✅

See CHANGELOG.md for detailed implementation notes.

**Upcoming:**
- **v0.32**: ImGui Refactor Cleanup (remove EnvironmentWindow duplication)
- **v0.33**: Code Quality & Technical Debt (!! operators, resource management, animation, performance)
- **v0.34**: 3D Grid System Refactoring (new - this document)
- **v0.35**: 3D Grid Polish & Features (new - this document)
