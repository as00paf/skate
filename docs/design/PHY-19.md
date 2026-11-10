# PHY-19 — Jolt-native ragdolls, vehicles and character controller (design note)

Status: **revision 2, awaiting human approval**. No implementation code has been written.

Task (GameEngine.md, Physics): "From PHY-17: optional Jolt `Ragdoll` (pose motors), `VehicleConstraint` and
`CharacterVirtual` modes (the last one with GPL). Design note first." Deps: PHY-17 (slices a–e done).

Revision 2 replaces the first draft, which the lead rejected because it was not grounded in the code. That draft
described a `skate-physics-jolt` module and `IRagdoll` / `IVehicle` / `ICharacterController` interfaces, and none of
them exist. It also called GPL-24 a movement system, but GPL-24 covers inventory, combat, interaction and
customization and is not involved here. The human decisions recorded on 2026-11-07 (section 9) are kept word for word.

All paths below are relative to `src/main/kotlin/com/pafoid/skate/` unless they start with `src/test/` or `docs/`.
**NEW** marks a file or type that does not exist yet.

## 1. How it works today

Nothing in this area is Bullet-specific. All three features are engine code built on `IPhysics3D`, and they already
run unchanged on Jolt (PHY-17, `docs/design/PHY-17.md` §4 "Ragdolls" / "Vehicles").

| Feature | Code today | Physics it uses |
|---|---|---|
| Ragdoll | `engine/physics3d/RagdollBuilder.kt` (`buildMixamoRagdoll`: 13 capsule bones, `ConeTwistConstraint3D` joints with identity frames and fixed ±0.15 m pivots), `engine/ecs/components/RagdollComponent.kt` (`boneBodies`, `joints`, `state`), `RagdollState.kt` (`ANIMATED`, `RAGDOLL`, `BLENDING`; `BLENDING` is a stub that falls back to `ANIMATED`), `engine/ecs/systems/RagdollSystem.kt` (kinematic sync from the animation pose, or writes physics poses back to the skeleton), `PhysicsSystem.syncRagdolls()` (adds joints, makes bones dynamic in `RAGDOLL`) | `createRigidBody`, `isKinematic`, `addConstraint(ConeTwistConstraint3D)`. Limp only, no motors. |
| Vehicle | `engine/ecs/components/VehicleComponent.kt` (`WheelConfig`, `WheelState`, `throttle`/`steering`/`brake`, spring/damper/force fields in N), `engine/physics3d/RaycastVehicleSolver.kt` (PHY-09), called from `PhysicsSystem.fixedUpdate` before `physics3d.step` | `raycastClosest`, `applyImpulse` on the chassis `RigidBody3D.rawBody` |
| Character | No controller in physics. `engine/ecs/components/PlayerController.kt` + `engine/gameplay/LocomotionBehavior.kt` produce a `CharacterMotion` (horizontal velocity, jump impulse). `engine/ecs/systems/PlayerMotionSystem.kt` writes it to the object's dynamic `RigidBody3D` (as `IPhysicsBody3D`) and snaps it to the ground with `GroundMotion`. `PhysicsSystem.probeGround()` fills `PlayerController.ground` / `groundTag` / `stepAhead` with `GroundDetector` (`engine/gameplay/ground/GroundDetection.kt`) raycasts and a `shapeCast`. | A dynamic capsule `RigidBody3D`, `raycast`, `shapeCast` |

The Jolt backend lives in `engine/physics3d/jolt/`: `JoltPhysics3D` (world, `step` = `system.update` then
`contacts.afterStep`), `JoltJoints` (descriptor → Jolt constraint; `ConeTwistConstraint3D` →
`SwingTwistConstraintSettings`), `JoltRigidBodyHandle`, `JoltShapes`, `JoltLayers`, `JoltContacts`, `JoltDebugDraw`,
`JoltRuntime`. `ownedNativeObjects()` and `JoltLifecycleTest` check that every native object is freed.

The relevant jolt-jni 6.2.0 API, checked in the jar:
- `SwingTwistConstraint.setSwingMotorState` / `setTwistMotorState(EMotorState)`, `setTargetOrientationCs(Quat)`,
  `getSwingMotorSettings` / `getTwistMotorSettings` (`MotorSettings`).
