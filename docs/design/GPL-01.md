# GPL-01 — Player/skate controller rewrite (design note)

Status: **awaiting design approval** (no rewrite code written).

## 1. Current state (problems)

- `PlayerController` (engine component) holds hardcoded tuning (`jumpImpulse=300`, `walkSpeed=2.5`, `runSpeed=7.5`, `rotationSpeed`, `takeOffTime`, ...), magic input thresholds (`0.15`, `0.65`, `0.42`) and logic in `update()` (marked `//TODO: move`). It mixes config, runtime state and behavior. `getDesiredSpeed` has swapped factors (`walkSpeed/2` on the run branch, `runSpeed` on the walk branch).
- `SkateboardController` is a bag of `*Pressed` booleans with no behavior; no system consumes it.
- `PlayerMotionSystem` (engine) subscribes to events and mutates controller fields; contains the state choice (`PlayerState` IDLE/WALKING/RUNNING/JUMPING/FALLING/LANDING), animation names (`"walking"`, `"hard landing"`...), speed thresholds (`0.1`, `5`, `0.15`) and motion math. It is on-foot, not skate.
- `game/player/PlayerState` is a sealed class of on-foot states only; `MotionData` is marked `//TODO: move` and is engine→game dependent (`PlayerController` imports `game.player`).
- Engine components import from `game/` (layering inversion). Event-driven fields (`jumpPressed`, `movementDirection`) duplicate `InputStateComponent`.
- Only test: `PlayerControllerTest`.

## 2. Goals

1. No hardcoded tuning: all values in a serializable, editor-editable config asset.
2. Reusable logic in `engine/`, skateboarding logic in `game/`.
3. State machine: IDLE, PUSH, RIDE, AIR, GRIND, MANUAL, BAIL, with unit-tested transitions (no window/GL).
4. Pure motion math, separated from physics/ECS so it can be tested headless.

## 3. Proposed architecture

### 3.1 Engine (`engine/`) — generic, game-agnostic

`engine/gameplay/statemachine/`
- `StateMachine<S : Any, C : Any>`: states registered with `onEnter/onUpdate/onExit` and an ordered list of `Transition(from, to, guard: (C) -> Boolean, priority)`. `update(ctx, dt)` evaluates guards of the current state by priority, runs exit/enter, tracks `timeInState`. No ECS/GL references; deterministic.
- `StateMachineListener` for animation/audio hooks (consumed by GPL-06).

`engine/gameplay/config/`
- `ControllerConfig` assets are plain `@Serializable` data classes loaded through the existing asset system (AST pipeline; asset type `controller_config`, extension `.ctrl.json`), referenced from components by asset handle/path. Editor exposes fields via the existing property inspector (annotations/reflection already used by components); a "Controller Config" asset entry is created from the project browser. Missing/invalid file → defaults + logged warning.

`engine/ecs/components/PlayerController.kt` (slimmed, **breaking**)
- Keeps only: `configRef` (asset reference) and `@Transient` runtime state (current state id, grounded flags, desired move). No tuning fields, no `update()` logic, no `game.*` imports.

`engine/ecs/systems/PlayerMotionSystem.kt` (slimmed, **breaking**)
- Thin driver: per frame builds a `ControllerInput` snapshot (move vector, camera basis, button edges) from `InputStateComponent`, queries ground contact via the physics abstraction (PHY-02), calls the controller's state machine, applies the returned `MotionCommand` (target velocity, yaw, impulse) to the body. No state names, no animation names, no thresholds.
- Drops the event subscriptions (`JumpPressed`, `Landing`, `Takeoff`, `MovementInput`); input is read once per frame from `InputStateComponent` (single source of truth). Grounded state comes from a ground probe, not events.

`engine/gameplay/motion/MotionMath.kt`: pure functions (`dampedSpeed`, `cameraRelativeDirection`, `yawFromQuat`, `turnTowards`) — fixes the swapped lerp factors, NaN-safe, unit-tested.

### 3.2 Game (`game/`) — skateboarding

`game/player/`
- `SkateState` enum: `IDLE, PUSH, RIDE, AIR, GRIND, MANUAL, BAIL` (replaces `PlayerState`).
- `SkateControllerConfig` (`@Serializable`): grouped sections
  - `push`: pushAccel, pushIntervalSec, maxPushSpeed
  - `ride`: friction/rolling drag, brakeDecel, turnRateAtSpeed curve (min/max speed → deg/s), carveLeanMax
  - `air`: ollieImpulse, popWindowSec (crouch time), airControlYawRate, gravityScale, landingToleranceAngleDeg
  - `grind`: snapDistance, minEntrySpeed, balanceDecay, maxGrindSec
  - `manual`: balanceGain, balanceDecay, bailAngleDeg, maxManualSec
  - `bail`: speedThreshold, ragdollImpulse, recoverySec
  - `input`: moveDeadZone, pushThreshold, sprint→push mapping
  - `animation`: state → clip-name map (so no clip names in code)
