# 🛹 SkateSim Engine - TODO & Roadmap

## Current Focus: ECS Architecture Improvements

This document tracks upcoming development priorities and technical debt for the SkateSim skateboarding simulation
engine.

---

## 🔴 v0.36: ECS Environment System (Planned)

### Summary

Refactor environment settings (sky, fog, atmosphere) from hardcoded `SceneData` properties into a proper ECS system.
This follows the established pattern from `DayNightCycleSystem` and `DirectionalLightSystem` for cleaner architecture
and better separation of concerns.

### Problem Statement

**Current Issues:**

- Environment data (skyColor, fogColor, fogDensity, fogGradient) stored in `SceneData` but not managed by any system
- `EnvironmentWindow` directly manipulates `SceneData` properties - no encapsulation
- Hardcoded values in `LevelEditorSceneInitializer.kt` (lines 71-74)
- Scattered environment logic across rendering, scene initialization, and UI
- No ECS system owns environment state - violates single responsibility principle

**Files with hardcoded environment code:**

- `LevelEditorSceneInitializer.kt`: Sets initial sky/fog values
- `SceneData.kt`: Contains environment properties without system management
- `EnvironmentWindow.kt`: Directly reads/writes `scene.sceneData` properties
- `LightingUniformsLoader.kt`: Reads from `sceneData` for shader uniforms
- `SkyDomeRenderer.kt`: Reads from `scene.sceneData` for rendering
- `GeometryPass.kt`: Uses `scene.sceneData.skyColor` for clear color

### Proposed Solution: EnvironmentSystem ECS

**Create new `EnvironmentSystem` that:**

1. Owns all environment state via `EnvironmentConfig` data class
2. Provides ImGui interface for real-time editing
3. Replaces `SceneData` environment properties
4. Integrates with existing renderers via system reference
5. Follows pattern from `DayNightCycleSystem` and `DirectionalLightSystem`

### Tasks

- [x] **A36.0.1: Create EnvironmentConfig data class** ✅
  - Location: `engine/ecs/config/EnvironmentConfig.kt` (new)
  - Properties: skyColor, skyTint, skyExposure, skyRotation, fogColor, fogDensity, fogGradient
  - Includes `reset()` and `applyPreset()` methods with 5 presets
  - **Status**: Complete - 170 lines, 12 unit tests passing ✅

- [x] **A36.0.2: Create EnvironmentSystem ECS** ✅
  - Location: `engine/ecs/systems/EnvironmentSystem.kt` (new)
  - Extends `System` with `ExecutionPriority.EARLY`
  - Full ImGui interface with presets and configuration sections
  - **Status**: Complete - 180 lines, 11 unit tests passing ✅

- [x] **A36.0.3: Remove environment properties from SceneData** ✅
  - Location: `engine/ecs/scene/SceneData.kt`
  - Removed: skyColor, skyTint, skyExposure, skyRotation, fogColor, fogDensity, fogGradient
  - SceneData now only contains core scene data (timeOfDay, ambientLight, gravity, etc.)
  - **Status**: Complete - Clean separation of concerns ✅

- [x] **A36.0.4: Update EnvironmentWindow to use EnvironmentSystem** ✅
  - Location: `editor/windows/EnvironmentWindow.kt`
  - Gets EnvironmentSystem from scene.systemManager
  - Delegates sky/fog controls to `system.imgui()`
  - **Status**: Complete - Proper ECS integration ✅

- [x] **A36.0.5: Update LevelEditorSceneInitializer** ✅
  - Location: `editor/LevelEditorSceneInitializer.kt`
  - Removed hardcoded environment setup
  - Adds EnvironmentSystem to scene
  - **Status**: Complete ✅

- [x] **A36.0.6: Update LightingUniformsLoader** ✅
  - Location: `engine/render/renderer/LightingUniformsLoader.kt`
  - Added `environmentConfig` parameter to `loadLightingUniforms()`
  - Reads fog settings from EnvironmentConfig with defaults fallback
  - **Status**: Complete ✅

