# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for completed items through v0.26
- All v0.23 technical debt items completed
- All v0.24 Phase 7 quality improvements completed
- All v0.25 Phases 1-3 integration items completed
- v0.26 lighting architecture refactoring completed - systems now own configuration directly

---

## ✅ v0.24: Lighting & Shadowing Quality Improvements - COMPLETED

### Summary

All Phase 7 quality improvements completed and moved to CHANGELOG.md.

**Completed in v0.24:**

- Dynamic shadow map resolution (auto-scales to 4096 or GPU max)
- Configurable shadow distance (10-200m range)
- Shadow stabilization (texel snapping to eliminate shimmering)
- Depth bias controls (constant + slope-scaled)
- RenderComponent shadow flags (castShadow, receiveShadow)

See CHANGELOG.md for full details.

---

## ✅ v0.25: Lighting Integration - COMPLETED

### Summary

All Phase 1-3 integration tasks completed and moved to CHANGELOG.md.

**Completed in v0.25:**

- DayNightCycleSystem and DirectionalLightSystem integration
- Environment Window light controls and time synchronization
- Shadow flags on Skater and all environment prefabs
- Fixed light consistency issue (before vs after first play)

See CHANGELOG.md for full details.

**Remaining:**

- Phase 4 verification tasks (shadow pipeline, day/night cycle, quality settings)

### Phase 4 — Verification & Testing (Remaining)

- [ ] **A25.10: Verify shadow rendering pipeline**
    - Shadow pass renders to ShadowMap
    - Geometry pass samples ShadowMap with correct uniforms
    - PCF filtering uses correct texel size

- [ ] **A25.11: Verify day/night cycle affects lighting**
    - Sun direction updates from DayNightCycleSystem
    - Sun color/intensity interpolate through day phases
    - Ambient light interpolates with day/night

- [ ] **A25.12: Test shadow quality settings**
    - Shadow distance slider affects coverage
    - Stabilize projection reduces shimmering
    - Depth bias eliminates acne without peter-panning

---

## ✅ v0.26: Lighting Architecture Refactor - COMPLETED

### Summary

Refactored lighting systems to own their configuration directly, eliminating artificial GameObjects and improving
architecture.

**Completed in v0.26:**

- Created `DayNightCycleConfig` and `DirectionalLightConfig` data classes
- Updated systems to own configuration directly
- Removed `DayNightCycleComponent` and `DirectionalLightComponent`
- Updated `LevelEditorSceneInitializer` to initialize configs directly
- Updated `EnvironmentWindow` to read/write from system configs
- Updated `LightingUniformsLoader` and `GeometryPass` to use configs

**Architecture improvements:**

- No artificial GameObjects ("DayNightCycle", "DirectionalLight") in scene hierarchy
- Direct property access instead of entity lookup every frame
- Cleaner separation of concerns - systems own logic, configs own data
- Configuration still `@Serializable` for save/load support

See CHANGELOG.md for full details.

---

## 🔴 v0.27: Shadow Pipeline Integration (Planned)

### Summary

The `ShadowRenderer` class exists but is **not integrated** into the rendering pipeline.
This task covers adding the shadow pass to the renderer and verifying end-to-end functionality.

### Critical Missing Integration

- [x] **A27.0: Integrate ShadowRenderer into the rendering pipeline**
  - [x] Add `ShadowRenderer` to `Renderers` data class in `RenderResources.kt`
  - [x] Instantiate `ShadowRenderer` in `RenderResourcesFactory.createRenderers()`
  - [x] Add shadow pass to `Renderer.render()` method (must execute BEFORE geometry pass)
  - [x] Update `Renderer.destroy()` to cleanup shadow renderer
  - [x] Create dedicated `ShadowPass`

### Tasks

- [ ] **A27.1: Verify shadow rendering pipeline**
  - Shadow pass renders to ShadowMap
  - Geometry pass samples ShadowMap with correct uniforms
  - PCF filtering uses correct texel size

- [ ] **A27.2: Verify day/night cycle affects lighting**
  - Sun direction updates from DayNightCycleSystem
  - Sun color/intensity interpolate through day phases
  - Ambient light interpolates with day/night

- [ ] **A27.3: Test shadow quality settings**
  - Shadow distance slider affects coverage
  - Stabilize projection reduces shimmering
  - Depth bias eliminates acne without peter-panning
