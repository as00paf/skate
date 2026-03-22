# 🛹 SkateSim Engine - TODO & Roadmap

## Current Focus: ECS Architecture Expansion

The ECS foundation is now in place with Scene as a GameObject supporting components.
Remaining work focuses on completing the hybrid pattern migration and expanding component-based architecture.

See [CHANGELOG.md](CHANGELOG.md) for complete history of completed versions.

---

## 🔴 v0.38: ECS Architecture Foundation (Complete) ✅

### Summary

Established proper ECS foundation by making Scene a GameObject and creating component-based architecture for
environment, time, and lighting state.

### Completed Tasks

- [x] **A38.0.1: Make Scene extend GameObject** ✅
  - Scene now extends GameObject with component support
  - Added helper methods: getTimeScale(), setTimeScale(), getTimeOfDay(), setTimeOfDay()
  - New lifecycle methods: startScene(), destroyScene(), editorUpdateScene(), updateScene(), imguiScene()
  - **Status**: Complete - Foundation for component-based architecture ✅

- [x] **A38.0.2: Create EnvironmentComponent** ✅
  - Location: `engine/ecs/components/EnvironmentComponent.kt`
  - Properties: skyColor, skyTint, skyExposure, skyRotation, fogColor, fogDensity, fogGradient, renderSky, renderFog
  - Includes applyPreset() with 5 presets and reset() method
  - **Status**: Complete - Environment data as component ✅

- [x] **A38.0.3: Create TimeComponent** ✅
  - Location: `engine/ecs/components/TimeComponent.kt`
  - Properties: timeOfDay, timeScale
  - Helper method: getFormattedTime()
  - **Status**: Complete - Time state as component ✅

- [x] **A38.0.4: Create LightingStateComponent** ✅
  - Location: `engine/ecs/components/LightingStateComponent.kt`
  - Properties: ambientLight, useAmbient
  - **Status**: Complete - Lighting state as component ✅

- [x] **A38.0.6: Update DayNightCycleSystem to write to components** ✅
  - updateSceneAmbient() now writes to LightingStateComponent
  - Reads timeOfDay from TimeComponent via Scene helper
  - **Status**: Complete - Proper ECS data flow ✅

- [x] **A38.0.7: Update renderers to read from components** ✅
  - SkyDomeRenderer reads TimeComponent for timeOfDay
  - LightingUniformsLoader reads LightingStateComponent for ambientLight
  - GeometryPass passes LightingStateComponent to loader
  - **Status**: Complete - Decoupled rendering from system classes ✅

- [x] **A38.0.8: Update LevelEditorSceneInitializer** ✅
  - Adds EnvironmentComponent, TimeComponent, LightingStateComponent to Scene
  - Removed sceneData.timeOfDay reference
  - **Status**: Complete - Scene initialization uses components ✅

- [x] **A38.0.9: Update EnvironmentWindow** ✅
  - Reads/writes TimeComponent for timeOfDay
  - Reads/writes LightingStateComponent for ambientLight
  - **Status**: Complete - Editor UI uses components ✅

- [x] **A38.0.10: Update LevelManager and LevelData** ✅
  - LevelData simplified (removed gravity property)
  - SceneData simplified (removed environment/time/lighting properties)
  - **Status**: Complete - Level serialization updated ✅

- [x] **A38.0.11: Update SceneManager** ✅
  - Updated to use startScene() and destroyScene()
  - **Status**: Complete - Scene management compatibility ✅

- [x] **A38.0.12: Update GameViewWindow** ✅
  - Uses getTimeScale() and setTimeScale() helper methods
  - **Status**: Complete - timeScale access via component ✅

- [x] **A38.0.13: Update SkyDomeRenderer timeOfDay access** ✅
  - Reads from TimeComponent instead of sceneData
  - **Status**: Complete - timeOfDay access via component ✅

- [x] **A38.0.14: Create LightingComponent** ✅
  - Location: `engine/ecs/components/LightingComponent.kt`
  - Properties: sunDirection, sunColor, sunIntensity, shadowIntensity, isDaytime
  - **Status**: Complete - Computed lighting state as component ✅

### Pending Tasks for v0.38

- [ ] **A38.0.5: Refactor EnvironmentSystem to iterate components**
  - Currently EnvironmentSystem still owns config directly
  - Should iterate GameObjects with EnvironmentComponent
  - **Impact**: High - Complete proper ECS pattern

- [ ] **A38.0.15: Add comprehensive unit tests**
  - Test new components (EnvironmentComponent, TimeComponent, LightingStateComponent, LightingComponent)
  - Test Scene as GameObject with components
  - Test component serialization
  - **Impact**: High - Ensure ECS foundation is solid

