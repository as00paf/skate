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

- [x] **A28.0.2: Fix shadow map texture binding synchronization**
  - Reviewed code - texture binding pattern is correct (upload unit index, activate unit, bind texture)
  - No actual bug found

- [x] **A28.0.3: Extract hardcoded texture unit to constant**
  - Created `ShaderConst.Uniforms.SHADOW_TEXTURE_UNIT = 4`
  - Updated `LightingUniformsLoader.kt` to use constant
  - Updated `GeometryPass.kt` to use constant
  - Location: `ShaderConst.kt`, `LightingUniformsLoader.kt:53`, `GeometryPass.kt:114`

- [x] **A28.0.4: Add framebuffer completeness check in ShadowMap**
  - Replaced `assert()` with `IllegalStateException`
  - Throws exception with status code if framebuffer is incomplete
  - Works in both debug and release builds
  - Location: `ShadowMap.kt` line 161

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
