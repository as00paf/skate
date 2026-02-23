# 🛹 SkateSim MVP - Master TODO

## ✅ v0.21: Input Mapping & Configuration System - COMPLETED

### Summary

Building on v0.20's input layer foundation, v0.21 completes the input mapping and configuration system with fully
rebindable controls, configurable sensitivities/deadzones, and proper architecture compliance across all camera systems.

**Completed Tasks:**

#### Phase 1: Foundation (Critical)

- [x] **A21.1: Extend InputStateComponent** - Added trick inputs, game state inputs, camera controls
- [x] **A21.2: Create InputMapping Data Structures** - Created `InputBinding`, `InputMappings`, `InputSettings`
- [x] **A21.3: Update InputSystem to Use Mappings** - Replaced hardcoded keys, implemented mouse look
- [x] **A21.4: Extend SettingsManager/KeyBindings** - Integrated input mappings and settings into SystemSettings

#### Phase 2: Gameplay Integration (High)

- [x] **A21.5: Fix GameCamera** - Removed direct hardware polling, reads from InputStateComponent
- [x] **A21.6: Update PlayerController** - Added trick input handling and combination detection

#### Phase 3: Editor Integration (Medium)

- [x] **A21.8: Fix EditorCamera** - Created EditorInputStateComponent, removed direct polling
- [x] **A21.9: Update GizmoSystem** - Verified and documented input handling

**Remaining Tasks:** (Moved to v0.22)

- [ ] **A21.10: Create Input Testing UI** - Debug window for visualizing input state
- [ ] **A21.11: Create Settings UI** - Extended settings window with sensitivity sliders and deadzone configuration

---

## 🔴 v0.22: UI & Configuration (In Progress)

### Remaining Tasks from v0.21

- [ ] **A21.10: Create Input Testing UI** - New debug window:
  - Show current input state (buttons, axes)
  - Visualize deadzones and thresholds
  - Test bindings in real-time
  - **Impact**: Low - Development tool

- [ ] **A21.11: Create Settings UI** - Extended settings window:
  - Sensitivity sliders (mouse, controller)
  - Deadzone configuration
  - **Impact**: Medium - User-facing configuration

---

## Notes

- See `input_architecture_review.md` for detailed architecture analysis
- See CHANGELOG.md for completed v0.15 through v0.20 items
- v0.21 tasks (A21.1-A21.9) completed - full input mapping and configuration system in place
- v0.22 will complete the UI for input testing and settings configuration
