# 🛹 SkateSim Engine - TODO & Roadmap

## Current Focus: Environment System Polish

Upcoming work to add enable/disable functionality and independent sky/fog controls to the EnvironmentSystem.

See [CHANGELOG.md](CHANGELOG.md) for complete history of completed versions.

---

## 🔴 v0.37: Environment System Polish (Planned)

### Summary

Add proper enable/disable support for EnvironmentSystem and independent sky/fog toggles for finer control over
environment rendering.

### Known Issues

- Disabling EnvironmentSystem in SystemsWindow has no effect - environment still renders
- No way to disable sky rendering independently from fog
- No way to disable fog rendering independently from sky
- EnvironmentSystem.enabled flag is not checked during rendering

### Tasks

- [ ] **A37.0.1: Add enabled flag checks to rendering pipeline**
  - Check `environmentSystem.enabled` before reading config
  - Fall back to default values when system is disabled
  - **Impact**: High - System enable/disable actually works

- [ ] **A37.0.2: Add independent sky/fog enable toggles to EnvironmentConfig**
  - Add `renderSky: Boolean = true` and `renderFog: Boolean = true` properties
  - Update `reset()` method to restore defaults
  - **Impact**: High - Independent control of sky and fog

- [ ] **A37.0.3: Add sky/fog toggles to EnvironmentSystem ImGui**
  - Add checkboxes in respective collapsing sections
  - **Impact**: Medium - UI for independent control

- [ ] **A37.0.4: Update SkyDomeRenderer to respect renderSky flag**
  - Skip rendering when disabled
  - **Impact**: High - Sky rendering can be disabled

- [ ] **A37.0.5: Update LightingUniformsLoader to respect renderFog flag**
  - Upload zero density when disabled
  - **Impact**: High - Fog rendering can be disabled

- [ ] **A37.0.6: Update GeometryPass to respect renderSky flag**
  - Use fallback clear color when disabled
  - **Impact**: Medium - Proper clear color when sky disabled

- [ ] **A37.0.7: Add string resources for sky/fog toggles**
  - `lbl.environment_system.render_sky`, `lbl.environment_system.render_fog`
  - **Impact**: Low - Localization for new toggles

- [ ] **A37.0.8: Add unit tests for enabled flag and render toggles**
  - **Impact**: Medium - Ensure toggle functionality

---

## 🔵 Future: Code Quality & Technical Debt (Planned)

### v0.33: Code Quality & Technical Debt

- [ ] Audit and replace remaining `!!` operators with safe calls
- [ ] Review resource management for potential memory leaks
- [ ] Optimize animation blending timing
- [ ] Reduce object allocation in hot loops
- [ ] Increase test coverage for complex systems

### v0.32: ImGui Refactor Cleanup

- [ ] Consolidate system UI patterns
- [ ] Review dockable window registry for dead code
