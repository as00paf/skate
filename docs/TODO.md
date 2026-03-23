# 🛹 SkateSim Engine - TODO & Roadmap

## Current Status: ECS Architecture Complete ✅

The ECS architecture is now 100% complete through v0.42.
All systems follow proper component-based patterns with no direct engine coupling in gameplay logic.

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture documentation.

---

## 🔴 v0.43: EventSystem Implementation (Planned)

### Summary

Implement a centralized EventSystem to decouple systems and components, reducing tight coupling and improving
maintainability.
Currently, many systems directly query other components/systems, creating hidden dependencies and making testing
difficult.

### Current Architecture Issues

**Tight Coupling Identified:**

1. **Input → Gameplay Coupling** ❌
  - `PlayerController` directly reads `InputStateComponent` every frame
  - `PlayerController` directly queries `SceneManager`, `Camera`, `Physics3D`
  - No way to listen for input events without polling

2. **Physics → Gameplay Coupling** ❌
  - `TrickDetector` polls `PhysicsComponent.angularVelocity` every frame
  - `PlayerController` polls `isGrounded` state every frame
  - No events for physics state changes (landing, takeoff, collision)

3. **SkateboardPhysics → Game Logic Coupling** ❌
  - `isGrounded` state polled by multiple systems (`TrickDetector`, `TrickAnalyzer`, `PlayerController`)
  - No event when skateboard lands or takes off
  - Trick detection relies on polling `isGrounded` instead of events

4. **Component → Component Direct Access** ❌
  - `PlayerController` accesses `PlayerStateManager` via `getComponent<>()`
  - `TrickDetector` accesses `PlayerStateManager` via `getComponent<>()`
  - `Animator` accesses `PlayerStateManager` via `getComponent<>()`

5. **No Event-Driven Trick System** ❌
  - `TrickDetector` detects tricks via polling
  - `TrickManager` not notified of trick events
  - `TrickUIWindow` polls `TrickDetector` every frame

### Proposed EventSystem Architecture

**Design Decision: Sealed Classes + Data Classes with String Event Names**

This hybrid approach provides type safety for Kotlin code while enabling future scripting integration.

```kotlin
// Base event type with string name for scripting
sealed class GameEvent(val eventName: String) {
  // Input events
  data class JumpPressed(val force: Float) : GameEvent("input.jump_pressed")
  data class MovementInput(val direction: Vector2f, val magnitude: Float) : GameEvent("input.movement")

  // Physics events
  data class Landing(val velocity: Vector3f, val impactForce: Float) : GameEvent("physics.landing")
  data class Takeoff(val velocity: Vector3f) : GameEvent("physics.takeoff")
  data class GroundedStateChanged(val isGrounded: Boolean) : GameEvent("physics.grounded_changed")

  // Trick events
  data class TrickDetected(val trickName: String, val rotation: Vector3f) : GameEvent("trick.detected")
  data class TrickCompleted(val trickName: String, val score: Int) : GameEvent("trick.completed")
}

// EventSystem supports BOTH type-safe and string-based subscriptions
class EventSystem : System() {
  // Type-safe for Kotlin code
  inline fun <reified T : GameEvent> subscribe(noinline listener: (T) -> Unit)

  // String-based for scripting (TypeScript, etc.)
  fun subscribe(eventName: String, listener: (GameEvent) -> Unit)

  // Publish works for both
  fun publish(event: GameEvent)
}
```

**Usage Examples:**

```kotlin
// Kotlin code (type-safe)
class TrickDetector : Component() {
  override fun start() {
    eventSystem.subscribe<TrickDetected> { event ->
      logger.log("Trick: ${event.trickName}, Rotation: ${event.rotation}")
    }

    eventSystem.subscribe<Landing> { event ->
      handleLanding(event.velocity, event.impactForce)
    }
  }
}
```

```typescript
// Future TypeScript scripting (string-based)
eventSystem.subscribe("trick.detected", (event) => {
    logger.log(`Trick: ${event.trickName}, Rotation: ${event.rotation}`);
});

eventSystem.subscribe("physics.landing", (event) => {
    handleLanding(event.velocity, event.impactForce);
});
```

**Event Namespace Convention:**

- `input.*` - Input events (jump_pressed, movement, trick_input)
- `physics.*` - Physics events (landing, takeoff, grounded_changed, collision)
- `trick.*` - Trick events (detected, completed, cancelled)
- `game.*` - Game state events (state_changed, score_changed)
- `ui.*` - UI events (button_clicked, menu_opened)

**Why This Approach:**

