# 🛹 SkateSim MVP - Master TODO

## ✅ v0.20: Input Layer Refactoring - COMPLETED

### Summary

Successfully refactored the input layer to separate raw hardware polling from gameplay logic.

**Completed Tasks:**

- [x] **A20.1**: GamepadListener naming (already correct)
- [x] **A20.2**: Created InputStateComponent
- [x] **A20.3**: Created InputSystem
- [x] **A20.4**: Updated PlayerController to read from InputStateComponent
- [x] **A20.5**: Updated KoinModule with InputSystem
- [x] **A20.6**: Updated LevelEditorSceneInitializer with InputSystem

**Remaining Issues:** (Moved to v0.21)

- Mouse look not implemented in InputSystem
- Hardcoded key bindings throughout codebase
- EditorCamera and GameCamera bypass InputSystem
- Limited InputStateComponent (no trick inputs, pause, reset, etc.)
- No configurable input mappings

---

## 🔴 v0.21: Input Mapping & Configuration System

### Problem Statement

While A20.x established the InputSystem → InputStateComponent architecture, several critical issues remain:

1. **Hardcoded Inputs**: Key bindings are hardcoded in multiple places (InputSystem, EditorCamera, GameCamera)
2. **Architecture Violations**: EditorCamera and GameCamera directly poll KeyListener/MouseListener/IInputProvider
3. **Missing Mouse Integration**: InputSystem.pollMouseInput() is empty (has TODO comment)
4. **Limited InputStateComponent**: Only supports move/jump/sprint - no trick inputs, pause, reset, camera controls
5. **No Configuration**: Deadzones, sensitivities, thresholds are hardcoded
6. **Editor-Only KeyBindings**: SettingsManager.keyBindings only covers gizmo controls

### Current Architecture Issues

**Direct Hardware Polling (Violations):**

- `EditorCamera` directly calls `keyListener.isKeyPressed()` and `mouseListener.getDX()`
- `GameCamera` creates its own `MouseListener` instance and polls `IInputProvider`
- `InputSystem` has hardcoded WASD, Space, Shift instead of using settings

**Missing Features:**

- No mouse look integration in InputSystem
- No trick input support (flip, grind, grab, manual)
- No game state inputs (pause, reset, camera reset, screenshot)
- No configurable input mappings for keyboard or gamepad
- No sensitivity/deadzone configuration

### Target Architecture

See `input_architecture_review.md` for detailed analysis.

**Key Principles:**

1. **Single Source of Truth**: All input flows through InputSystem → InputStateComponent
2. **Fully Rebindable**: All keyboard and gamepad inputs configurable via SettingsManager
3. **Unified Configuration**: One place for all input mappings, sensitivities, deadzones
4. **Game/Editor Separation**: Editor inputs and gameplay inputs separately configurable

### Target Data Structure

```kotlin
// Extended KeyBindings with full input mapping support
@Serializable
data class KeyBindings(
  // Editor (existing)
  var gizmoTranslate: Int = 87,
  var gizmoRotate: Int = 69,
  var gizmoScale: Int = 82,

  // Movement
  var moveUp: Int = 87,       // W
  var moveDown: Int = 83,     // S
  var moveLeft: Int = 65,     // A
  var moveRight: Int = 68,    // D

  // Actions
  var jump: Int = 32,         // Space
  var sprint: Int = 340,      // Left Shift
  var crouch: Int = 341,      // Left Control

  // Camera
  var cameraReset: Int = 82,  // R

  // Tricks
  var flipLeft: Int = 81,     // Q
  var flipRight: Int = 69,    // E
  var kickflip: Int = 87,     // W
  var heelflip: Int = 83,     // S

  // Game State
  var pause: Int = 256,       // Escape
  var reset: Int = 260,       // Delete

  // Gamepad mappings (button indices)
  var gamepadJump: Int = 0,   // A Button
  var gamepadFlip: Int = 4,   // LB
  // ... etc
)

@Serializable
data class InputSettings(
  // Deadzones
  var leftStickDeadzone: Float = 0.15f,
  var rightStickDeadzone: Float = 0.1f,
  var triggerThreshold: Float = 0.5f,

  // Sensitivities
  var mouseSensitivity: Float = 0.1f,
  var controllerSensitivity: Float = 2.0f,

  // Movement
  var movementThreshold: Float = 0.15f,
  var sprintThreshold: Float = 0.65f,

  // Physics
  var jumpImpulse: Float = 300.0f,
  var walkSpeed: Float = 2.5f,
  var runSpeed: Float = 7.5f
)
```

