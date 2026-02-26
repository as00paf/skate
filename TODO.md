# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for all completed items through v0.30
- This file contains planned improvements and technical debt

---

## 🔴 v0.31: Systems ImGui Integration (Planned)

### Summary

The current ImGui architecture has a gap: `System.imgui()` methods exist but are never called by `ImGuiLayer`. This
creates inconsistent UI patterns where Components have auto-generated UI but Systems require manual window classes.

### Problem Statement

1. **Unused System.imgui()**: The base `System` class defines `open fun imgui()` but `ImGuiLayer` never calls it
2. **Scattered Windows**: System controls are split across multiple windows (EnvironmentWindow, PhysicsTunerWindow,
   ProfilerWindow)
3. **Inconsistent Pattern**: `Component.imgui()` uses reflection for auto-UI, but Systems need manual window boilerplate
4. **Discovery Issues**: No centralized way to see all system controls in one place

### Tasks

- [x] **A31.0.1: Create SystemsWindow.kt**
  - Location: `editor/windows/SystemsWindow.kt`
  - Single dockable window that lists all systems from current scene
  - Uses collapsing headers for each system
  - Calls each system's `imgui()` method inside its header
  - Auto-discovers systems via `SystemManager.systems`
  - Visual indication for `system.enabled` flag
  - Context menu to toggle system enabled state
  - Integrated into ImGuiLayer with menu item and visibility flag
  - String resources added for English and French

- [x] **A31.0.2: Add System UI Metadata**
  - Location: `engine/ecs/systems/System.kt`
  - Added `open val displayName: String` for friendly names
  - Defaults to `javaClass.simpleName` but can be overridden for custom display
  - Used by SystemsWindow to display system names in headers

- [x] **A31.0.3: Update ImGuiLayer Integration**
  - Location: `editor/imgui/ImGuiLayer.kt`
  - Add `private val systemsWindow = SystemsWindow()` instance
  - Add `showSystems: ImBoolean = ImBoolean(false)` visibility flag
  - Add menu item: View → Windows → Systems
  - Call `systemsWindow.imgui(currentScene)` in update loop
  - Consider removing hardcoded system window calls where applicable

- [x] **A31.0.4: Implement DayNightCycleSystem.imgui()**
  - Location: `engine/ecs/systems/DayNightCycleSystem.kt`
  - Time of day slider (0-24 hours)
  - Day duration slider (60-600 seconds)
  - Auto-ambient toggle
  - Current phase display (Night/Dawn/Day/Dusk)
  - Sun direction vector display (read-only)
  - Sun color/intensity display (read-only)
  - Ambient color/intensity display (read-only)
  - Shadow intensity display (read-only)

- [ ] **A31.0.5: Implement InputSystem.imgui()**
  - Location: `engine/ecs/systems/InputSystem.kt`
  - Input state debugging (current axis values, button states)
  - Sensitivity/deadzone controls from InputSettings
  - Binding viewer (keyboard/gamepad mapping display)

- [ ] **A31.0.6: Implement AnimationSystem.imgui()**
  - Location: `engine/ecs/systems/AnimationSystem.kt`
  - Animated object count display
  - Per-object animation state (current animation, time, playing state)
  - Animation speed multiplier
  - Cache statistics

- [ ] **A31.0.7: Implement GizmoSystem.imgui()**
  - Location: `engine/ecs/systems/GizmoSystem.kt`
  - Current active gizmo display
  - Gizmo size/scale multipliers
  - Snapping settings (grid snap, rotation snap, scale snap)
  - Tool key binding display/

- [ ] **A31.0.8: Refactor EnvironmentWindow**
  - Location: `editor/windows/EnvironmentWindow.kt`
  - Remove day/night cycle controls (moved to DayNightCycleSystem.imgui)
  - Remove directional light controls (moved to DirectionalLightSystem.imgui)
  - Keep scene-level settings: sky color, fog settings, time scale
  - Add links/buttons to open Systems window for system-specific controls

### Architecture Benefits

