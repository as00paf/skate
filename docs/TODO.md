# 🛹 SkateSim Engine - TODO & Roadmap

## Current Focus: Incremental ECS Migration

The current architecture is a hybrid pattern mixing ECS with Service Locator. This document outlines a pragmatic
incremental migration to proper ECS architecture where systems operate on components rather than owning config directly.

See [CHANGELOG.md](CHANGELOG.md) for complete history of completed versions.

---

## 🔴 v0.38: ECS Architecture Foundation (Planned)

### Summary

Establish proper ECS foundation by making Scene a GameObject and creating component-based environment system. This is
the first step in incremental ECS migration.

### Architectural Issues (Current State)

**Hybrid Pattern Problems:**

- Systems own config directly instead of operating on components (`EnvironmentSystem.config`,
  `DayNightCycleSystem.config`)
- Systems communicate via Service Locator (`scene.systemManager.getSystem<T>()`)
- `SceneData` is global singleton anti-pattern
- Scene itself is not a GameObject (can't have components)

**What Proper ECS Looks Like:**

- Components = Pure data (no logic)
- Systems = Logic that operates on components they iterate
- No direct system-to-system communication
- Scene is a GameObject with components

### Tasks

- [ ] **A38.0.1: Make Scene extend GameObject**
  - Location: `engine/ecs/Scene.kt`
  - Scene becomes a GameObject that can have components
  - Migrate `camera: Camera` to `CameraComponent` (or keep as special case with getter)
  - Migrate `sceneData` properties to appropriate components
  - Update `init()`, `start()`, `update()`, `editorUpdate()`, `destroy()` to call super
  - **Impact**: Critical - Foundation for component-based architecture

- [ ] **A38.0.2: Create EnvironmentComponent**
  - Location: `engine/ecs/components/EnvironmentComponent.kt` (new)
  - Properties: skyColor, skyTint, skyExposure, skyRotation, fogColor, fogDensity, fogGradient, renderSky, renderFog
  - Pure data component (no logic)
  - Serializable for level saves
  - **Impact**: High - Environment data as component

- [ ] **A38.0.3: Create TimeComponent (for timeOfDay, timeScale)**
  - Location: `engine/ecs/components/TimeComponent.kt` (new)
  - Properties: timeOfDay, timeScale
  - Used by DayNightCycleSystem and Scene.update()
  - **Impact**: High - Time state as component

- [ ] **A38.0.4: Create LightingStateComponent (for ambientLight, useAmbient)**
  - Location: `engine/ecs/components/LightingStateComponent.kt` (new)
  - Properties: ambientLight, useAmbient
  - Written by DayNightCycleSystem, read by LightingUniformsLoader
  - **Impact**: High - Lighting state as component

- [ ] **A38.0.5: Refactor EnvironmentSystem to iterate components**
  - Location: `engine/ecs/systems/EnvironmentSystem.kt`
  - Remove direct config ownership
  - Iterate GameObjects with EnvironmentComponent (including Scene)
  - Update method processes environment components
  - **Impact**: High - Proper ECS pattern

- [ ] **A38.0.6: Update DayNightCycleSystem to write to components**
  - Location: `engine/ecs/systems/DayNightCycleSystem.kt`
  - Keep config for cycle parameters (cycleTime, dayDuration)
  - Write ambientColor to LightingStateComponent on Scene
  - Read timeOfDay from TimeComponent on Scene
  - **Impact**: High - Proper ECS data flow

- [ ] **A38.0.7: Update renderers to read from components**
  - Location: `SkyDomeRenderer.kt`, `LightingUniformsLoader.kt`, `GeometryPass.kt`
  - Get EnvironmentComponent from Scene GameObject
  - Get TimeComponent from Scene for timeOfDay
  - Get LightingStateComponent for ambientLight
  - Remove `scene.systemManager.getSystem<EnvironmentSystem>()` calls
  - **Impact**: High - Decouple rendering from system classes

- [ ] **A38.0.8: Update LevelEditorSceneInitializer**
  - Location: `editor/LevelEditorSceneInitializer.kt`
  - Add EnvironmentComponent to Scene (instead of EnvironmentSystem config)
  - Add TimeComponent to Scene with initial timeOfDay
  - Add LightingStateComponent to Scene
  - Remove `scene.sceneData.*` references for environment properties
  - **Impact**: High - Scene initialization uses components

- [ ] **A38.0.9: Update EnvironmentWindow**
  - Location: `editor/windows/EnvironmentWindow.kt`
  - Read/write EnvironmentComponent on Scene
  - Read/write TimeComponent for timeOfDay
  - Read/write LightingStateComponent for ambientLight
  - Keep DayNightCycleSystem integration for cycle time
  - **Impact**: Medium - Editor UI uses components

- [ ] **A38.0.10: Update LevelManager and LevelData**
  - Location: `game/level/LevelManager.kt`, `game/level/LevelData.kt`
  - LevelData.sceneData no longer contains environment properties
  - Serialize components on Scene GameObject instead
  - Update saveToFile() and loadFromFile() to handle component-based scene data
  - **Impact**: Critical - Level serialization must work with new structure

- [ ] **A38.0.11: Update SceneManager**
  - Location: `engine/ecs/SceneManager.kt`
  - Ensure scene change properly handles new component structure
  - May need to update changeScene() to handle component initialization
  - **Impact**: Medium - Scene management compatibility

- [ ] **A38.0.12: Update GameViewWindow**
  - Location: `editor/windows/GameViewWindow.kt`
  - Update `scene.sceneData.timeScale` reference to use TimeComponent
  - **Impact**: Low - Fix timeScale access

- [ ] **A38.0.13: Update SkyDomeRenderer timeOfDay access**
  - Location: `engine/render/renderer/SkyDomeRenderer.kt`
  - Change `scene.sceneData.timeOfDay` to read from TimeComponent
  - **Impact**: Low - Fix timeOfDay access

- [ ] **A38.0.14: Create LightingComponent (prepare for DayNightCycle refactor)**
  - Location: `engine/ecs/components/LightingComponent.kt` (new)
  - Properties: sunDirection, sunColor, sunIntensity, shadowIntensity, isDaytime
  - Pure data component for computed lighting state
  - **Impact**: Medium - Prepare for DayNightCycleSystem refactor

- [ ] **A38.0.15: Add unit tests for ECS foundation**
  - Location: `test/.../ecs/components/`, `test/.../ecs/systems/`
  - Test Scene as GameObject
  - Test EnvironmentComponent, TimeComponent, LightingStateComponent creation
  - Test component serialization/deserialization
  - Test EnvironmentSystem iterates components correctly
  - Test level save/load with new component structure
  - **Impact**: High - Ensure ECS foundation is solid, no regressions

---

## 🔴 v0.39: ECS Architecture Expansion (Planned)

### Summary

Continue ECS migration by refactoring remaining systems to operate on components.

### Tasks

- [ ] **A39.0.1: Create InputComponent**
  - Location: `engine/ecs/components/InputComponent.kt` (new)
  - Properties: moveVector, lookVector, jumpPressed, actionPressed, etc.
  - InputSystem writes to InputComponent
  - **Impact**: High - Input as component

- [ ] **A39.0.2: Refactor InputSystem to write to InputComponent**
  - Location: `engine/ecs/systems/InputSystem.kt`
  - Iterate GameObjects, poll input, write to InputComponent
  - Remove direct component access, use component iteration
  - **Impact**: Medium - Proper ECS input pattern

- [ ] **A39.0.3: Create PhysicsComponent (consolidate rigid body state)**
  - Location: `engine/ecs/components/PhysicsComponent.kt` (new)
  - Properties: velocity, angularVelocity, mass, friction, etc.
  - **Impact**: Medium - Physics state as component

- [ ] **A39.0.4: Document ECS architecture patterns**
  - Location: `docs/ECS_ARCHITECTURE.md` (new)
  - Explain hybrid pattern and migration strategy
  - Document when to use components vs system config
  - Code examples for proper ECS patterns
  - **Impact**: Medium - Architecture documentation

---

## Architecture Notes

### Current Hybrid Pattern

The current codebase uses a hybrid of:

1. **ECS pattern** - AnimationSystem, InputSystem iterate GameObjects and update components ✅
2. **Service Locator pattern** - Systems accessed via `scene.systemManager.getSystem<T>()`
3. **Singleton pattern** - SceneData holds global state

This works but creates coupling between systems and renderers.

### Target ECS Pattern

```
Scene (GameObject)
├── EnvironmentComponent (sky/fog settings)
├── LightingComponent (sun direction/color, ambient)
├── CameraComponent (view/projection matrices)
└── GameObject[]
    ├── TransformComponent
    ├── RenderComponent
    ├── InputComponent
    └── PhysicsComponent

Systems iterate components:
- EnvironmentSystem → reads/writes EnvironmentComponent
- DayNightCycleSystem → writes LightingComponent
- InputSystem → writes InputComponent
- AnimationSystem → reads Animator, SkeletonComponent ✅ (already correct)
- GeometryPass → reads EnvironmentComponent, LightingComponent (no system lookup)
```

### Migration Strategy

1. **Phase 1 (v0.38)**: Scene as GameObject + EnvironmentComponent
2. **Phase 2 (v0.39)**: InputComponent + LightingComponent expansion
3. **Phase 3 (future)**: PhysicsComponent + remaining systems

Each phase is independent and can be tested incrementally.
