# 🛹 SkateSim Engine - TODO & Roadmap

## Current Focus: ECS Architecture Expansion

The ECS foundation is now in place with Scene as a GameObject supporting components.
Remaining work focuses on completing the hybrid pattern migration and expanding component-based architecture.

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture
documentation.

---

## 🔴 v0.39: ECS Architecture Completion (Planned)

### Summary

Complete the ECS migration by refactoring EnvironmentSystem to iterate components and adding comprehensive unit tests
for the new architecture.

### Pending Tasks from v0.38

- [ ] **A39.0.1: Refactor EnvironmentSystem to iterate components**
  - Location: `engine/ecs/systems/EnvironmentSystem.kt`
  - Remove direct `config: EnvironmentConfig` ownership
  - Add `update(dt: Float)` method that iterates GameObjects with EnvironmentComponent
  - ImGui should read/write EnvironmentComponent on Scene directly
  - Keep preset functionality (apply to Scene's EnvironmentComponent)
  - **Impact**: High - Complete proper ECS pattern for environment

- [ ] **A39.0.2: Add comprehensive unit tests for ECS foundation**
  - Location: `test/.../ecs/components/`, `test/.../ecs/systems/`
  - Test EnvironmentComponent creation, presets, reset
  - Test TimeComponent with getFormattedTime()
  - Test LightingStateComponent and LightingComponent
  - Test Scene as GameObject with components
  - Test component serialization/deserialization
  - Test level save/load with component-based scene data
  - **Impact**: High - Ensure ECS foundation is solid, no regressions

- [ ] **A39.0.3: Write ECS architecture documentation**
  - Location: `docs/ECS_ARCHITECTURE.md` (new) ✅ CREATED
  - Document component-based scene state architecture
  - Explain hybrid pattern and why it was chosen
  - Provide before/after code examples (v0.37 → v0.38)
  - Document when to use components vs system config
  - List all components and their purposes
  - **Status**: Complete - See ECS_ARCHITECTURE.md ✅

---

## 🔵 Future: ECS Architecture Expansion (Planned)

### v0.40: Input and Physics Components

- [ ] **A40.0.1: Create InputComponent**
  - Location: `engine/ecs/components/InputComponent.kt` (new)
  - Properties: moveVector, lookVector, jumpPressed, actionPressed, etc.
  - InputSystem writes to InputComponent on GameObjects
  - **Impact**: High - Input as component

- [ ] **A40.0.2: Refactor InputSystem to write to InputComponent**
  - Location: `engine/ecs/systems/InputSystem.kt`
  - Iterate GameObjects, poll input, write to InputComponent
  - Remove direct component access, use component iteration pattern
  - **Impact**: Medium - Proper ECS input pattern

- [ ] **A40.0.3: Create PhysicsComponent**
  - Location: `engine/ecs/components/PhysicsComponent.kt` (new)
  - Properties: velocity, angularVelocity, mass, friction, etc.
  - Integrate with BulletPhysics3D
  - **Impact**: Medium - Physics state as component

- [ ] **A40.0.4: Document complete ECS patterns**
  - Update `docs/ECS_ARCHITECTURE.md` with InputComponent and PhysicsComponent
  - Add migration guide for future component additions
  - **Impact**: Low - Documentation completeness

---

## End of TODO