- `VehicleConstraint(Settings)` with `WheeledVehicleControllerSettings` and `WheelSettingsWv`
  (`setPosition`, `setRadius`, `setSuspensionMin/MaxLength`, `setMaxSteerAngle`, `setMaxBrakeTorque`).
  Driven with `WheeledVehicleController.setDriverInput(forward, right, brake, handBrake)`; read with
  `getWheel(i)` and `getWheelWorldTransform(i, right, up)`. It needs `PhysicsSystem.addStepListener`.
- `CharacterVirtual(Settings)` with `CharacterVirtualSettings` (`setMaxSlopeAngle`, `setInnerBodyShape`, `setMass`,
  `setMaxStrength`). Driven with `extendedUpdate(dt, gravity, ExtendedUpdateSettings, filters…, TempAllocator)`,
  `setLinearVelocity`, `setShape` (crouch), `getGroundState`, `getGroundNormal`, `isSupported`, and
  `CharacterContactListener`.

## 2. Principles

1. **Opt-in per component, and the engine-side API stays the same.** The current behaviour stays the default on every
   backend: limp ragdolls, the raycast vehicle solver, and the rigid-body character. Jolt-native modes are a field on
   the component. When the backend cannot do the mode, the engine uses today's code path and logs a warning once.
2. **No Jolt types outside `engine/physics3d/jolt/`.** New API on `IPhysics3D` uses neutral descriptors and handles,
   following the existing pattern (`IPhysicsConstraint` descriptors with `rawJoint`, and `IRigidBodyHandle`). New
   methods have defaults that mean "unsupported", so `BulletPhysics3D` and `SimplePhysics3D` compile unchanged.
3. **Backends are still built only through `Physics3DFactory`.** Bullet stays the default backend (PHY-17 decision 3).
   Per-project selection remains PHY-17 slice f, which waits on EDT-59, and is not part of this task.
4. **Fixed timestep.** Native vehicles and characters advance inside `IPhysics3D.step` (Jolt step listener /
   `extendedUpdate` before `system.update`), so `PhysicsSystem`'s interpolation keeps working.
5. **Leak-safe.** Every new native object is listed in `JoltPhysics3D.ownedNativeObjects()` and freed in `destroy()`.
   `JoltLifecycleTest` gets a ragdoll with motors, a vehicle and a character in its scene.

## 3. Ragdoll — pose motors (human decision 1: no Jolt built-in `Ragdoll`)

### 3.1 API (backend-neutral)

- `engine/physics3d/constraints/ConeTwistConstraint3D.kt`: add `var motor: JointMotor? = null` (default null = limp,
  as today).
- **NEW** `engine/physics3d/constraints/JointMotor.kt`: `data class JointMotor(var frequency: Float = 20f,
  var damping: Float = 1f, var maxTorque: Float = Float.MAX_VALUE)` (spring-style motor tuning).
- `engine/physics3d/IPhysics3D.kt`: add
  `fun setJointMotorTarget(constraint: IPhysicsConstraint, targetInConstraintSpace: Quaternionf): Boolean = false`
  and `val supportsJointMotors: Boolean get() = false`.

### 3.2 Jolt

- `jolt/JoltJoints.kt`: when `ConeTwistConstraint3D.motor != null`, set the swing and twist `MotorSettings`
  (spring frequency/damping, torque limits) on the `SwingTwistConstraintSettings`.
- `jolt/JoltPhysics3D.kt`: `setJointMotorTarget` sets both motor states to `EMotorState.Position` and calls
  `setTargetOrientationCs`. `null` motor or an unknown constraint returns false.

### 3.3 Engine

- `RagdollState.kt`: add `POWERED` (bones are dynamic, and joint motors pull them toward the animation pose).
  `BLENDING` keeps its current stub behaviour (see open question 1).
- `RagdollComponent.kt`: add `var motorStrength: Float = 1f` (scales `JointMotor.maxTorque`; serialised, so it shows
  in the inspector through `reflectedImgui`).
- `RagdollSystem.kt`, `POWERED` branch: for each joint, the target is the child bone's rotation relative to its
  parent bone in the current animation pose (`pose.globalTransforms`), expressed in the joint's constraint space. The
  physics poses are then written back as in `RAGDOLL`. If `physics.supportsJointMotors` is false, `POWERED` behaves
  like `RAGDOLL`, with one warning.
- `PhysicsSystem.syncRagdolls()`: `POWERED` makes bones dynamic like `RAGDOLL`.
- `RagdollBuilder.kt`: give every joint a default `JointMotor` (unused unless `POWERED`). Constraint frames are
  identity today, so constraint space equals parent-bone space. Proper frames from the bind pose are a known gap: the
  builder comment says pivots are approximate. They are not fixed here (open question 4).

