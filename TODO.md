# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for completed items through v0.23
- All v0.23 technical debt items completed

---

## 🔴 v0.24: Lighting & Shadowing Overhaul (Planned)

### Phase 1 — Remove Architectural Problems

- [x] **A24.1: Create DayNightCycleComponent**
- [x] **A24.2: Create DayNightCycleSystem**
- [x] **A24.3: Remove hardcoded camera-offset point light** from LightingUniformsLoader
- [x] **A24.4: Delete lighting hacks** that ensure objects are always lit
- [x] **A24.5: Refactor to single directional light** (remove separate Sun/Moon lights)
- [x] **A24.6: Create DirectionalLightComponent**
- [x] **A24.7: Create DirectionalLightSystem**

### Phase 2 — Implement Basic Directional Shadow Mapping

- [x] **A24.8: Create ShadowMap class**
- [x] **A24.9: Create ShadowRenderer**
- [x] **A24.10: Configure orthographic projection** for directional light
- [x] **A24.11: Compute lightSpaceMatrix** (`lightProjection * lightView`)
- [x] **A24.12: Pass lightSpaceMatrix** to shadow pass shader and main PBR shader
- [x] **A24.13: Implement shadow depth-only shader**
- [x] **A24.14: Ensure skinned meshes run animation** in shadow pass

### Phase 3 — Modify PBR Shader for Shadows

- [x] **A24.15: Add shadow uniforms**
- [x] **A24.16: Calculate FragPosLightSpace** in vertex shader
- [x] **A24.17: Implement shadow comparison** in fragment shader
- [x] **A24.18: Add normal-based shadow bias**
- [x] **A24.19: Apply shadow factor** to directional light contribution

### Phase 4 — Add PCF (Soft Shadows)

- [x] **A24.20: Implement 3x3 PCF sampling**
- [x] **A24.21: Average 9 samples** around projected coord
- [x] **A24.22: Make PCF kernel size** configurable

### Phase 5 — Clean Day/Night Cycle

- [x] **A24.23: Replace dual Sun/Moon lights** with single blended directional light
- [x] **A24.24: Interpolate light properties**
- [x] **A24.25: Interpolate ambient color** with sky color
- [x] **A24.26: Lower shadow intensity** at night

### Phase 6 — Lighting Refactor (Forward Cleanup)

- [x] **A24.27: Remove moon-specific logic** from shader
- [x] **A24.28: Replace uSunColor / uMoonColor** with single directional light

### Phase 7 — Quality Improvements

- [x] **A24.30: Increase shadow map resolution** to 4096 if GPU allows
- [ ] **A24.31: Add configurable shadow distance**
- [ ] **A24.32: Add depth clamp** or stabilize light projection to reduce shimmering
- [ ] **A24.33: Add RenderComponent flags**
  - `castShadow: Boolean`
  - `receiveShadow: Boolean`

### Validation Checklist

- [ ] Character casts shadow on ground
- [ ] Ground casts shadow onto ramps
- [ ] Animated skeleton casts correct shadow
- [ ] Shadows soften with PCF
- [ ] No shadow acne
- [ ] No Peter Panning
- [ ] Day/night transitions smoothly