- **Centralized**: All system controls discoverable in one place
- **Auto-Discovery**: New systems with `imgui()` automatically appear
- **Consistent**: Follows Component pattern with collapsing headers
- **Cleaner Separation**: Systems own their UI logic
- **Reduced Bloat**: Less hardcoded window management in ImGuiLayer

---

## 🔴 v0.32: ImGui Refactor Cleanup (Planned)

### Summary

The v0.31 Systems ImGui refactor will create temporary code duplication as system controls are moved from dedicated
windows (EnvironmentWindow, PhysicsTunerWindow) into system classes. This version tracks the cleanup work.

### Tasks

- [ ] **A32.0.1: Remove duplicate DayNightCycle controls from EnvironmentWindow**
  - Location: `editor/windows/EnvironmentWindow.kt`
  - Remove time slider (now in DayNightCycleSystem.imgui)
  - Remove day duration controls (now in DayNightCycleSystem.imgui)
  - Keep only sceneData.timeOfDay sync for save/load compatibility
  - Add "Open Systems Window" button for full system controls

- [ ] **A32.0.2: Remove duplicate DirectionalLight controls from EnvironmentWindow**
  - Location: `editor/windows/EnvironmentWindow.kt`
  - Remove shadow distance slider (now in DirectionalLightSystem.imgui)
  - Remove ortho bounds controls (now in DirectionalLightSystem.imgui)
  - Remove shadow quality toggles (stabilize, bias - now in DirectionalLightSystem.imgui)
  - Keep only scene-level light overrides if needed

- [ ] **A32.0.3: Audit PhysicsTunerWindow for system duplication**
  - Location: `editor/windows/PhysicsTunerWindow.kt`
  - Identify which controls access system data vs component data
  - Gravity controls: Keep (global physics setting)
  - PlayerController tuning: Consider moving to PlayerController.imgui() on component
  - SkateboardPhysics tuning: Keep (component-level, not system)

- [ ] **A32.0.4: Update ImGuiLayer to remove redundant window instantiations**
  - Location: `editor/imgui/ImGuiLayer.kt`
  - Remove `showEnvironment`, `showPhysicsTuner` flags if windows are deprecated
  - Remove direct calls to deprecated window imgui methods
  - Keep only essential windows: GameView, Hierarchy, Properties, AssetBrowser, Console, Systems

- [ ] **A32.0.5: Update documentation and tooltips**
  - Location: All modified window files
  - Update KDoc to reflect new window purposes
  - Add @Deprecated tags to removed functionality
  - Update stringManager keys for any renamed UI elements

- [ ] **A32.0.6: Test all ImGui windows for functionality**
  - Verify all system controls work from SystemsWindow
  - Verify EnvironmentWindow still functions for scene-level settings
  - Check that save/load still works with new UI structure
  - Test docking layout persistence

---

## 🔴 v0.33: Code Quality & Technical Debt (Planned)

### Summary

Ongoing code quality improvements and technical debt reduction.

### Tasks

- [ ] **A33.0.1: Audit remaining !! operators**
  - Search codebase for `!!` usage
  - Replace with safe calls (`?.`) and Elvis operator (`?:`)
  - Add proper null handling or validation

- [ ] **A33.0.2: Review resource management**
  - Audit asset loading/unloading for memory leaks
  - Ensure proper cleanup in `destroy()` methods
  - Add resource tracking/debugging tools

- [ ] **A33.0.3: Animation blending timing**
  - Investigate crossfade transition timing issues
  - Verify blend duration calculations
  - Add blending debug visualization

- [ ] **A33.0.4: Object allocation in hot loops**
  - Profile physics and rendering loops
  - Reduce garbage collection pressure
  - Use object pooling where appropriate

---

## ✅ Completed Versions

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

## Historical Summary

**All v0.28, v0.29, and v0.30 items are COMPLETE** ✅

See CHANGELOG.md for detailed implementation notes.

**Upcoming:**

- **v0.31**: Systems ImGui Integration (centralized system UI)
- **v0.32**: ImGui Refactor Cleanup (remove code duplication)
- **v0.33**: Code Quality & Technical Debt (!! operators, resource management, animation, performance)
