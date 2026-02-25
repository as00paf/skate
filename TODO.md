# 🛹 SkateSim MVP - Master TODO

## Notes

- See CHANGELOG.md for all completed items through v0.27
- This file contains remaining v0.28 work (bug fixes + verification)

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

- [x] **A28.0.5: Add logging when shadow pass is skipped**
  - Uses `LoggerService.logEngine()` instead of println
  - Logs when light system is null or castShadows is false
  - Format: `[ShadowPass] Skipped: lightSystem=true/false, castShadows=true/false`
  - Location: `ShadowPass.kt` line 44, `RenderResourcesFactory.kt` line 229

- [x] **A28.0.6: Fix inconsistent shadow bias defaults**
  - Reviewed code - fallback values (0.005, 0.01) match DirectionalLightConfig defaults exactly
  - Fallback is a safety measure for edge cases where shadow map exists but light system is null
  - No actual bug found

- [x] **A28.0.7: Fix Out-of-Bounds Shadow Artifacts**
  - Added early exit in `calculateShadow()` for fragments outside shadow map
  - Returns 0.0 (no shadow) instead of sampling border color
  - Checks: `projCoords` outside [-1, 1] range for x, y, z
  - Location: `assets/shaders/shader_3d_default.glsl` line 162

- [x] **A28.0.8: Support Alpha Masking in Shadow Pass**
  - Updated `shadow.glsl` to sample base color texture and discard fragments with alpha < cutoff
  - Added vertex shader texture coordinate passthrough
  - Added fragment shader uniforms: `uBaseColorTexture`, `uAlphaMode`, `uAlphaCutoff`, `uHasBaseColorTexture`
  - Added `Uniforms.HAS_BASE_COLOR_TEXTURE` constant to `ShaderConst.kt`
  - Updated `ShadowRenderer` to bind base color texture and upload alpha uniforms for MASK mode materials
  - OPAQUE and BLEND modes render all fragments (depth-only)
  - Locations: `assets/shaders/shadow.glsl`, `ShadowRenderer.kt`, `ShaderConst.kt`

- [x] **A28.0.9: Fix TranslateGizmo hardcoded resolution**
  - Line 100: `screenToRay(mouseX, mouseY, 1920f, 1080f)` → use `viewportSize.x, viewportSize.y`
  - Lines 159-160: `worldToScreen(..., 1920f, 1080f)` → use `viewportSize.x, viewportSize.y` (x2)
  - Pattern: `val viewportSize = mouseListener.getGameViewportSize()`
  - Location: `editor/gizmos/TranslateGizmo.kt`

- [ ] **A28.0.10: Fix RotationGizmo hardcoded resolution**
  - Line 63: `screenToRay(mouseX, mouseY, 1920f, 1080f)` → use `viewportSize.x, viewportSize.y`
  - Pattern: `val viewportSize = mouseListener.getGameViewportSize()`
  - Location: `editor/gizmos/RotationGizmo.kt`

- [ ] **A28.0.11: Fix ScaleGizmo hardcoded resolution**
  - Line 81: `screenToRay(mouseX, mouseY, 1920f, 1080f)` → use `viewportSize.x, viewportSize.y`
  - Lines 162-163: `worldToScreen(..., 1920f, 1080f)` → use `viewportSize.x, viewportSize.y` (x2)
  - Pattern: `val viewportSize = mouseListener.getGameViewportSize()`
  - Location: `editor/gizmos/ScaleGizmo.kt`

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
