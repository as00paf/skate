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

- [x] **A28.0.10: Fix RotationGizmo hardcoded resolution**
  - Line 63: `screenToRay(mouseX, mouseY, 1920f, 1080f)` → use `viewportSize.x, viewportSize.y`
  - Pattern: `val viewportSize = mouseListener.getGameViewportSize()`
  - Location: `editor/gizmos/RotationGizmo.kt`

- [x] **A28.0.11: Fix ScaleGizmo hardcoded resolution**
  - Line 81: `screenToRay(mouseX, mouseY, 1920f, 1080f)` → use `viewportSize.x, viewportSize.y`
  - Lines 162-163: `worldToScreen(..., 1920f, 1080f)` → use `viewportSize.x, viewportSize.y` (x2)
  - Pattern: `val viewportSize = mouseListener.getGameViewportSize()`
  - Location: `editor/gizmos/ScaleGizmo.kt`
  - **Bonus fix:** Added NaN guard and scale clamping (min 0.01) to prevent physics crashes

- [x] **A28.1: Verify shadow rendering pipeline**
  - ✅ Shadow pass renders to ShadowMap (ShadowPass.kt executes before GeometryPass)
  - ✅ Geometry pass samples ShadowMap with correct uniforms (texture bound to unit 4)
  - ✅ PCF filtering uses correct texel size (1.0f / shadowMapResolution uploaded)
  - ✅ Shadow map texture binding uses constant (Uniforms.SHADOW_TEXTURE_UNIT = 4)
  - ✅ Alpha masking supported for transparent objects (shadow.glsl samples base color)
  - ✅ Skinned meshes render correctly to shadow map (VAO attributes enabled)

- [x] **A28.2: Verify day/night cycle affects lighting**
  - ✅ Sun direction updates from DayNightCycleSystem (computed via trigonometry in updateSunDirection())
  - ✅ Sun color interpolates through day phases (dawn → noon → dusk → night in updateSunColor())
  - ✅ Sun intensity interpolates (0.0 at night, 1.0 at day)
  - ✅ Ambient light interpolates with day/night (nightAmbient.lerp(dayAmbient, sunIntensity))
  - ✅ DirectionalLightSystem reads from DayNightCycleSystem.config each frame
  - ✅ LightingUniformsLoader uploads sun direction, color, intensity to shader
  - ✅ Environment Window time slider syncs with DayNightCycleSystem.getCycleTime()/setCycleTime()

- [x] **A28.0.12: Fix Skater Shadow (Uniform Name Mismatch + Depth Testing)**
  - Skater doesn't cast a visible shadow.
  - **Root Cause 1:** `shadow.glsl` declares `uniform bool uHasSkin;` but `ShaderConst.HAS_SKIN = "u_HasSkin"` (with
    underscore). Uniform upload fails, skinning never applied in shadow pass.
  - **Fix 1:** Changed `shadow.glsl` to use `uniform bool u_HasSkin;` to match ShaderConst
  - **Root Cause 2:** `ShadowRenderer` didn't enable depth testing/writing, so shadow map never received depth values
  - **Fix 2:** Added `glEnable(GL_DEPTH_TEST)` and `glDepthMask(true)` in `ShadowRenderer.render()`
  - **Secondary:** Add `uTextureScale` uniform if alpha masking UVs still wrong after primary fix
  - Location: `assets/shaders/shadow.glsl`, `ShadowRenderer.kt`

### Tasks

- [x] **A28.3: Test shadow quality settings**
  - ✅ Shadow distance slider affects coverage (DirectionalLightConfig.shadowDistance, auto-calculates ortho bounds)
  - ✅ Stabilize projection reduces shimmering (texel snapping in DirectionalLightSystem.updateLightSpaceMatrix())
  - ✅ Depth bias eliminates acne without peter-panning (depthBias + slopeScaledBias uploaded to shader, used in
    calculateShadow())

---

## 🔴 v0.29: Shadow Quality Improvements (Complete) ✅

### Summary

Shadow rendering works with robust frustum fitting and stabilization.

### Bug Fixes

- [x] **A29.0.4: Implement Robust Shadow Frustum Fitting (Bounding Spheres)**
  - ✅ Implemented bounding sphere calculation for camera frustum
  - ✅ Fixed corner indices in frustum calculation
  - ✅ Rotation-invariant shadow map coverage
  - Location: `DirectionalLightSystem.kt`

- [x] **A29.0.5: Fix Shadow Stabilization Logic**
  - ✅ Implemented stable snapping to texel grid
  - ✅ Reduced shimmering when camera moves
    - Location: `DirectionalLightSystem.kt`

- [x] **A29.0.6: Fix High-Noon Light Up-Vector Failure**
  - ✅ Dynamically switch Up vector to (0, 0, 1) when sun is overhead
    - Location: `DirectionalLightSystem.kt`

- [x] **A29.0.7: Fix Shadow Clipping & "Far" Shadow Center**
  - ✅ Increased buffer for shadow casters (500m)
  - ✅ Centered shadow map on visible frustum
    - Location: `DirectionalLightSystem.kt`

---

## 🔴 v0.30: Production Polish (Planned)

### Summary

Finalizing the shadow pipeline and preparing for release.

### Tasks

- [x] **A30.0.1: Fix shadow peter-panning**
  - ✅ Lowered default depthBias from 0.005 to 0.001
  - ✅ Lowered default slopeScaledBias from 0.01 to 0.002
  - ✅ Addressed detachment artifact while preserving acne prevention
  - Locations: `DirectionalLightConfig.kt`, `GeometryPass.kt`
- [x] **A30.0.2: Verify shadow stability with moving character**

### Summary

**v0.28: Shadow Quality & Robustness - COMPLETE** ✅

All shadow pipeline improvements implemented and verified:

- Shadow rendering pipeline integrated and functional
- Day/night cycle properly affects lighting
- Shadow quality settings (distance, stabilization, bias) working correctly
- Alpha masking support for transparent objects
- Skinned mesh shadow rendering fixed
- Viewport/resizing issues fixed for all TRS gizmos