| Feature             | Sealed + Data Classes       | String Names Only         |
|---------------------|-----------------------------|---------------------------|
| **Type Safety**     | ✅ Compile-time checking     | ❌ Runtime errors on typos |
| **IDE Support**     | ✅ Autocomplete, refactoring | ❌ No autocomplete         |
| **Event Data**      | ✅ Strongly typed properties | ❌ Need Map/Dictionary     |
| **Scripting**       | ✅ Via `eventName` property  | ✅ Native support          |
| **Exhaustive When** | ✅ `when(event)` checks      | ❌ Not possible            |
| **Performance**     | ✅ No string parsing         | ❌ String lookups          |

### Tasks

- [x] **A43.0.1: Create EventSystem core infrastructure** ✅
  - Location: `engine/ecs/systems/EventSystem.kt` (new)
  - Create `GameEvent` sealed class with `eventName: String` property
  - Create `EventSystem` with dual subscribe API (type-safe + string-based)
  - Support one-time and persistent listeners
  - Support event priority (for ordering)
  - Support event cancellation (listeners can prevent further processing)
  - **Status**: Complete - EventSystem created with full functionality ✅

- [x] **A43.0.2: Create input event types** ✅
  - Location: `engine/events/InputEvents.kt` (new)
  - Sealed class `InputEvent : GameEvent`
  - `JumpPressed(val force: Float)` : "input.jump_pressed"
  - `JumpReleased` : "input.jump_released"
  - `MovementInput(val direction: Vector2f, val magnitude: Float)` : "input.movement"
  - `TrickInput(val trickType: TrickType, val isPressed: Boolean)` : "input.trick"
  - `CameraLook(val delta: Vector2f)` : "input.camera_look"
  - **Status**: Complete - All input events created ✅

- [x] **A43.0.3: Create physics event types** ✅
  - Location: `engine/events/PhysicsEvents.kt` (new)
  - Sealed class `PhysicsEvent : GameEvent`
  - `Landing(val velocity: Vector3f, val impactForce: Float)` : "physics.landing"
  - `Takeoff(val velocity: Vector3f)` : "physics.takeoff"
  - `GroundedStateChanged(val isGrounded: Boolean)` : "physics.grounded_changed"
  - `Collision(val other: GameObject, val contactPoint: Vector3f, val normal: Vector3f)` : "physics.collision"
  - **Status**: Complete - All physics events created ✅

- [x] **A43.0.4: Create trick event types** ✅
  - Location: `engine/events/TrickEvents.kt` (new)
  - Sealed class `TrickEvent : GameEvent`
  - `TrickDetected(val trickName: String, val rotation: Vector3f)` : "trick.detected"
  - `TrickCompleted(val trickName: String, val score: Int, val style: Float)` : "trick.completed"
  - `TrickCancelled(val reason: String)` : "trick.cancelled"
  - **Status**: Complete - All trick events created ✅

- [x] **A43.0.5: Update InputSystem to publish events** ✅
  - Location: `engine/ecs/systems/InputSystem.kt`
  - Publish `JumpPressed` when jump button pressed (with force value)
  - Publish `MovementInput` when movement input changes
  - Publish `TrickInput` for trick inputs (flip, kickflip, heelflip, grab, manual)
  - Keep `InputStateComponent` for polling-based systems (backward compatibility)
  - **Status**: Complete - InputSystem now publishes events ✅

- [x] **A43.0.6: Update PhysicsSystem to publish events** ✅
  - Location: `engine/ecs/systems/PhysicsSystem.kt`
  - Simplified to focus on syncing physics state only
  - Landing/Takeoff events published by SkateboardPhysics instead
  - **Status**: Complete - PhysicsSystem simplified, events handled by SkateboardPhysics ✅

- [x] **A43.0.7: Update SkateboardPhysics to publish events** ✅
  - Location: `game/skateboard/SkateboardPhysics.kt`
  - Publish `Landing` when landing detected (with impact force)
  - Publish `Takeoff` when takeoff detected (with velocity)
  - Publish `GroundedStateChanged` on state change
  - **Status**: Complete - SkateboardPhysics publishes physics events ✅

- [x] **A43.0.8: Update TrickDetector to use events** ✅
  - Location: `game/trick/TrickDetector.kt`
  - Subscribe to `Landing` and `Takeoff` events instead of polling
  - Publish `TrickDetected` when trick detected (with trick name, rotation)
  - Publish `TrickCompleted` when trick successfully landed (with score, style)
  - **Status**: Complete - Event-driven trick detection ✅

