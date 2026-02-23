# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.23: Input System Code Quality (Planned)

### Technical Debt from Input System Review

- [ ] **A23.1: Fix checkButtonBindingBeginPress()** - Currently returns "held" instead of "begin press"
  - Need to track previous frame button states for proper rising edge detection
  - **Impact**: High - Trick inputs and one-frame actions may not work correctly

- [ ] **A23.2: Consistent inverted flag usage** - Primary declarations don't match resetToDefaults()
  - `moveUp` and `cameraLookY` should have `inverted = true` in primary declarations
  - **Impact**: Medium - Saved configs may have inconsistent inversion behavior

- [ ] **A23.3: Use inverted flag in getAxisFromBinding()** - Currently uses hardcoded axis checking
  - Should respect `InputBinding.inverted` instead of `if (axisIndex == 1 || axisIndex == 3)`
  - **Impact**: Low - Works but defeats purpose of configurable inversion

- [ ] **A23.4: Remove duplicate deadzone logic** - `InputProvider.getMovementVector()` has hardcoded threshold
  - Either remove function or make it use `InputSettings`
  - **Impact**: Medium - Bypasses configurable deadzones if used

- [ ] **A23.5: Create EditorInputMappings** - Editor camera uses hardcoded keys instead of mappings
  - Extract editor bindings to dedicated `EditorInputMappings` class
  - **Impact**: Low - Editor bindings not configurable via UI

---

## Notes

- See `input_architecture_review.md` for detailed architecture analysis
- See CHANGELOG.md for completed v0.15 through v0.21 items
- v0.21 tasks (A21.1-A21.11) COMPLETED - full input mapping and configuration system with complete UI
- v0.23 will address technical debt and code quality issues in input system
