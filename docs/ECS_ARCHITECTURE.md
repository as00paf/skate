# SkateSim Engine - ECS Architecture

## Overview

This document describes the ECS (Entity-Component-System) architecture used in SkateSim Engine, the hybrid pattern
employed, and the migration strategy from global state to component-based architecture.

---

## Current Architecture (v0.38)

### Hybrid Pattern

The SkateSim Engine uses a **hybrid ECS pattern** combining traditional ECS with pragmatic design choices:

1. **ECS Pattern** ✅
    - Scene extends GameObject and can have components
    - Components store pure data (no logic)
    - Some systems iterate components (AnimationSystem, InputSystem)

2. **Component-based Data Flow** ✅
    - Renderers read from components instead of SceneData
    - DayNightCycleSystem writes to LightingStateComponent
    - Clean separation: components for state, SceneData for serialization metadata

3. **Systems Still Own Config** ⏳ (To be refactored in v0.39)
    - EnvironmentSystem owns EnvironmentConfig directly
    - DayNightCycleSystem owns DayNightCycleConfig (acceptable - cycle parameters)
    - DirectionalLightSystem owns DirectionalLightConfig (acceptable - light settings)

### Why Hybrid?

A pure ECS architecture would require:

- All systems to iterate components
- No direct system-to-system communication
- Complete decoupling of systems

This is ideal but requires significant refactoring. The hybrid approach:

- ✅ Provides immediate benefits (component-based state, clean data flow)
- ✅ Maintains backward compatibility
- ✅ Allows incremental migration
- ⚠️ Creates some coupling (systems accessed via Service Locator)

---

## Component Hierarchy

### Scene Components

```
Scene (extends GameObject)
├── EnvironmentComponent (sky/fog settings)
├── TimeComponent (timeOfDay, timeScale)
├── LightingStateComponent (ambientLight, useAmbient)
├── LightingComponent (computed lighting state)
└── GameObject[] (child entities)
    ├── TransformComponent
    ├── RenderComponent
    ├── InputStateComponent
    ├── SkeletonComponent (animated objects)
    └── Animator (animated objects)
```

### Component Catalog

| Component                  | Properties                                                                                           | Purpose                                | Written By                                                     | Read By                                              |
|----------------------------|------------------------------------------------------------------------------------------------------|----------------------------------------|----------------------------------------------------------------|------------------------------------------------------|
| **EnvironmentComponent**   | skyColor, skyTint, skyExposure, skyRotation, fogColor, fogDensity, fogGradient, renderSky, renderFog | Sky and fog rendering settings         | EnvironmentWindow, LevelEditorSceneInitializer                 | SkyDomeRenderer, LightingUniformsLoader              |
| **TimeComponent**          | timeOfDay, timeScale                                                                                 | Time state and simulation speed        | EnvironmentWindow, GameViewWindow, LevelEditorSceneInitializer | DayNightCycleSystem, SkyDomeRenderer, Scene.update() |
| **LightingStateComponent** | ambientLight, useAmbient                                                                             | Ambient lighting state                 | DayNightCycleSystem, EnvironmentWindow                         | LightingUniformsLoader, EnvironmentWindow            |
| **LightingComponent**      | sunDirection, sunColor, sunIntensity, shadowIntensity, isDaytime                                     | Computed lighting from day/night cycle | (Future: DayNightCycleSystem)                                  | (Future: rendering systems)                          |
| **InputStateComponent**    | moveVector, lookVector, buttons                                                                      | Input state per GameObject             | InputSystem                                                    | PlayerController, SkateboardPhysics                  |
| **SkeletonComponent**      | boneMatrices, skeletonData                                                                           | Skeletal animation data                | AnimationLoader                                                | AnimationSystem, ModelRenderer                       |
| **Animator**               | animations, currentTime, playing                                                                     | Animation controller                   | AnimationLoader                                                | AnimationSystem                                      |

---

## Data Flow

### Environment Data Flow

```
┌─────────────────────┐
│ EnvironmentWindow   │
│ (User Input)        │
└─────────┬───────────┘
          │ Writes
          ▼
┌─────────────────────┐
│ EnvironmentComponent│◄─── LevelEditorSceneInitializer (initial values)
│ - skyColor          │
│ - fogColor          │
│ - renderSky         │
└─────────┬───────────┘
          │ Reads
          ▼
┌─────────────────────┐     ┌─────────────────────┐
│ SkyDomeRenderer     │     │ LightingUniformsLoader │
│ - skyRotation       │     │ - fogColor             │
│ - skyTint           │     │ - fogDensity           │
│ - skyExposure       │     │ - fogGradient          │
└─────────────────────┘     └─────────────────────────┘
```

### Time Data Flow

```
┌─────────────────────┐
│ EnvironmentWindow   │─────┐
│ GameViewWindow      │─────┤ Writes
└─────────────────────┘     │
                            ▼
                    ┌─────────────────────┐
                    │   TimeComponent     │◄─── LevelEditorSceneInitializer (initial)
                    │   - timeOfDay       │
                    │   - timeScale       │
                    └─────────┬───────────┘
                              │ Reads
                              ▼
                    ┌─────────────────────┐
                    │ DayNightCycleSystem │
                    │ - reads timeOfDay   │
                    │ - computes sun      │
                    └─────────┬───────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │  SkyDomeRenderer    │
                    │  - timeOfDay        │
                    └─────────────────────┘
```

