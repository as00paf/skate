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

- [x] **A20.1: Rename JoystickListener to GamepadListener** - `engine/input/listeners/`:
  - Rename file: `JoystickListener.kt` → `GamepadListener.kt`
  - Update class name inside file
  - Update all imports and references
  - Update Koin module registration
  - **Impact**: Low - Better naming clarity

- [ ] **A20.2: Create InputStateComponent** - New `ecs/components/InputStateComponent.kt`:
  - Create `InputStateComponent : Component`
  - Add gameplay input properties:
    - `moveDirection: Vector2f` - Normalized movement direction
    - `jumpPressed: Boolean` - Jump button pressed this frame
    - `jumpHeld: Boolean` - Jump button currently held
    - `sprintPressed: Boolean` - Sprint button pressed
    - `cameraLook: Vector2f` - Right stick / mouse look delta
    - `isGrounded: Boolean` - Grounded state (synced from physics)
  - **Impact**: High - Clean separation between input and gameplay logic

- [ ] **A20.3: Create InputSystem** - New `ecs/systems/InputSystem.kt`:
  - Create `InputSystem : System(priority = EARLY)`
  - Inject `IInputProvider`
  - In `update()`:
    - Poll `IInputProvider` for raw inputs
    - Apply deadzone handling for analog sticks
    - Implement jump state machine (pressed → held → released)
    - Write gameplay state to `InputStateComponent` on player entities
  - **Impact**: High - Centralized input processing, consistent behavior

- [ ] **A20.4: Update PlayerController** - `game/player/PlayerController.kt`:
  - Remove `IInputProvider` dependency
  - Remove direct `getMovementVector()`, `buttonBeginPress()` calls
  - Read from `InputStateComponent` instead
  - Keep physics logic (applyMotion, handleJumping, handleGroundSnapping)
  - **Impact**: High - PlayerController focuses on physics, not input polling

- [ ] **A20.5: Update KoinModule** - `app/KoinModule.kt`:
  - Rename `JoystickListener` → `GamepadListener` in DI
  - Add `InputSystem` singleton with EARLY priority
  - Update `InputProvider` constructor if needed
  - **Impact**: Medium - Wire up new input architecture

- [ ] **A20.6: Update LevelEditorSceneInitializer** - `editor/LevelEditorSceneInitializer.kt`:
  - Add `InputSystem` to scene systems
  - Ensure it runs before PlayerController (EARLY priority)
  - **Impact**: Medium - Integrate InputSystem into scene

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