- [x] **A43.0.9: Update PlayerController to use events** ✅
  - Location: `game/player/PlayerController.kt`
  - Subscribe to `JumpPressed` event instead of polling `InputStateComponent`
  - Subscribe to `Landing`/`Takeoff` events instead of polling `isGrounded`
  - Subscribe to `MovementInput` for movement direction
  - Reduce direct component queries (hybrid approach for backward compatibility)
  - **Status**: Complete - Event-driven player controller ✅

- [x] **A43.0.9b: Fix Animator component coupling** ✅
  - Location: `engine/ecs/components/Animator.kt`
  - Removed direct `PlayerStateManager` access via `getComponent<>()`
  - Subscribe to `MovementInput` event to determine walk/run state
  - Subscribe to `JumpPressed`/`Landing`/`Takeoff` events for jump/fall/landing states
  - Event-driven animation selection with state tracking
  - Fallback to PlayerStateManager if events not received (hybrid approach)
  - Added EventSystem to LevelEditorSceneInitializer
  - **Status**: Complete - Animator now uses events with fallback ✅

- [x] **A43.0.10: Update TrickUIWindow to use events** ✅
  - Location: `editor/windows/TrickUIWindow.kt`
  - Subscribe to `TrickCompleted` event instead of polling `TrickDetector`
  - Update UI with fade effect when trick completed
  - **Status**: Complete - Event-driven UI updates ✅

- [ ] **A43.0.11: Add event system unit tests**
  - Location: `test/.../ecs/systems/EventSystemTest.kt`
  - Test subscribe/unsubscribe functionality
  - Test event publishing to multiple listeners
  - Test event priority ordering
  - Test one-time vs persistent listeners
  - **Impact**: High - Ensure event system reliability

- [ ] **A43.0.12: Add integration tests for event-driven systems**
  - Location: `test/.../game/`
  - Test input event flow (InputSystem → PlayerController)
  - Test physics event flow (PhysicsSystem → TrickDetector)
  - Test trick event flow (TrickDetector → TrickManager → TrickUIWindow)
  - **Impact**: High - Ensure end-to-end event flow works

---

## 🔵 Future: Additional Improvements (Planned)

### v0.44: Code Quality & Performance

- [ ] **A44.0.1: Audit and replace remaining `!!` operators**
  - Use safe calls (`?.`) and Elvis operator (`?:`)
  - Add proper null checks with meaningful error messages
  - **Impact**: Medium - Improve code safety

- [ ] **A44.0.2: Review resource management for memory leaks**
  - Check for unclosed resources in asset loading
  - Review texture/model disposal on scene change
  - Add resource tracking and leak detection
  - **Impact**: High - Prevent memory leaks

- [ ] **A44.0.3: Optimize object allocation in hot loops**
  - Profile and identify high-allocation code paths
  - Reuse Vector3f/Quaternionf objects where possible
  - Use object pooling for frequently allocated objects
  - **Impact**: Medium - Improve performance

- [ ] **A44.0.4: Increase test coverage for complex systems**
  - Target: 80% coverage for engine/ecs packages
  - Focus on AnimationSystem, PhysicsSystem, InputSystem
  - Add integration tests for system interactions
  - **Impact**: High - Improve code reliability

---

## Architecture Notes

### Current ECS Architecture (v0.42)

**Components (15 total):**

- Core: Transform, RenderComponent, RigidBody3D, PhysicsComponent
- Input: InputStateComponent, EditorInputStateComponent
- Animation: SkeletonComponent, Animator
- Environment: EnvironmentComponent, TimeComponent, LightingStateComponent, LightingComponent
- Editor: NonPickable, ModularTile, SpriteRenderer
- Special: Component (base class)

**Systems (12 total):**

- ECS Infrastructure: System, SystemManager, GameObjectManager
- Gameplay: InputSystem, PhysicsSystem, AnimationSystem, DayNightCycleSystem
- Environment: EnvironmentSystem, DirectionalLightSystem, GridLines
- Editor: GizmoSystem, MouseControls

**ECS Pattern Compliance: 100%** ✅
- ✅ All gameplay systems read from components (not engine directly)
- ✅ All physics state synced via PhysicsSystem
- ✅ All input state written by InputSystem
- ✅ All animation state managed by AnimationSystem
- ✅ All environment state in components
- ✅ Clean separation: Components = data, Systems = logic

### Future Event-Driven Architecture (v0.43+)

**Benefits:**

- Decoupled systems (no direct component queries)
- Testable systems (mock events instead of full ECS)
- Flexible reactions (multiple listeners for same event)
- Clear data flow (events document system interactions)

**Hybrid Approach:**

- EventSystem for cross-system communication
- Components for state storage
- Systems for state updates
- Polling still available for simple cases (backward compatibility)

---

## End of TODO