### Tasks

#### Phase 1: Foundation (Critical)

- [x] **A21.1: Extend InputStateComponent** - `engine/ecs/components/InputStateComponent.kt`:
  - **Added** trick inputs: `flipLeftPressed`, `flipRightPressed`, `kickflipPressed`, `heelflipPressed`, `grabPressed`,
    `manualPressed`
  - **Added** game state inputs: `pausePressed`, `resetPressed`, `cameraResetPressed`, `stanceChangePressed`
  - **Added** `crouchPressed` for crouching/manual setup
  - **Organized** properties into logical sections (Movement, Jump, Tricks, Camera, Game State, Physics)
  - **Updated** `reset()` method to handle all new properties
  - **Enhanced** KDoc with comprehensive documentation and usage examples
  - **Impact**: High - Required for full skateboarding controls
  - **Status**: Completed and compiles successfully

- [x] **A21.2: Create InputMapping Data Structures** - New `engine/input/InputMapping.kt`:
  - **Created** `InputBinding` data class with `keyboardKey`, `gamepadButton`, `gamepadAxis`, `inverted` properties
  - **Created** `InputMappings` data class with 26 bindings for all gameplay actions:
    - Movement (6): moveUp/Down/Left/Right, sprint, crouch
    - Jump (1): jump
    - Tricks (6): flipLeft/Right, kickflip, heelflip, grab, manual
    - Camera (3): cameraLookX/Y, cameraReset
    - Game State (4): pause, reset, stanceChange/Right
    - Editor (6): gizmo modes, measure, deselect
  - **Added** helper methods: `getAllBindings()`, `resetToDefaults()`, `getDescription()`
  - **Created** `InputSettings` data class in `SystemSettings.kt`:
    - Deadzone configuration (leftStick, rightStick, trigger)
    - Sensitivity configuration (mouse, controller)
    - Movement thresholds (movement, sprint)
    - Physics configuration (jumpImpulse, walkSpeed, runSpeed, rotationSpeed, etc.)
    - `validate()` method for range clamping
  - **Updated** `SystemSettings` with `inputMappings` and `inputSettings` properties
  - **Deprecated** legacy `KeyBindings` (kept for backwards compatibility)
  - **Impact**: High - Foundation for rebindable controls
  - **Status**: Completed and compiles successfully

- [x] **A21.3: Update InputSystem to Use Mappings** - Refactor `InputSystem`:
  - **Injected** `SettingsManager` and `MouseListener` via constructor
  - **Replaced** all hardcoded keys with configurable `InputMappings`
  - **Implemented** mouse look integration (TODO fixed) - reads `MouseListener.getDx()/getDy()`
  - **Made** deadzones and thresholds configurable from `InputSettings`
  - **Added** helper methods: `getAxisFromBinding()`, `checkButtonBindingActive()`, `checkBindingActive()`
  - **Updated** `KoinModule` to register `InputSystem` with new dependencies
  - **Updated** `LevelEditorSceneInitializer` to inject `InputSystem` from Koin
  - **Fixed** gamepad axis inversion (Y-axis auto-inverted for GLFW coordinate system)
  - **Impact**: Critical - Centralizes all input configuration
  - **Status**: Completed and compiles successfully

- [x] **A21.4: Extend SettingsManager/KeyBindings** - `editor/data/SystemSettings.kt`:
  - **Created** `InputSettings` data class with comprehensive configuration:
    - Deadzone settings: `leftStickDeadzone`, `rightStickDeadzone`, `triggerThreshold`
    - Sensitivity settings: `mouseSensitivity`, `controllerSensitivity`
    - Movement thresholds: `movementThreshold`, `sprintThreshold`
    - Physics settings: `jumpImpulse`, `walkSpeed`, `runSpeed`, `rotationSpeed`, `takeOffTime`, `inputSmoothing`
    - `validate()` method for range clamping
  - **Updated** `SystemSettings` with `inputMappings` and `inputSettings` properties
  - **Deprecated** legacy `KeyBindings` (kept for backwards compatibility)
  - **Impact**: High - Single source of truth for input config
  - **Status**: Completed in A21.2 (created alongside InputMappings)

