# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for completed items through v0.24
- All v0.23 technical debt items completed
- All v0.24 Phase 7 quality improvements completed

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

## 🔴 v0.25: Lighting Integration (Planned)

### Summary

The lighting and shadow systems (v0.24) were implemented but not integrated into the scene initialization and gameplay.
This phase connects all the new systems to make them actually work in the game.

### Phase 1 — Scene Initialization Integration

- [x] **A25.1: Add DayNightCycleSystem to LevelEditorSceneInitializer**
- [x] **A25.2: Add DirectionalLightSystem to LevelEditorSceneInitializer**
- [x] **A25.3: Create DirectionalLightComponent entity in LevelEditorSceneInitializer**

### Phase 2 — Environment Window Integration

- [x] **A25.4: Update EnvironmentWindow to read from DirectionalLightComponent**
- [x] **A25.5: Integrate DayNightCycleSystem with EnvironmentWindow time controls**
- [x] **A25.6: Connect fog settings to EnvironmentWindow**

### Phase 3 — Skater Integration

- [x] **A25.7: Verify InputStateComponent integration with Skater**
- [x] **A25.8: Add shadow flags to Skater RenderComponent**
- [x] **A25.9: Add shadow flags to environment objects**

### Phase 4 — Verification & Testing

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
