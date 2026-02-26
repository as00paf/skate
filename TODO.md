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

- [x] **A31.0.5: Implement InputSystem.imgui()**
  - Location: `engine/ecs/systems/InputSystem.kt`
  - Deadzone settings (left stick, right stick)
  - Trigger threshold
  - Sensitivity settings (mouse, controller)
  - Movement thresholds (movement, sprint)
  - Physics settings (jump impulse, walk/run speed, rotation speed, take off time, input smoothing)
  - Input state debug (gamepad axes and buttons display)

- [x] **A31.0.6: Implement AnimationSystem.imgui()**
  - Location: `engine/ecs/systems/AnimationSystem.kt`
  - Animated object count display
  - Cache dirty status
  - Global speed multiplier (0-3x)
  - Per-object animation state:
    - Current animation name
    - Current time / duration
    - Playing state
    - Blend time remaining
  - Empty state message when no animated objects

- [x] **A31.0.7: Replace hardcoded strings with StringManager**
  - Location: All system imgui() implementations
  - DayNightCycleSystem: Constructor injection for StringManager, replaced all hardcoded strings
  - InputSystem: Constructor injection for StringManager, replaced all hardcoded strings
  - AnimationSystem: Constructor injection for StringManager, replaced all hardcoded strings
  - DirectionalLightSystem: Constructor injection for StringManager, replaced all hardcoded strings
  - Added 70+ new string keys to strings.properties and strings_fr.properties
  - Updated LevelEditorSceneInitializer to pass StringManager to system constructors
  - Updated KoinModule.kt for InputSystem constructor injection
  - All UI text now properly localized without KoinComponent usage

- [x] **A31.0.8: Refactor ImGuiLayer for cleaner window management**
  - Location: `editor/imgui/ImGuiLayer.kt`
  - Created `IWindow` and `IWindowWithScene` interfaces for type-safe window handling
  - Created `EditorWindow` data class in dedicated file for window metadata
  - Created `editorWindows` registry list for centralized window management
  - Refactored window rendering loop (replaced 10 if statements with forEach)
  - Refactored View menu (replaced 10 checkbox calls with forEach)
  - Refactored dock builder (dynamic window docking based on registry)
  - Extracted `EditorMenuBar` class for menu bar logic (~150 lines moved)
  - All 10 dockable windows updated to implement appropriate interface:
    - IWindow: PropertiesWindow, GameViewWindow, AssetBrowserWindow, ProfilerWindow, ConsoleWindow
    - IWindowWithScene: SceneHierarchyWindow, EnvironmentWindow, PhysicsTunerWindow, InputTestingWindow, SystemsWindow
  - Added `getHoveredGameObject()` public method for Engine access
  - Reduced ImGuiLayer by ~200 lines through eliminated duplication
  - SystemsWindow default visibility changed to true for better discoverability

- [ ] **A31.0.9: Refactor EnvironmentWindow**
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
- **Type-Safe**: Window interfaces enforce correct imgui() signatures
- **Maintainable**: Single registry for all window management

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

- [ ] **A32.0.4: Update ImGuiLayer window registry**
  - Location: `editor/imgui/ImGuiLayer.kt`
  - Window registry already complete (A31.0.8)
  - Consider removing EnvironmentWindow and PhysicsTunerWindow from default dock layout
  - Since they will be deprecated in favor of SystemsWindow
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

### v0.31: Systems ImGui Integration (Complete) ✅

- SystemsWindow created for centralized system UI
- System UI metadata (displayName) added to base System class
- DayNightCycleSystem.imgui() implemented with full controls
- InputSystem.imgui() implemented with deadzones, sensitivity, and debug
- AnimationSystem.imgui() implemented with per-object state display
- DirectionalLightSystem.imgui() implemented with shadow quality controls
- All hardcoded strings replaced with StringManager localization
- ImGuiLayer refactored with EditorWindow registry pattern
- IWindow and IWindowWithScene interfaces created for type safety
- All 10 dockable windows updated to implement interfaces
- ~50 lines of code reduction through eliminated duplication

**Upcoming:**

- **v0.32**: ImGui Refactor Cleanup (remove EnvironmentWindow duplication)
- **v0.33**: Code Quality & Technical Debt (!! operators, resource management, animation, performance)
