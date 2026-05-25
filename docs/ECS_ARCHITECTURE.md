# SkateSim Engine - ECS Architecture

## Overview

This document describes the implemented ECS (Entity-Component-System) architecture used in SkateSim Engine, including
the hybrid ECS model, target event-driven editor mutation pipeline, and current engine/editor boundary remediation
status.

---

## Current Architecture (ARCH-023)

### Hybrid Pattern

The SkateSim Engine uses a **hybrid ECS pattern** combining ECS data-oriented composition with explicit subsystem
contracts:

1. **Hybrid ECS core** ✅
     - Scene extends GameObject and can have components
     - Components store pure data (no logic)
     - Systems consume component state (for example `AnimationSystem`, `InputSystem`)

2. **Component-based runtime data flow** ✅
     - Renderers read from components instead of SceneData
     - DayNightCycleSystem writes to LightingStateComponent
     - Clean separation: components for state, SceneData for serialization metadata

3. **Editor mutation pipeline contract** ⚠️ (partially enforced; remediation active)
     - Editor UI mutation requests publish typed events/actions
     - Action handlers execute commands
     - `UndoRedoManager` tracks command history
     - Canonical flow: `UI -> Event -> Handler -> CommandExecutor -> UndoRedoManager`

4. **Engine/editor boundary contracts** ❌ (known violations in active remediation)
   - Violations currently exist where engine code paths import editor packages
     - Engine systems use engine-owned interfaces (`InputMappingsProvider`, `LocalizationProvider`,
       `EngineLogger`)
     - Editor/application adapters are bound through DI (Koin)

### Compliance Snapshot (A48.0.2 planning baseline)

Known violations from latest audit/review:

- `engine/core`, `engine/render`, and `engine/ecs` still import editor types directly in multiple paths.
- Engine lifecycle currently wires editor constructs (`EditorWorkspace`, `ImGuiLayer`) into runtime loop.
- ECS components/systems include editor ImGui/string/settings logic.
- Event pipeline regressions remain in some handlers/windows (direct state mutation still present).
- Koin composition remains monolithic and mixes runtime/editor ownership.
- Layering guard tests are currently too narrow and do not catch all real violations.

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
| **LightingComponent**      | sunDirection, sunColor, sunIntensity, shadowIntensity, isDaytime                                     | Computed lighting from day/night cycle | DayNightCycleSystem                                             | Rendering pipeline consumers                         |
| **InputStateComponent**    | moveVector, lookVector, buttons                                                                      | Input state per GameObject             | InputSystem                                                    | PlayerController, SkateboardPhysics                  |
| **SkeletonComponent**      | boneMatrices, skeletonData                                                                           | Skeletal animation data                | AnimationLoader                                                | AnimationSystem, ModelRenderer                       |
| **Animator**               | animations, currentTime, playing                                                                     | Animation controller                   | AnimationLoader                                                | AnimationSystem                                      |

---

## Data Flow

### Editor Mutation & Undo Data Flow (Target Contract)

```
Editor UI (windows/menus/search/toolbar/project) 
  -> publish typed action event
  -> ActionHandler receives event
  -> ActionHandler executes command
  -> UndoRedoManager records undoable command history
```

This is the required editor mutation entry path. Current implementation is not fully compliant yet; remaining direct UI
mutation and direct command-execution paths are tracked under `A48.0.2`.

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

## Follow-ups (Tracked Remediation)

- Complete editor/engine boundary remediation phases (bootstrap split, DI split, engine import cleanup, ECS extraction,
  guard-test expansion) tracked under `A48.0.2`.
- Extend async command lifecycle coverage for `UndoRedoManager.clear()` while async completion is still in-flight.

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
- `engine/ecs/systems/EnvironmentSystem.kt`

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
