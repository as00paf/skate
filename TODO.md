# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for all completed items through v0.31
- This file contains planned improvements and technical debt

---

## 🔴 v0.32: ImGui Refactor Cleanup (Planned)

### Summary

The v0.32 Systems ImGui refactor created some temporary code duplication as system controls were moved from dedicated
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

## Historical Summary

**All v0.28, v0.29, v0.30, and v0.31 items are COMPLETE** ✅

See CHANGELOG.md for detailed implementation notes.

**Upcoming:**

- **v0.32**: ImGui Refactor Cleanup (remove EnvironmentWindow duplication)
- **v0.33**: Code Quality & Technical Debt (!! operators, resource management, animation, performance)
