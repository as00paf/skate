# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for all completed items through v0.27
- This file contains only remaining planned work

---

## 🔴 v0.28: Shadow Quality & Robustness (Planned)

### Summary

Remaining shadow pipeline improvements for production-quality shadows.

### Bug Fixes

- [x] **A28.0.1: Fix ShadowRenderer VAO attribute binding**
  - `ShadowRenderer.renderShadowCaster()` binds VAO but doesn't enable attributes 6,7 (joints/weights)
  - Skinned meshes won't render correctly to shadow map
  - Added `vaoId.bindVAO(rawModel.enabledAttributes)` call
  - Location: `ShadowRenderer.kt` line 105

- [ ] **A28.0.2: Fix shadow map texture binding synchronization**
  - `LightingUniformsLoader` sets texture unit but `GeometryPass` binds texture separately
  - Texture unit 4 might not be active when binding texture
  - Move texture binding into `LightingUniformsLoader.loadLightingUniforms()` or ensure GL_TEXTURE4 is active
  - Location: `LightingUniformsLoader.kt` and `GeometryPass.kt` lines 113-115

- [ ] **A28.0.3: Extract hardcoded texture unit to constant**
  - Texture unit 4 is hardcoded in multiple places
  - Create `const val SHADOW_TEXTURE_UNIT = 4` in `ShaderConst` or `TextureSlots`
  - Locations: `GeometryPass.kt:114`, `LightingUniformsLoader.kt:54`

- [ ] **A28.0.4: Add framebuffer completeness check in ShadowMap**
  - `ShadowMap.initialize()` uses assert() which is disabled in release builds
  - Should throw exception or log error if framebuffer is incomplete
  - Location: `ShadowMap.kt` line 125

- [ ] **A28.0.5: Add logging when shadow pass is skipped**
  - `ShadowPass.execute()` returns silently when light is null or shadows disabled
  - Makes debugging difficult
  - Add debug log: `println("[ShadowPass] Skipped: castShadows=${lightSystem.config.castShadows}")`
  - Location: `ShadowPass.kt` line 39

- [ ] **A28.0.6: Fix inconsistent shadow bias defaults**
  - Default bias values in `GeometryPass` (0.005, 0.01) differ from `DirectionalLightConfig` defaults
  - Should use config values consistently
  - Location: `GeometryPass.kt` lines 108-111, `DirectionalLightConfig.kt`

- [ ] **A28.0.8: Fix Out-of-Bounds Shadow Artifacts**
  - Objects beyond shadow distance (`projCoords.z > 1.0`) are rendered black because they sample border color
  - Add early exit in `calculateShadow()` to return 0.0 shadow for out-of-bounds fragments
  - Location: `assets/shaders/shader_3d_default.glsl`

- [ ] **A28.0.9: Support Alpha Masking in Shadow Pass**
  - Transparent/masked objects cast solid rectangular shadows because `shadow.glsl` ignores alpha
  - Update `shadow.glsl` to sample base color and `discard` if alpha < cutoff
  - Update `ShadowRenderer.kt` to bind textures and upload alpha uniforms during shadow pass
  - Locations: `assets/shaders/shadow.glsl`, `ShadowRenderer.kt`

### Tasks

- [ ] **A28.1: Verify shadow rendering pipeline**
  - Shadow pass renders to ShadowMap
  - Geometry pass samples ShadowMap with correct uniforms
  - PCF filtering uses correct texel size

- [ ] **A28.2: Verify day/night cycle affects lighting**
  - Sun direction updates from DayNightCycleSystem
  - Sun color/intensity interpolate through day phases
  - Ambient light interpolates with day/night

- [ ] **A28.3: Test shadow quality settings**
  - Shadow distance slider affects coverage
  - Stabilize projection reduces shimmering
  - Depth bias eliminates acne without peter-panning