- [ ] **A38.0.16: Migration guide and documentation**
  - Document what changed from v0.37 to v0.38
  - List all files modified
  - Provide before/after code examples
  - **Impact**: Medium - Help future development

---

## 🔴 v0.39: ECS Architecture Expansion (Planned)

### Summary

Continue ECS migration by refactoring remaining systems to operate on components and completing the hybrid pattern
migration.

### Tasks

- [ ] **A39.0.1: Complete EnvironmentSystem refactor** (carried from v0.38)
  - Remove direct config ownership
  - Iterate GameObjects with EnvironmentComponent
  - Update method processes environment components
  - **Impact**: High - Proper ECS pattern complete

- [ ] **A39.0.2: Create InputComponent**
  - Location: `engine/ecs/components/InputComponent.kt` (new)
  - Properties: moveVector, lookVector, jumpPressed, actionPressed, etc.
  - InputSystem writes to InputComponent
  - **Impact**: High - Input as component

- [ ] **A39.0.3: Refactor InputSystem to write to InputComponent**
  - Location: `engine/ecs/systems/InputSystem.kt`
  - Iterate GameObjects, poll input, write to InputComponent
  - **Impact**: Medium - Proper ECS input pattern

- [ ] **A39.0.4: Create PhysicsComponent**
  - Location: `engine/ecs/components/PhysicsComponent.kt` (new)
  - Properties: velocity, angularVelocity, mass, friction, etc.
  - **Impact**: Medium - Physics state as component

- [ ] **A39.0.5: Document ECS architecture patterns**
  - Location: `docs/ECS_ARCHITECTURE.md` (new)
  - Explain hybrid pattern and migration strategy
  - Document when to use components vs system config
  - Code examples for proper ECS patterns
  - **Impact**: Medium - Architecture documentation

---

## Architecture Notes

### Current Hybrid Pattern (v0.38)

The codebase now uses an improved hybrid pattern:

1. **ECS pattern** - Scene extends GameObject, components store state ✅
2. **Component-based data flow** - Renderers read from components ✅
3. **Systems still own config** - EnvironmentSystem, DayNightCycleSystem own config (to be refactored in v0.39)

### Target ECS Pattern

```
Scene (GameObject)
├── EnvironmentComponent (sky/fog settings) ✅
├── TimeComponent (timeOfDay, timeScale) ✅
├── LightingStateComponent (ambientLight, useAmbient) ✅
├── LightingComponent (computed lighting state) ✅
└── GameObject[]
    ├── TransformComponent
    ├── RenderComponent
    └── PhysicsComponent (via BulletPhysics3D)

Systems:
- EnvironmentSystem → owns config (refactor pending)
- DayNightCycleSystem → writes to LightingStateComponent ✅
- InputSystem → writes to InputStateComponent on GameObjects ✅
- AnimationSystem → reads Animator, SkeletonComponent ✅
```

### Migration Progress

| Phase   | Status         | Description                                                     |
|---------|----------------|-----------------------------------------------------------------|
| Phase 1 | ✅ Complete     | Scene as GameObject, component infrastructure                   |
| Phase 2 | 🔄 In Progress | Environment/Time/Lighting components created, renderers updated |
| Phase 3 | ⏳ Pending      | EnvironmentSystem refactor, InputComponent, PhysicsComponent    |
| Phase 4 | ⏳ Pending      | Documentation and architecture guide                            |

### Files Modified in v0.38

**New Files (4):**

- `engine/ecs/components/EnvironmentComponent.kt`
- `engine/ecs/components/TimeComponent.kt`
- `engine/ecs/components/LightingStateComponent.kt`
- `engine/ecs/components/LightingComponent.kt`

**Modified Files (14):**

- `engine/ecs/Scene.kt` - Now extends GameObject
- `engine/ecs/SceneManager.kt` - Updated lifecycle calls
- `engine/ecs/scene/SceneData.kt` - Simplified (removed environment properties)
- `game/level/LevelData.kt` - Simplified (removed gravity)
- `game/level/LevelManager.kt` - Updated for simplified LevelData
- `editor/LevelEditorSceneInitializer.kt` - Adds components to Scene
- `editor/windows/EnvironmentWindow.kt` - Reads/writes components
- `editor/windows/GameViewWindow.kt` - Uses TimeComponent helpers
- `engine/ecs/systems/DayNightCycleSystem.kt` - Writes to LightingStateComponent
- `engine/render/renderer/SkyDomeRenderer.kt` - Reads TimeComponent
- `engine/render/renderer/LightingUniformsLoader.kt` - Reads LightingStateComponent
- `engine/render/renderer/passes/GeometryPass.kt` - Passes LightingStateComponent
- `engine/core/Engine.kt` - Updated scene update calls
- `editor/imgui/ImGuiLayer.kt` - Updated imgui call

---

## End of TODO
