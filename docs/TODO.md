# 🛹 SkateSim Engine - TODO & Roadmap

## Current Status: ECS Architecture Complete ✅

The ECS architecture is now 100% complete through v0.42.
All systems follow proper component-based patterns with no direct engine coupling in gameplay logic.

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture documentation.

---

## Architecture Summary

### Components (15 total)
- **Core**: Transform, RenderComponent, RigidBody3D, PhysicsComponent
- **Input**: InputStateComponent, EditorInputStateComponent
- **Animation**: SkeletonComponent, Animator
- **Environment**: EnvironmentComponent, TimeComponent, LightingStateComponent, LightingComponent
- **Editor**: NonPickable, ModularTile, SpriteRenderer
- **Special**: Component (base class)

### Systems (12 total)
- **ECS Infrastructure**: System, SystemManager, GameObjectManager
- **Gameplay**: InputSystem, PhysicsSystem, AnimationSystem, DayNightCycleSystem
- **Environment**: EnvironmentSystem, DirectionalLightSystem, GridLines
- **Editor**: GizmoSystem, MouseControls

### ECS Pattern Compliance
- ✅ All gameplay systems read from components (not engine directly)
- ✅ All physics state synced via PhysicsSystem
- ✅ All input state written by InputSystem
- ✅ All animation state managed by AnimationSystem
- ✅ All environment state in components
- ✅ Clean separation: Components = data, Systems = logic

---

## Future: Optional Enhancements (No ECS work remaining)

### Potential Future Work

- [ ] **Code Quality & Technical Debt**
  - Audit and replace remaining `!!` operators with safe calls
  - Review resource management for potential memory leaks
  - Optimize animation blending timing
  - Reduce object allocation in hot loops
  - Increase test coverage for complex systems

- [ ] **ImGui Refactor Cleanup**
  - Consolidate system UI patterns
  - Review dockable window registry for dead code

---

## End of TODO