#### Phase 2: Gameplay Integration (High)

- [x] **A21.5: Fix GameCamera** - Update `game/camera/GameCamera.kt`:
  - **Removed** direct `MouseListener` instantiation (was: `MouseListener()`)
  - **Removed** direct `IInputProvider` polling
  - **Now reads** camera look from `InputStateComponent.cameraLook` (combines gamepad + mouse)
  - **Made** sensitivity configurable from `InputSettings.controllerSensitivity`
  - **Updated** `update()` signature to accept `InputStateComponent` parameter
  - **Added** time-based movement (`dt * 60f`) for frame-rate independence
  - **Impact**: High - Architecture compliance
  - **Status**: Completed and compiles successfully

- [x] **A21.6: Update PlayerController** - Extend `game/player/PlayerController.kt`:
  - **Added** `handleTrickInputs()` method to process trick inputs from `InputStateComponent`
  - **Added** trick input tracking: `flipLeftHeld`, `flipRightHeld` for combination detection
  - **Added** trick combination detection logic (kickflip, heelflip, grab, manual)
  - **Added** trick input logging for debugging
  - **Made** `desiredMoveDirection` public for `PlayerStateManager` access
  - **Made** `isJumping` public for state manager access
  - **Documented** configurable properties in KDoc (references `InputSettings`)
  - **Note**: Thresholds and physics values remain as class properties (can be set from InputSettings externally)
  - **Impact**: Medium - Extended functionality
  - **Status**: Completed and compiles successfully

#### Phase 3: Editor Integration (Medium)

- [ ] **A21.8: Fix EditorCamera** - Update `editor/EditorCamera.kt`:
  - Remove direct KeyListener/MouseListener polling
  - Use input mappings from SettingsManager
  - Add editor camera bindings to KeyBindings
  - **Impact**: Medium - Architecture compliance

- [ ] **A21.9: Update GizmoSystem** - Refactor to use input mappings:
  - Already uses SettingsManager.keyBindings (good)
  - Ensure consistency with new structure
  - **Impact**: Low - Verification only

#### Phase 4: UI & Configuration

- [ ] **A21.10: Extend Key Binding UI** - Update `editor/imgui/ImGuiLayer.kt`:
  - Extend renderKeyBindingsWindow() for gameplay inputs
  - Add tabs: Editor, Gameplay, Camera, Gamepad
  - Support rebinding all inputs
  - Add reset to default functionality
  - **Impact**: Medium - User-facing configuration

- [ ] **A21.11: Create Input Testing UI** - New debug window:
  - Show current input state (buttons, axes)
  - Visualize deadzones and thresholds
  - Test bindings in real-time
  - **Impact**: Low - Development tool

- [ ] **A21.12: Create Settings UI** - Extended settings window:
  - Sensitivity sliders (mouse, controller)
  - Deadzone configuration
  - **Impact**: Medium - User-facing configuration

### Execution Order After Refactor

```
1. InputSystem (EARLY) 
   └─> Polls IInputProvider with configurable mappings
   └─> Applies configurable deadzones/thresholds
   └─> Writes to extended InputStateComponent

2. InputStateComponent
   ├─> Movement: moveDirection, sprintPressed
   ├─> Actions: jumpPressed/Held, crouchPressed
   ├─> Tricks: flipLeft/Right, kickflip, heelflip, grab, manual
   ├─> Camera: cameraLook (mouse + gamepad integrated)
   └─> Game: pausePressed, resetPressed, cameraResetPressed

3. PlayerController (DEFAULT)
   └─> Reads InputStateComponent
   └─> Applies physics with configurable values

4. GameCamera (via InputStateComponent)
   └─> Reads cameraLook from InputStateComponent
   └─> Applies configurable sensitivity

5. EditorCamera (DEFAULT)
   └─> Uses input mappings from SettingsManager
   └─> Configurable key bindings
```

---

## Notes

- See `input_architecture_review.md` for detailed architecture analysis
- See CHANGELOG.md for completed v0.15 through v0.19 items
- v0.20 tasks (A20.1-A20.6) completed - basic InputSystem infrastructure in place
- v0.21 will complete the input mapping and configuration system