- [x] **A36.0.7: Update SkyDomeRenderer** ✅
  - Location: `engine/render/renderer/SkyDomeRenderer.kt`
  - Gets EnvironmentSystem from scene
  - Reads sky/fog settings from `system.config`
  - **Status**: Complete ✅

- [x] **A36.0.8: Update GeometryPass** ✅
  - Location: `engine/render/renderer/passes/GeometryPass.kt`
  - Reads clear color from EnvironmentSystem.config
  - Passes environmentConfig to LightingUniformsLoader
  - **Status**: Complete ✅

- [x] **A36.0.9: Add string resources for EnvironmentSystem ImGui** ✅
  - Location: `values/strings.properties`, `values/strings_fr.properties`
  - 17 new keys for ImGui controls (English + French)
  - **Status**: Complete ✅

- [x] **A36.0.10: Add unit tests for EnvironmentSystem** ✅
    - Location: `test/.../ecs/systems/EnvironmentSystemTest.kt`
    - 11 unit tests covering initialization, presets, reset, and config access
    - **Status**: Complete - 23/23 tests passing ✅

- [x] **A36.0.11: Update documentation** ✅
  - Location: `CHANGELOG.md`, `TODO.md`
  - Document v0.36 changes
  - Mark all A36 tasks complete
  - **Status**: Complete ✅

---

## 🔵 Future: Code Quality & Technical Debt (Planned)

### v0.33: Code Quality & Technical Debt

- [ ] Audit and replace remaining `!!` operators with safe calls
- [ ] Review resource management for potential memory leaks
- [ ] Optimize animation blending timing
- [ ] Reduce object allocation in hot loops
- [ ] Increase test coverage for complex systems

### v0.32: ImGui Refactor Cleanup

- [ ] Remove `EnvironmentWindow` duplication (moved to EnvironmentSystem)
- [ ] Consolidate system UI patterns
- [ ] Review dockable window registry for dead code

---

## ✅ Completed Versions

### v0.36: ECS Environment System (Complete) ✅

- **A36.0.1**: EnvironmentConfig data class with sky/fog settings and 5 presets ✅
- **A36.0.2**: EnvironmentSystem ECS with EARLY priority and ImGui interface ✅
- **A36.0.3**: Removed deprecated environment properties from SceneData ✅
- **A36.0.4**: EnvironmentWindow now uses EnvironmentSystem ✅
- **A36.0.5**: LevelEditorSceneInitializer adds EnvironmentSystem ✅
- **A36.0.6**: LightingUniformsLoader updated for EnvironmentConfig ✅
- **A36.0.7**: SkyDomeRenderer reads from EnvironmentSystem ✅
- **A36.0.8**: GeometryPass reads skyColor from EnvironmentSystem ✅
- **A36.0.9**: String resources for ImGui (17 keys, English/French) ✅
- **A36.0.10**: Unit tests (23 tests passing) ✅
- **A36.0.11**: Documentation updated ✅

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

## Historical Notes

### Grid System Evolution (v0.34-v0.35)

**v0.34** established the Godot-style grid foundation:

- Dynamic extent based on camera distance
- LOD system with smoothstep transitions
- Origin axes with correct RGB=XYZ colors
- Full ImGui configuration panel
- 20+ unit tests

**v0.35** added advanced polish features:

- Center marker crosshair at world origin
- Edge fading for smoother infinite grid illusion
- Secondary grid plane for multi-level design
- Snap visualization for object placement
- 6 additional unit tests (26 total)

**Total grid system code:**

- GridLines.kt: ~560 lines (rendering + ImGui)
- GridConfig.kt: ~70 lines (embedded in GridLines.kt)
- GridLinesTest.kt: ~520 lines (26 tests)
- String resources: 31 keys (English + French)

---

## Technical Reference

### Godot Grid Reference

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

**Coordinate System:**
- **X-axis**: Red (right/left)
- **Y-axis**: Green (up/down)
- **Z-axis**: Blue (forward/back)

---

## End of TODO