### 3.4 Tests (unit, Jolt-native where noted)

- `JoltJoints`: a cone-twist with a motor creates a `SwingTwistConstraint` with both motors configured.
- Jolt: two bodies joined by a motorised cone-twist, with a 45° twist target, reach the target within 0.05 rad in 120
  steps. With no motor they do not.
- Bullet/Simple: `setJointMotorTarget` returns false, and a `POWERED` ragdoll behaves like `RAGDOLL` (extends
  `src/test/.../ecs/systems/PhysicsSystemRagdollTest.kt`).
- `RagdollSystem`: targets computed from a two-bone pose equal the relative rotation.

## 4. Vehicle — Jolt `VehicleConstraint`

### 4.1 API (backend-neutral)

- `VehicleComponent.kt`: add `var mode: VehicleMode = VehicleMode.RAYCAST`. **NEW** enum
  `VehicleMode { RAYCAST, NATIVE }` in the same file. All existing fields keep their meaning in both modes. Nothing
  moves to new units (open question 2).
- **NEW** `engine/physics3d/VehicleHandle.kt`: `interface IVehicleHandle { fun setDriverInput(throttle: Float,
  steering: Float, brake: Float); fun readWheels(into: List<WheelState>); fun wheelWorldTransform(index: Int, out:
  Matrix4f): Matrix4f }`. This is a neutral handle in the style of `IRigidBodyHandle`.
- `IPhysics3D.kt`: add `fun createVehicle(chassis: IRigidBodyHandle, vehicle: VehicleComponent): IVehicleHandle? =
  null` and `fun removeVehicle(handle: IVehicleHandle) {}`.

### 4.2 Jolt (**NEW** `engine/physics3d/jolt/JoltVehicle.kt`)

Mapping from `VehicleComponent` to Jolt:

| Engine | Jolt |
|---|---|
| `WheelConfig.offset`, `radius` | `WheelSettingsWv.setPosition` (chassis space, already +Y up / +Z forward), `setRadius` |
| `suspensionRestLength` | `setSuspensionMaxLength` (min 0) |
| `springStiffness`, `springDamping` | Suspension `SpringSettings` as frequency/damping ratio, from the mass share per wheel: f = √(k/m)/2π, ζ = c / (2√(k·m)) |
| `maxSteerAngle` on `steerable` wheels | `setMaxSteerAngle` (0 on others) |
| `maxBrakeForce` | `setMaxBrakeTorque` = force share × radius |
| `maxEngineForce`, `driven` | `VehicleEngineSettings` max torque (force × radius); a differential over the driven wheels |
| `tyreFriction` | Flat longitudinal/lateral friction curves at that value |
| `throttle`, `steering`, `brake` | `WheeledVehicleController.setDriverInput(throttle, steering, brake, 0)` |

- The collision tester is `VehicleCollisionTesterRay` on the chassis layer, which matches the current ray behaviour.
- `JoltPhysics3D` keeps the constraint in its own list, calls `system.addConstraint` + `addStepListener`, and lists
  the constraint, settings and tester in `ownedNativeObjects()`. Removing the chassis body removes its vehicle first
  (as `destroyBody` already does for joints).
- `readWheels` fills `WheelState.grounded` / `compression` / `steerAngle` / `load` from `Wheel`, so audio and visuals
  that read `wheelStates` keep working.

### 4.3 Engine

`PhysicsSystem.fixedUpdate`, vehicle loop: in `NATIVE` mode, create the handle once per vehicle (identity map, like
`activeJoints`) and push `throttle` / `steering` / `brake` each step. If `createVehicle` returns null (Bullet/Simple),
use `RaycastVehicleSolver`, with one warning. Changing `mode` or the wheel list rebuilds the handle. `WheelState` gets
no new fields.

Human decision 2 (suspension parameters in the editor inspector): `VehicleComponent` has no custom drawer today. It
is drawn by `Component.reflectedImgui` (`editor/imgui/components/Component.kt`), which already lists
`suspensionRestLength`, `springStiffness`, `springDamping` and `maxSuspensionForce`, and will list `mode`. A grouped
"Suspension" section with tooltips and translations is slice PHY-19d (EDT).

### 4.4 Tests

- `JoltVehicle` mapping: the spring conversion round-trips, brake and engine torque scale with the radius, and only
  steerable wheels get a steer angle.
