# 🛹 SkateSim MVP - Master TODO

## 🔴 v0.20: Input Layer Refactoring

### Problem Statement

The current input layer mixes raw hardware polling with gameplay logic. PlayerController directly polls IInputProvider,
violating ECS separation of concerns. There's no abstraction between raw inputs (button codes, axis values) and gameplay
actions (jump, move, sprint).

### Current Architecture Issues

**Direct Dependencies:**

- `PlayerController` directly calls `inputProvider.getMovementVector()` - mixes raw input with physics
- `MouseListener` is standalone, not integrated with `IInputProvider`
- No gameplay input state component - components read raw hardware state directly

**Naming:**

- `JoystickListener` should be `GamepadListener` (more accurate, modern naming)

### Target Architecture

```
Input Layer (engine/input/)
├── Raw Input Listeners (poll hardware)
│   ├── GamepadListener (renamed from JoystickListener)
│   ├── KeyListener
│   └── MouseListener
├── InputProvider (aggregates raw listeners, implements IInputProvider)
└── InputSystem (ECS System - converts raw → gameplay state)

Gameplay Layer (game/player/)
├── InputStateComponent (stores gameplay inputs)
│   - moveDirection: Vector2f
│   - jumpPressed: Boolean
│   - jumpHeld: Boolean
│   - sprintPressed: Boolean
│   - cameraLook: Vector2f
└── PlayerController (reads InputStateComponent, applies physics)
```

### Tasks

- [ ] **A20.1: Rename JoystickListener to GamepadListener** - `engine/input/listeners/`:
  - Rename file: `JoystickListener.kt` → `GamepadListener.kt`
  - Update class name inside file
  - Update all imports and references
  - Update Koin module registration
  - **Impact**: Low - Better naming clarity

- [x] **A20.2: Create InputStateComponent** - New `ecs/components/InputStateComponent.kt`:
  - **Created**: `InputStateComponent : Component` with gameplay input properties
  - **Properties**:
    - `moveDirection: Vector2f` - Normalized movement direction
    - `jumpPressed: Boolean` - Jump button pressed this frame (one-frame pulse)
    - `jumpHeld: Boolean` - Jump button currently held
    - `sprintPressed: Boolean` - Sprint modifier active
    - `cameraLook: Vector2f` - Right stick / mouse look delta
    - `isGrounded: Boolean` - Grounded state (synced from physics)
  - **Features**:
    - `reset()` method to clear input state each frame
    - `@Contextual` annotations for Vector2f serialization
    - Comprehensive KDoc with usage examples
  - **Impact**: High - Clean separation between input and gameplay logic
  - **Status**: Created and compiles successfully

- [x] **A20.3: Create InputSystem** - New `ecs/systems/InputSystem.kt`:
  - **Created**: `InputSystem : System(priority = EARLY)` that converts raw inputs to gameplay state
  - **Features**:
    - Polls `IInputProvider` for gamepad and keyboard inputs
    - Applies deadzone handling for analog sticks (configurable thresholds)
    - Implements jump state machine (pressed → held → released)
    - Writes gameplay state to `InputStateComponent` on player entities
    - Keyboard input overrides gamepad for movement
  - **Input Mapping**:
    - Move: Left Stick / WASD
    - Jump: A Button / Space
    - Sprint: Left Trigger / Left Shift
    - Camera Look: Right Stick (mouse TODO)
  - **Impact**: High - Centralized input processing, clean ECS separation
  - **Status**: Created and compiles successfully

- [x] **A20.4: Update PlayerController** - `game/player/PlayerController.kt`:
  - **Removed** `IInputProvider` dependency and direct input polling
  - **Now reads** from `InputStateComponent` for gameplay input state
  - **Updated** `update()` method to use `inputState.moveDirection` and `inputState.sprintPressed`
  - **Updated** `handleJumping()` to use `inputState.jumpPressed` (one-frame pulse)
  - **Cleaned up** unused smoothing variables and imports
  - **Impact**: High - PlayerController focuses on physics, not input polling
  - **Status**: Completed and compiles successfully

- [x] **A20.5: Update KoinModule** - `app/KoinModule.kt`:
  - **Note**: `GamepadListener` naming already correct (no rename needed)
  - **Added** `InputSystem` singleton to `engineModule`
  - **Impact**: Medium - Wire up new input architecture
  - **Status**: Completed and compiles successfully

- [x] **A20.6: Update LevelEditorSceneInitializer** - `editor/LevelEditorSceneInitializer.kt`:
  - **Added** `InputSystem` injection and registration to scene systems
  - **Runs first** due to EARLY priority, before PlayerController
  - **Impact**: Medium - Integrate InputSystem into scene
  - **Status**: Completed and compiles successfully

### Execution Order After Refactor

1. **InputSystem** (EARLY) - Poll hardware, write InputStateComponent
2. **PlayerController** (DEFAULT) - Read InputStateComponent, apply physics
3. **PlayerStateManager** (DEFAULT) - Read physics state, update animation state
4. **Animator** (DEFAULT) - Read PlayerStateManager, select animation
5. **AnimationSystem** (DEFAULT) - Apply animation to skeleton

---

## Notes

- See CHANGELOG.md for completed v0.15 through v0.19 items
- Use the template above for new phase tasks