- `SkateContext` (per-frame input to the machine): speed, groundedness, ground normal/angle, input axes, button edges (ollie, manual, grind), nearGrindable, landing alignment, time in state.
- `SkateStateMachine`: builds the machine from config. Transitions (guards via config):
  - IDLE→PUSH push input & speed < pushMax; PUSH→RIDE speed ≥ rideEnter or push released; RIDE→PUSH push input & slow; PUSH/RIDE→IDLE speed < eps & no input
  - IDLE/PUSH/RIDE→AIR not grounded for > coyote time or ollie executed (after pop window); AIR→RIDE/IDLE on landing aligned within tolerance; AIR→BAIL on landing misaligned or too steep
  - AIR/RIDE→GRIND near grindable with entry speed; GRIND→AIR on jump/exit; GRIND→BAIL on balance lost
  - RIDE→MANUAL manual input while grounded; MANUAL→RIDE released; MANUAL→BAIL balance beyond bailAngle
  - BAIL→IDLE after recovery time
- `SkateboardController` (component, in `game/`): tricks/stance request state (`TrickType`, `Stance` already in `game/skateboard`), replacing the booleans with a small request queue consumed by the machine.
- `SkateMotionSolver`: per-state velocity/yaw commands from `MotionMath` + config.
- Registration: `game/` registers the `SkateState` machine and config type with the engine's controller hook at startup (`GameMain`), keeping `engine/` free of `game.*` imports.

### 3.3 Editor / assets

- Config asset type registered with the asset system; default config generated by `PrefabsGenerator` (`Skater` prefab references `default.skate.ctrl.json`).
- Inspector shows all config fields; edits go through the existing command pipeline (property-change command), and hot-reload re-reads the config (AST hot reload when available).
- Needs from other domains (will be reported as NEEDS): ECS-03 (component lifecycle/serialization of asset references), PHY-02 (ground probe / raycast API on `IPhysicsBody3D`), EDT (inspector support for nested config assets), AST (new asset type registration).

## 4. Breaking changes (human-approved)

1. `PlayerController` loses all tuning fields (`jumpImpulse`, `flickSensitivity`, `catchStrength`, `walkSpeed`, `runSpeed`, `rotationSpeed`, `takeOffTime`) and `update()`/`handleJumping()`; scenes/prefabs saved with them lose those values (migrate to config asset; defaults reproduce current tuning).
2. `PlayerController` runtime fields (`isJumping`, `jumpTimer`, `lastSpeed`, `motionData`, `jumpPressed`, `movementDirection`, ...) removed.
3. `PlayerMotionSystem` no longer subscribes to `JumpPressed`, `Landing`, `Takeoff`, `MovementInput` events and no longer drives `Animator` directly.
4. `PlayerState` (IDLE/WALKING/RUNNING/JUMPING/FALLING/LANDING) replaced by `SkateState` (IDLE/PUSH/RIDE/AIR/GRIND/MANUAL/BAIL); `Animator.currentState` type/usages change accordingly. On-foot walk/run states are dropped (on-foot is GPL-02).
5. `MotionData` removed.
6. `SkateboardController` moves from `engine/ecs/components` to `game/` (package change; serialization type name/`ComponentType` entry changes) and its boolean fields become a request queue.
7. Existing saved scenes/prefabs need re-generation or a one-time migration (version bump of the scene format for these components).
8. `PlayerControllerTest` is rewritten; quarantined `PlayerStateManagerTest` stays quarantined.

## 5. Testing plan (headless, JUnit 5)

- `StateMachineTest`: priority, enter/exit ordering, time-in-state.
- `SkateStateMachineTest`: every transition above plus negative cases (no transition without guard), coyote time, bail on misaligned landing, manual balance bail, BAIL recovery.
- `MotionMathTest`: speed damping (fixes swapped factors), camera-relative direction, NaN safety, yaw wrap.
- `SkateControllerConfigTest`: serialization round-trip, defaults equal legacy values, missing file fallback.
- Architecture guard: `engine/gameplay` and `engine/ecs/components` must not import `com.pafoid.skate.game.*` (new test in `architecture/`, only for the files I own).

## 6. Implementation steps (after approval)

1. Engine `StateMachine` + `MotionMath` + tests.
2. `SkateControllerConfig` + asset type + defaults + tests.
3. `SkateState`/`SkateStateMachine`/`SkateMotionSolver` + tests.
4. Slim `PlayerController`/`PlayerMotionSystem`; move `SkateboardController`; update `Skater` prefab, `Serializer`, `ComponentType`, `Engine`.
5. Editor inspector wiring, full `gradlew test`.

## 7. Open questions for approval

- Q1: Config as a standalone asset file (proposed) vs. inline component fields with a shared preset? Proposed: standalone asset so presets are shareable.
- Q2: Drop on-foot walk/run states (proposed; GPL-02 covers it) or keep as a fallback machine?
- Q3: Grind/manual need rail/ground-probe data; OK to stub `nearGrindable` via a tag query until PHY-02 lands?