- Jolt `NATIVE`: a 4-wheel chassis on a plane settles with all wheels grounded and the chassis level within 2°. Full
  throttle for 2 s moves it forward. Braking from speed stops it. Steering right yaws it right. These mirror the
  scenarios in `src/test/.../physics3d/RaycastVehicleTest.kt`.
- Bullet: `NATIVE` falls back to the raycast solver (same result as `RAYCAST`).
- `JoltLifecycleTest`: the populated world includes a native vehicle; nothing leaks.

## 5. Character controller — Jolt `CharacterVirtual` (with GPL)

### 5.1 API (backend-neutral)

- **NEW** `engine/ecs/components/CharacterBody3D.kt` (PHY-owned). It implements `IPhysicsBody3D`, so
  `PlayerMotionSystem` can drive it the way it drives `RigidBody3D` today. It is used instead of `RigidBody3D` on an
  object, not in addition to it. Serialised fields (human decision 3: configurable capsule radius and height):
  `radius = 0.3f`, `height = 1.2f` (cylinder part, matching `ColliderShape.Capsule`), `crouchHeight = 0.6f`,
  `mass = 70f`, `maxSlopeAngle = 50°`, `stepHeight = 0.35f`, `maxStrength = 100f` (push force on dynamic bodies),
  `collisionLayer`, `collisionMask`. Runtime: `rawCharacter: ICharacterHandle?`, `crouching`, and read-only ground
  state.
- **NEW** `engine/physics3d/CharacterHandle.kt`: `interface ICharacterHandle { position/rotation get/set;
  linearVelocity get/set; val groundState: CharacterGround (NEW enum ON_GROUND, ON_STEEP_GROUND, NOT_SUPPORTED,
  IN_AIR); fun groundNormal(out: Vector3f); fun setCrouching(crouching: Boolean): Boolean }`. `setCrouching` returns
  false when standing up is blocked.
