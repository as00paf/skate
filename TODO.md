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
- [ ] **A24.9: Create ShadowRenderSystem**
  - Bind shadow FBO
  - Set viewport to shadow resolution
  - Clear depth buffer
  - Render all entities with RenderComponent.castShadow == true

- [ ] **A24.10: Configure orthographic projection** for directional light
  - left/right/top/bottom bounds configurable
  - near/far tuned for skate level size

- [ ] **A24.11: Compute lightSpaceMatrix** (`lightProjection * lightView`)

- [ ] **A24.12: Pass lightSpaceMatrix** to shadow pass shader and main PBR shader

- [ ] **A24.13: Implement shadow depth-only shader**
  - Vertex: apply skinning
  - Fragment: empty (depth only)

- [ ] **A24.14: Ensure skinned meshes run animation** in shadow pass

### Phase 3 — Modify PBR Shader for Shadows

- [ ] **A24.15: Add shadow uniforms**
  - `uniform sampler2D uShadowMap`
  - `uniform mat4 uLightSpaceMatrix`

- [ ] **A24.16: Calculate FragPosLightSpace** in vertex shader

- [ ] **A24.17: Implement shadow comparison** in fragment shader
  - Project coordinates to 0..1
  - Sample depth from shadow map
  - Compare `currentDepth > closestDepth + bias`

- [ ] **A24.18: Add normal-based shadow bias**
  - `bias = max(0.005 * (1.0 - dot(normal, lightDir)), 0.0005)`

- [ ] **A24.19: Apply shadow factor** to directional light contribution

### Phase 4 — Add PCF (Soft Shadows)

- [ ] **A24.20: Implement 3x3 PCF sampling**

- [ ] **A24.21: Average 9 samples** around projected coord

- [ ] **A24.22: Make PCF kernel size** configurable

### Phase 5 — Clean Day/Night Cycle

- [ ] **A24.23: Replace dual Sun/Moon lights** with single blended directional light

- [ ] **A24.24: Interpolate light properties**
  - direction
  - color (warm daylight → cool moonlight)
  - intensity

- [ ] **A24.25: Interpolate ambient color** with sky color

- [ ] **A24.26: Lower shadow intensity** at night

### Phase 6 — Lighting Refactor (Forward Cleanup)

- [ ] **A24.27: Remove moon-specific logic** from shader

- [ ] **A24.28: Replace uSunColor / uMoonColor** with `uniform DirectionalLight uDirectionalLight`

- [ ] **A24.29: Optional: Add point light array** support (4 lights)

### Phase 7 — Quality Improvements

- [ ] **A24.30: Increase shadow map resolution** to 4096 if GPU allows

- [ ] **A24.31: Add configurable shadow distance**

- [ ] **A24.32: Add depth clamp** or stabilize light projection to reduce shimmering

- [ ] **A24.33: Add RenderComponent flags**
  - `castShadow: Boolean`
  - `receiveShadow: Boolean`

### Phase 8 — Next-Level Features (Later)

- [ ] **A24.34: Implement Cascaded Shadow Maps (CSM)**

- [ ] **A24.35: Add Image Based Lighting (IBL)**
  - Irradiance map
  - Prefiltered environment map
  - BRDF LUT

- [ ] **A24.36: Add Bloom post-processing**

- [ ] **A24.37: Add Reflection Probes or SSR**

### Validation Checklist

- [ ] Character casts shadow on ground
- [ ] Ground casts shadow onto ramps
- [ ] Animated skeleton casts correct shadow
- [ ] Shadows soften with PCF
- [ ] No shadow acne
- [ ] No Peter Panning
- [ ] Day/night transitions smoothly
