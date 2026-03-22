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
  - Camera becomes a component on Scene (or keep as special case)
  - SceneData properties migrate to components
  - **Impact**: Critical - Foundation for component-based architecture

- [ ] **A38.0.2: Create EnvironmentComponent**
  - Location: `engine/ecs/components/EnvironmentComponent.kt` (new)
  - Properties: skyColor, skyTint, skyExposure, skyRotation, fogColor, fogDensity, fogGradient, renderSky, renderFog
  - Pure data component (no logic)
  - Serializable for level saves
  - **Impact**: High - Environment data as component

- [ ] **A38.0.3: Refactor EnvironmentSystem to iterate components**
  - Location: `engine/ecs/systems/EnvironmentSystem.kt`
  - Remove direct config ownership
  - Iterate GameObjects with EnvironmentComponent (including Scene)
  - Update method processes environment components
  - **Impact**: High - Proper ECS pattern

- [ ] **A38.0.4: Update renderers to read from EnvironmentComponent**
  - Location: `SkyDomeRenderer.kt`, `LightingUniformsLoader.kt`, `GeometryPass.kt`
  - Get EnvironmentComponent from Scene GameObject
  - Remove `scene.systemManager.getSystem<EnvironmentSystem>()` calls
  - **Impact**: High - Decouple rendering from system classes

- [ ] **A38.0.5: Create LightingComponent (prepare for DayNightCycle refactor)**
  - Location: `engine/ecs/components/LightingComponent.kt` (new)
  - Properties: sunDirection, sunColor, ambientColor, sunIntensity, shadowIntensity, isDaytime
  - Pure data component for lighting state
  - **Impact**: Medium - Prepare for DayNightCycleSystem refactor

- [ ] **A38.0.6: Update DayNightCycleSystem to write to LightingComponent**
  - Location: `engine/ecs/systems/DayNightCycleSystem.kt`
  - Keep config for cycle parameters (cycleTime, dayDuration)
  - Write computed values (sunDirection, sunColor, ambientColor) to LightingComponent
  - **Impact**: Medium - Partial ECS migration for lighting

- [ ] **A38.0.7: Add unit tests for ECS foundation**
  - Location: `test/.../ecs/components/`, `test/.../ecs/systems/`
  - Test Scene as GameObject
  - Test EnvironmentComponent creation and serialization
  - Test EnvironmentSystem iterates components correctly
  - **Impact**: High - Ensure ECS foundation is solid

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

## 🔵 Future: Code Quality & Technical Debt

### v0.33: Code Quality & Technical Debt

- [ ] Audit and replace remaining `!!` operators with safe calls
- [ ] Review resource management for potential memory leaks
- [ ] Optimize animation blending timing
- [ ] Reduce object allocation in hot loops
- [ ] Increase test coverage for complex systems

### v0.32: ImGui Refactor Cleanup

- [ ] Consolidate system UI patterns
- [ ] Review dockable window registry for dead code

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