- `IPhysics3D.kt`: add `fun add(go)` support for `CharacterBody3D` (the same entry point as rigid bodies, so
  `PhysicsSystem`'s add/teleport/remove bookkeeping is reused). Add a `fun createCharacter(desc:
  CharacterDescription): ICharacterHandle?` hook. **NEW** `CharacterDescription` is in `engine/physics3d/` next to
  `BodyDescription.kt`.

### 5.2 Jolt (**NEW** `engine/physics3d/jolt/JoltCharacter.kt`)

- `CharacterVirtualSettings`: capsule shape (from `JoltShapes`), `setMaxSlopeAngle`, `setMass`, `setMaxStrength`,
  plus an inner body (`setInnerBodyShape` on the character's object layer). Raycasts, `shapeCast`, triggers and
  other bodies' contacts then see the character, like they see the rigid-body capsule today (open question 5).
- `JoltPhysics3D.step`: before `system.update`, call `extendedUpdate(dt, gravity, ExtendedUpdateSettings(stick to
  floor, walk stairs with stepHeight), filters from JoltLayers, tempAllocator)` for each character. Jolt does the
  sliding, step-up and slope limit.
- Crouch: `setShape(crouch capsule, maxPenetration, …)`. If standing up would penetrate, the call returns false and
  the character stays crouched.
- A `CharacterContactListener` turns character-vs-trigger overlaps into `JoltContacts` trigger events, so
  `TriggerEntered` / `TriggerExited` (`engine/events/PhysicsEvents.kt`) still fire.
- Debug draw: `JoltDebugDraw` draws the capsule with the existing primitives.

### 5.3 Fallback on Bullet / Simple

`createCharacter` returns null. `PhysicsSystem` then builds what characters use today: a dynamic capsule body
(`createRigidBody(ColliderShape.Capsule(radius, height, 1), mass)`, angular factor 0). It is wrapped in a small
**NEW** `RigidBodyCharacter` adapter (`engine/physics3d/RigidBodyCharacter.kt`) that implements `ICharacterHandle`, so
one component works on every backend. Crouch on the fallback rebuilds the capsule. Step-up and slope limit are not
done on the fallback; GPL's existing `GroundMotion` keeps doing them.

### 5.4 Gameplay integration (GPL)

- `engine/ecs/systems/PlayerMotionSystem.kt` (GPL): query `PlayerController` with `RigidBody3D` **or**
  `CharacterBody3D`, and drive both through `IPhysicsBody3D`. On a native character, the jump impulse becomes a
  velocity change (impulse / mass). `applyGround` (`GroundMotion` snapping) is skipped, because `extendedUpdate`
  already sticks to the floor and walks stairs. `CharacterInput.crouch` sets `CharacterBody3D.crouching`.
- `PhysicsSystem.probeGround()` (PHY): with a native character, `PlayerController.ground.standing` comes from
  `groundState == ON_GROUND` and the normal from `groundNormal`. `GroundDetector` still runs for foot IK targets,
  `groundTag` and `stepAhead`, which `CharacterVirtual` does not provide.
- Existing prefabs keep `RigidBody3D`: the skater (`game/prefabs/Skater.kt`, mount/ride logic, net prediction in
  `game/net/SkaterPredictionStep.kt`) and the showcase character. Moving any of them is a separate GPL task
  (open question 6).

### 5.5 Tests

- Jolt: a character on a plane stays grounded (`ON_GROUND`). Walking into a 0.3 m step climbs it, and a 0.5 m wall
  does not. A 60° slope reports `ON_STEEP_GROUND` with max 50°. Crouch then stand under a 1 m ceiling stays crouched.
  A trigger volume fires enter/exit. Pushing a 10 kg box moves it.
- Fallback: on Bullet, `CharacterBody3D` creates a capsule body with angular factor 0, and `ICharacterHandle`
  velocity round-trips.
- GPL: `PlayerMotionSystem` drives a `CharacterBody3D` object (walk speed reached, jump leaves the ground).
- `JoltLifecycleTest`: the populated world includes a character; nothing leaks.
- Serialization: `CharacterBody3D` round-trips through `engine/assets/serialization/Serializer.kt` (registered as a
  subclass, `ComponentType` entry), and `rawCharacter` is not saved.

## 6. Ownership of touched files

| File | Owner | Change |
|---|---|---|
| `engine/physics3d/IPhysics3D.kt`, `constraints/ConeTwistConstraint3D.kt`, **NEW** `constraints/JointMotor.kt`, **NEW** `VehicleHandle.kt`, **NEW** `CharacterHandle.kt`, **NEW** `CharacterDescription.kt`, **NEW** `RigidBodyCharacter.kt` | PHY | Neutral API |
| `engine/physics3d/jolt/JoltPhysics3D.kt`, `JoltJoints.kt`, `JoltContacts.kt`, `JoltDebugDraw.kt`, **NEW** `JoltVehicle.kt`, **NEW** `JoltCharacter.kt` | PHY | Jolt implementation |
| `engine/ecs/systems/PhysicsSystem.kt`, `RagdollSystem.kt`, `engine/ecs/components/RagdollComponent.kt`, `RagdollState.kt`, `VehicleComponent.kt`, **NEW** `CharacterBody3D.kt`, `engine/physics3d/RagdollBuilder.kt` | PHY | Engine glue |
| `engine/ecs/components/ComponentType.kt`, `Component.kt` (type mapping), `engine/EngineExtensions.kt`, `engine/assets/serialization/Serializer.kt` | ECS / AST | Register `CharacterBody3D` (one line each) |
| `engine/ecs/systems/PlayerMotionSystem.kt` | GPL | Drive `CharacterBody3D` |
| `editor/imgui/components/` (optional grouped drawers) | EDT | Inspector sections |
| `game/samples/FeatureShowcase.kt` | TLG | Showcase entries |
| `src/test/kotlin/com/pafoid/skate/architecture/` | QA | A guard: only `engine/physics3d/jolt/` may import `com.github.stephengold.joltjni` (it is not enforced today; the Bullet guard in `EngineLayeringGuardTest` is) |

## 7. Slicing

Each slice is one task with its own tests and lands green on its own. Slices a–c and e do not depend on each other.

| Slice | Owner | Content | Deps |
|---|---|---|---|
| **PHY-19a** Joint motors | PHY | `JointMotor`, `ConeTwistConstraint3D.motor`, `IPhysics3D.setJointMotorTarget` / `supportsJointMotors`, Jolt `SwingTwistConstraint` motors, Bullet/Simple unsupported (§3.1–3.2) | — |
| **PHY-19b** Powered ragdoll | PHY (ANI reviews the pose maths) | `RagdollState.POWERED`, `motorStrength`, targets in `RagdollSystem`, default motors in `RagdollBuilder`, fallback to `RAGDOLL` (§3.3) | 19a |
| **PHY-19c** Native vehicle | PHY | `VehicleMode`, `IVehicleHandle`, `JoltVehicle`, `PhysicsSystem` mode switch + fallback, `WheelState` readback (§4) | — |
| **PHY-19d** Inspector sections | EDT | Grouped "Suspension" (vehicle) and "Capsule" (character) sections with tooltips and translation keys; `reflectedImgui` covers the fields until then (human decisions 2 and 3) | 19c, 19e |
| **PHY-19e** Character body | PHY (ECS/AST one-line registrations) | `CharacterBody3D`, `ICharacterHandle`, `CharacterDescription`, `JoltCharacter`, `RigidBodyCharacter` fallback, trigger events, debug draw, `probeGround` ground state (§5.1–5.3) | — |
| **PHY-19f** Gameplay on the character body | GPL | `PlayerMotionSystem` drives `CharacterBody3D`, skips `GroundMotion` on native characters, crouch (§5.4) | 19e |
| **PHY-19g** Showcase | TLG | A powered ragdoll, a native-mode vehicle and a `CharacterBody3D` walker in the Feature Showcase, shown when the project runs on Jolt | 19b, 19c, 19f; PHY-17 slice f for switching backend |
| **QA guard** | QA | joltjni import guard (§6) | — |

## 8. Risks

- **Motor stability**: stiff motors on light bones (0.05 × 70 kg = 3.5 kg forearms) can jitter. Defaults are a
  20 Hz / critically damped spring, and torque is limited by `motorStrength`. Tuning is per project.
- **Ragdoll frames**: identity constraint frames and fixed pivots in `RagdollBuilder` make motor targets only
  approximately anatomical. This is acceptable for "keep the pose" and wrong for precise limits (open question 4).
- **Vehicle tuning drift**: the same numbers will not feel identical in `RAYCAST` and `NATIVE` modes (Jolt models
  engine RPM, a differential and friction curves). The tests check behaviour (settles, accelerates, brakes, turns),
  not equal trajectories.
- **Character and networking**: `CharacterVirtual` is not part of the rigid-body world state that net prediction
  replays. Characters that are predicted over the network stay on `RigidBody3D` until NET reviews this.
- **Platform**: only `jolt-jni-Windows64` is on the classpath today (`NativeLibrarySpec.JOLT`), the same constraint
  as PHY-17. On another OS every mode falls back.

## 9. Decisions

Human decisions recorded on 2026-11-07:

1. **Jolt built-in Ragdoll**: No — stick with pose motors only (does not match the engine's existing API).
2. **Vehicle suspension in editor**: Yes — expose suspension parameters in the editor inspector.
3. **Character controller collision shapes**: Yes — configurable capsule radius and height.

Already decided elsewhere and assumed here: Bullet stays the default backend, and per-project selection is PHY-17
slice f, which waits on EDT-59 (PHY-17 "Decisions after review" 3).

## 10. Open questions for the human

1. **Powered ragdoll state.** Add a new `RagdollState.POWERED` and leave `BLENDING` as it is, or implement `BLENDING`
   with motors (blend from physics back to animation)? *Recommended:* add `POWERED` now. File animation↔physics
   blending as a separate ANI+PHY task, because it needs animation-side cross-fade work.
2. **Vehicle units in `NATIVE` mode.** Keep one set of `VehicleComponent` fields (forces in N, converted to Jolt
   torques and spring frequencies), or add Jolt-specific fields (engine RPM/torque curve, gearbox, anti-roll bars)?
   *Recommended:* one set of fields now, converted. Add a separate `NativeVehicleTuning` block later if a game needs
   gears or anti-roll bars.
3. **Joint motors on Bullet.** Leave them unsupported on Bullet (`POWERED` falls back to limp), or also implement
   them with libbulletjme `New6Dof` motors? *Recommended:* Jolt only for now. Bullet would need the ragdoll joints
   rebuilt as 6-DoF joints, and Bullet stays the default mostly for compatibility.
4. **Ragdoll frames from the bind pose.** Fix `RagdollBuilder` now (joint frames and pivots from the skeleton bind
   pose, capsule sizes from bone lengths), or later? *Recommended:* a separate PHY task right after 19b. Motor
   targets work with today's identity frames, but accurate limits and nicer poses need real frames.
5. **Character inner body.** Give `CharacterVirtual` an inner rigid body so raycasts, shape casts, triggers and
   other bodies see it? *Recommended:* yes. Without it, `probeGround` of other characters, trigger zones and AI line
   of sight would ignore Jolt characters.
6. **Existing characters.** Move the showcase walker and the skater to `CharacterBody3D`? *Recommended:* no.
   `CharacterBody3D` stays opt-in for new characters. The skater keeps `RigidBody3D`, because its board mount and net
   prediction depend on it. Revisit the walker after 19f ships.
