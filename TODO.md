# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for completed items through v0.23
- All v0.23 technical debt items completed

---

## 🔴 v0.24: Lighting & Shadowing Overhaul (Planned)

### Phase 1 — Remove Architectural Problems

- [ ] Remove hardcoded camera-offset point light from LightingUniformsLoader
- [ ] Delete any lighting "hack" that ensures objects are always lit
- [ ] Refactor Directional Light to a single active light (remove separate Sun/Moon lights)
- [ ] Create DirectionalLightComponent:
  - `direction: Vector3f`
  - `color: Vector3f`
  - `intensity: Float`
  - `lightSpaceMatrix: Matrix4f`
- [ ] Create DirectionalLightSystem:
  - Updates direction based on dayCycleTime
  - Computes lightSpaceMatrix
  - Uploads light uniforms to shader

### Phase 2 — Implement Basic Directional Shadow Mapping

- [ ] Create ShadowMap class:
  - FBO with depth texture (2048x2048)
  - Configure GL_DEPTH_COMPONENT
  - Set texture wrapping to CLAMP_TO_BORDER
  - Set border color = vec4(1.0)
- [ ] Create ShadowRenderSystem:
  - Bind shadow FBO
  - Set viewport to shadow resolution
  - Clear depth buffer
  - Render all entities with RenderComponent.castShadow == true
- [ ] Use orthographic projection for directional light:
  - left/right/top/bottom bounds configurable
  - near/far tuned for skate level size
- [ ] Compute lightSpaceMatrix: `lightProjection * lightView`
- [ ] Pass lightSpaceMatrix to shadow pass shader and main PBR shader
- [ ] Implement shadow depth-only shader:
  - Vertex: apply skinning
  - Fragment: empty (depth only)
- [ ] Ensure skinned meshes run animation in shadow pass

### Phase 3 — Modify PBR Shader for Shadows

- [ ] Add uniforms:
  - `uniform sampler2D uShadowMap`
  - `uniform mat4 uLightSpaceMatrix`
- [ ] In vertex shader: calculate FragPosLightSpace
- [ ] In fragment shader:
  - Project coordinates to 0..1
  - Sample depth from shadow map
  - Compare `currentDepth > closestDepth + bias`
- [ ] Add normal-based shadow bias:
  - `bias = max(0.005 * (1.0 - dot(normal, lightDir)), 0.0005)`
- [ ] Multiply directional light contribution by `(1.0 - shadowFactor)`

### Phase 4 — Add PCF (Soft Shadows)

- [ ] Replace single depth compare with 3x3 PCF sampling
- [ ] Average 9 samples around projected coord
- [ ] Make PCF kernel size configurable

### Phase 5 — Clean Day/Night Cycle

- [ ] Replace dual Sun/Moon lights with single blended directional light
- [ ] Interpolate:
  - direction
  - color (warm daylight → cool moonlight)
  - intensity
- [ ] Interpolate ambient color with sky color
- [ ] Lower shadow intensity at night

### Phase 6 — Lighting Refactor (Forward Cleanup)

- [ ] Remove moon-specific logic from shader
- [ ] Replace uSunColor / uMoonColor with `uniform DirectionalLight uDirectionalLight`
- [ ] Optional: Add small array support for 4 point lights

### Phase 7 — Quality Improvements

- [ ] Increase shadow map resolution to 4096 if GPU allows
- [ ] Add configurable shadow distance
- [ ] Add depth clamp or stabilize light projection to reduce shimmering
- [ ] Ensure RenderComponent has:
  - `castShadow: Boolean` flag
  - `receiveShadow: Boolean` flag

### Phase 8 — Next-Level Features (Later)

- [ ] Implement Cascaded Shadow Maps (CSM)
- [ ] Add Image Based Lighting (IBL):
  - Irradiance map
  - Prefiltered environment map
  - BRDF LUT
- [ ] Add Bloom post-processing
- [ ] Add Reflection Probes or SSR

### Validation Checklist

- [ ] Character casts shadow on ground
- [ ] Ground casts shadow onto ramps
- [ ] Animated skeleton casts correct shadow
- [ ] Shadows soften with PCF
- [ ] No shadow acne
- [ ] No Peter Panning
- [ ] Day/night transitions smoothly