### Lighting Data Flow

```
┌─────────────────────────┐
│ DayNightCycleSystem     │
│ - Computes ambientColor │
└───────────┬─────────────┘
            │ Writes
            ▼
┌─────────────────────────┐
│ LightingStateComponent  │◄─── EnvironmentWindow (manual override)
│ - ambientLight          │
│ - useAmbient            │
└───────────┬─────────────┘
            │ Reads
            ▼
┌─────────────────────────┐
│ LightingUniformsLoader  │
│ - Uploads to shader     │
└─────────────────────────┘
```

---

## Migration History

### v0.37 → v0.38 Migration

**Before (v0.37):**

```kotlin
// SceneData held all state
scene.sceneData.skyColor = Vector3f(0.6f, 0.7f, 0.9f)
scene.sceneData.timeOfDay = 12.0f
scene.sceneData.timeScale = 1.0f
scene.sceneData.ambientLight = Vector3f(0.3f, 0.3f, 0.35f)

// Renderers read from SceneData
shader.uploadVec3f(Uniforms.FOG_COLOR, scene.sceneData.fogColor)
val timeOfDay = scene.sceneData.timeOfDay
```

**After (v0.38):**

```kotlin
// Components hold state
scene.addComponent(EnvironmentComponent())
scene.addComponent(TimeComponent(timeOfDay = 12.0f))
scene.addComponent(LightingStateComponent())

// Renderers read from components
val envComponent = scene.getComponent<EnvironmentComponent>()
shader.uploadVec3f(Uniforms.FOG_COLOR, envComponent?.fogColor ?: defaultColor)
val timeComponent = scene.getComponent<TimeComponent>()
val timeOfDay = timeComponent?.timeOfDay ?: 12.0f
```

### Benefits of Migration

1. **Proper Encapsulation**: State lives in components, not global SceneData
2. **Component Lifecycle**: Components can have init(), update(), destroy()
3. **Serialization**: Components are serialized with GameObjects
4. **Testability**: Components can be tested in isolation
5. **Flexibility**: Multiple objects can have different environment settings (future)

---

## System Patterns

### System Types

1. **Iterating Systems** ✅ (Pure ECS)
    - Iterate GameObjects with specific components
    - Update components based on logic
    - Examples: AnimationSystem, InputSystem

2. **Config Systems** ⏳ (Hybrid)
    - Own configuration data class
    - May write to components
    - Examples: EnvironmentSystem, DayNightCycleSystem, DirectionalLightSystem

3. **Rendering Systems** ✅ (Component-based)
    - Read from components
    - No state ownership
    - Examples: SkyDomeRenderer, LightingUniformsLoader

### Best Practices

**When to use components:**

- State that needs to be serialized
- State that varies per GameObject
- State that needs component lifecycle

**When to use system config:**

- Global settings that don't vary
- Complex configuration with many properties
- Settings that are edited via ImGui

**When to use SceneData:**

- Minimal serialization metadata (levelPath)
- Legacy compatibility
- Simple value objects (DirectionalLight)

---

## Future Work (v0.39+)

### Pending Refactors

1. **EnvironmentSystem Iteration** (v0.39)
    - Remove direct config ownership
    - Iterate GameObjects with EnvironmentComponent
    - ImGui reads/writes component directly

2. **InputComponent** (v0.40)
    - Replace InputStateComponent with proper InputComponent
    - InputSystem writes to component

3. **PhysicsComponent** (v0.40)
    - Consolidate physics state
    - Integrate with BulletPhysics3D

### Long-term Goals

- Complete migration to pure ECS pattern
- All systems iterate components
- Eliminate Service Locator pattern where possible
- Comprehensive unit tests for all components

---

## File Reference

### Components (4 new in v0.38)

- `engine/ecs/components/EnvironmentComponent.kt`
- `engine/ecs/components/TimeComponent.kt`
- `engine/ecs/components/LightingStateComponent.kt`
- `engine/ecs/components/LightingComponent.kt`

### Modified Core Files

- `engine/ecs/Scene.kt` - Now extends GameObject
- `engine/ecs/scene/SceneData.kt` - Simplified
- `game/level/LevelData.kt` - Simplified

### Modified Systems

- `engine/ecs/systems/DayNightCycleSystem.kt`
- `engine/ecs/systems/EnvironmentSystem.kt` (pending refactor)

### Modified Renderers

- `engine/render/renderer/SkyDomeRenderer.kt`
- `engine/render/renderer/LightingUniformsLoader.kt`
- `engine/render/renderer/passes/GeometryPass.kt`

### Modified Editor

- `editor/LevelEditorSceneInitializer.kt`
- `editor/windows/EnvironmentWindow.kt`
- `editor/windows/GameViewWindow.kt`

---

## See Also

- [CHANGELOG.md](CHANGELOG.md) - Version history and changes
- [TODO.md](TODO.md) - Upcoming work and roadmap
