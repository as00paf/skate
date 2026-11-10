# PHY-19 — Jolt-native ragdolls, vehicles and character controller (design note)

Status: **revision 3, APPROVED by the human on 2026-11-10** (answers to the open questions in §9). Implementation is
PHY-23 (P3), later. No implementation code has been written.

Task (GameEngine.md, Physics): "From PHY-17: optional Jolt `Ragdoll` (pose motors), `VehicleConstraint` and
`CharacterVirtual` modes (the last one with GPL). Design note first." Deps: PHY-17 (slices a–e done).

History:
- Revision 1 was rejected because it was not grounded in the code.
- Revision 2 rebuilt the note on the real `physics3d` API.
- Revision 3 applies the human's answers of 2026-11-10 to revision 2's open questions. They are recorded word for word
  in §9. The main changes: powered ragdolls and the full vehicle drivetrain work on **every** backend, and the
  Feature Showcase gets a walker on `CharacterBody3D`.

All paths below are relative to `src/main/kotlin/com/pafoid/skate/` unless they start with `src/test/` or `docs/`.
**NEW** marks a file or type that does not exist yet.

## 1. How it works today

Nothing in this area is Bullet-specific today. All three features are engine code built on `IPhysics3D`, and they
already run unchanged on Jolt (PHY-17, `docs/design/PHY-17.md` §4 "Ragdolls" / "Vehicles").

| Feature | Code today | Physics it uses |
|---|---|---|
| Ragdoll | `engine/physics3d/RagdollBuilder.kt` (`buildMixamoRagdoll`: 13 capsule bones, `ConeTwistConstraint3D` joints with identity frames and fixed ±0.15 m pivots), `engine/ecs/components/RagdollComponent.kt`, `RagdollState.kt` (`ANIMATED`, `RAGDOLL`, `BLENDING`; `BLENDING` is a stub that falls back to `ANIMATED`), `engine/ecs/systems/RagdollSystem.kt`, `PhysicsSystem.syncRagdolls()` | `createRigidBody`, `isKinematic`, `addConstraint`. Bullet builds a `ConeJoint` (`bullet/BulletPhysics3D.kt`), Jolt a `SwingTwistConstraint` (`jolt/JoltJoints.kt`). Limp only, no motors. |
| Vehicle | `engine/ecs/components/VehicleComponent.kt` (`WheelConfig`, `WheelState`, `throttle`/`steering`/`brake`, `maxEngineForce`/`maxBrakeForce` in N, spring/damper), `engine/physics3d/RaycastVehicleSolver.kt` (PHY-09: forces applied straight at the contact, no engine, gearbox or wheel spin), called from `PhysicsSystem.fixedUpdate` before `physics3d.step` | `raycastClosest`, `applyImpulse` on the chassis `RigidBody3D.rawBody` |
| Character | `engine/ecs/components/PlayerController.kt` + `engine/gameplay/LocomotionBehavior.kt` produce a `CharacterMotion`. `engine/ecs/systems/PlayerMotionSystem.kt` writes it to a dynamic `RigidBody3D` (as `IPhysicsBody3D`) and snaps it to the ground with `GroundMotion`. `PhysicsSystem.probeGround()` fills `PlayerController.ground` / `groundTag` / `stepAhead` using `GroundDetector` (`engine/gameplay/ground/GroundDetection.kt`). Only the skater (`game/prefabs/Skater.kt`) has a `PlayerController` today. | A dynamic capsule `RigidBody3D`, `raycast`, `shapeCast` |
| Showcase | `game/samples/FeatureShowcase.kt` has **no walking character**. "[Animation] Character" and "[Animation] Upper-body mask" (`animation()`, via `PrefabsGenerator.spawnAnimatedCharacter`) are animated meshes with no body or controller, and line 140 notes "the showcase has no player character; the camera is 'you'". | — |

Jolt backend: `engine/physics3d/jolt/` contains `JoltPhysics3D`, `JoltJoints`, `JoltRigidBodyHandle`, `JoltShapes`,
`JoltLayers`, `JoltContacts`, `JoltDebugDraw` and `JoltRuntime`. `ownedNativeObjects()` and `JoltLifecycleTest`
check that every native object is freed.

APIs, checked in the jars:
- jolt-jni 6.2.0:
  - `SwingTwistConstraint` motors: `setSwingMotorState` / `setTwistMotorState`, `setTargetOrientationCs`,
    `MotorSettings`.
  - `VehicleConstraint` with `WheeledVehicleControllerSettings` (`VehicleEngineSettings`,
    `VehicleTransmissionSettings`, `VehicleDifferentialSettings`), `VehicleAntiRollBar`, `WheelSettingsWv`, and
    `WheeledVehicleController.setDriverInput`.
  - `CharacterVirtual` / `CharacterVirtualSettings` (`setInnerBodyShape`, `setMaxSlopeAngle`, `extendedUpdate`,
    `setShape`, `getGroundState`).
- libbulletjme 22.0.3: `New6Dof(bodyA, bodyB, pivotA, pivotB, rotA, rotB, RotationOrder)`,
  `getRotationMotor(axis)`, and `RotationMotor.setServoEnabled` / `setSpringEnabled` / `setMotorEnabled` /
  `set(MotorParam.ServoTarget | Stiffness | Damping | MaxMotorForce, v)`.

## 2. Principles

1. **One component API, every backend.** Ragdoll motors and the vehicle drivetrain are engine features with one set
   of fields. Each backend implements them natively where it can (Jolt motors and `VehicleConstraint`, Bullet
   `New6Dof` servos). Otherwise the engine does it (`RaycastVehicleSolver` drivetrain on Bullet and Simple). The only
   documented exception: Simple has no joints, so `POWERED` stays limp there.
2. **No backend types outside the backend package.** Jolt stays in `engine/physics3d/jolt/` and Bullet
   (`com.jme3`) in `engine/physics3d/bullet/` (guard: `EngineLayeringGuardTest`). New `IPhysics3D` methods have
   "unsupported" defaults.
3. **Backends are still built only through `Physics3DFactory`.** Bullet stays the default backend (PHY-17 decision 3).
   Per-project selection remains PHY-17 slice f, which waits on EDT-59.
4. **Fixed timestep.** Motors, vehicles and characters advance inside `IPhysics3D.step` or the existing fixed-step
   vehicle loop, so `PhysicsSystem`'s interpolation keeps working.
5. **Leak-safe.** New native objects are listed in `JoltPhysics3D.ownedNativeObjects()` and freed in `destroy()`.
   `JoltLifecycleTest` gets a powered ragdoll, a vehicle and a character in its scene.
6. **Cross-backend tests.** Where the human asked for equal behaviour (powered ragdoll, vehicle), tests run the same
   scenario on each backend and compare measured outcomes within the tolerances in §8.

## 3. Ragdoll — pose motors on every backend (decisions 1 of 2026-11-07; 1, 3 and 4 of 2026-11-10)

### 3.1 API (backend-neutral)

- `engine/physics3d/constraints/ConeTwistConstraint3D.kt`: add `var motor: JointMotor? = null` (null = limp).
- **NEW** `engine/physics3d/constraints/JointMotor.kt`: `data class JointMotor(var frequency: Float = 20f,
  var damping: Float = 1f, var maxTorque: Float = Float.MAX_VALUE)`. These are spring-style terms, so both backends
  can derive their own gains from the same numbers.
- `engine/physics3d/IPhysics3D.kt`: add
  `fun setJointMotorTarget(constraint: IPhysicsConstraint, targetInConstraintSpace: Quaternionf): Boolean = false`
  and `val supportsJointMotors: Boolean get() = false`.

### 3.2 Jolt

- `jolt/JoltJoints.kt`: with a `motor`, configure the swing and twist `MotorSettings` (spring frequency/damping,
  torque limits) on the `SwingTwistConstraintSettings`.
- `jolt/JoltPhysics3D.kt`: `setJointMotorTarget` sets both motor states to `EMotorState.Position` and calls
  `setTargetOrientationCs`. `supportsJointMotors = true`.

### 3.3 Bullet (decision 3 of 2026-11-10)

- `bullet/BulletPhysics3D.kt`: every `ConeTwistConstraint3D` becomes a `New6Dof` instead of a `ConeJoint`:
  - Frames are `pivotA/B` and `rotA/B`, with `RotationOrder.XYZ`.
  - Twist (X) is limited to ±`twistSpan`, and the swing axes (Y, Z) to ±`swingSpan1` / ±`swingSpan2`. Translation
    is locked.
  - This applies to limp joints too, so ragdolls have one joint type on Bullet (approval answer 2).
- With a `motor`: on each rotation axis, `setMotorEnabled(true)`, `setServoEnabled(true)`, `setSpringEnabled(true)`.
  Stiffness and damping come from `frequency` / `damping` and the joint's effective inertia
  (k = I·(2πf)², c = 2ζ·I·2πf), and `MaxMotorForce = maxTorque`.
- `setJointMotorTarget` decomposes the target quaternion into XYZ Euler angles and sets `ServoTarget` on each axis.
  The middle axis of an XYZ `New6Dof` is limited to ±π/2 by Bullet. Ragdoll swing spans stay well below that
  (≤ 0.5 rad today), so the decomposition is unambiguous. `supportsJointMotors = true`.

### 3.4 Simple

`SimplePhysics3D` has no joints, so `supportsJointMotors` stays false, and `POWERED` behaves like `RAGDOLL` (limp)
with one warning.

### 3.5 Engine

- `RagdollState.kt`: add `POWERED` (decision 1 of 2026-11-10): bones are dynamic, and motors pull them toward the
  animation pose. `BLENDING` keeps its stub. Animation↔physics blending with motors is a **separate ANI+PHY task**.
- `RagdollComponent.kt`: add `var motorStrength: Float = 1f` (scales `JointMotor.maxTorque`; serialised).
- `RagdollSystem.kt`, `POWERED` branch: each joint's target is the child bone's rotation relative to its parent bone
  in the current animation pose (`pose.globalTransforms`), in constraint space. The physics poses are then written
  back as in `RAGDOLL`.
- `PhysicsSystem.syncRagdolls()`: `POWERED` makes bones dynamic, like `RAGDOLL`.
- `RagdollBuilder.kt`: give every joint a default `JointMotor`. The frames stay identity in this task.
  **Bind-pose frames are a separate PHY task, scheduled right after the motor slice** (decision 4 of 2026-11-10;
  §7).

### 3.6 Tests (unit)

- Per backend, a motorised two-body joint with a 45° twist target reaches it within the joint tolerance (§8) in 120
  steps. Without a motor it does not. Bullet and Jolt are measured with the same scenario and compared.
- Bullet: a `ConeTwistConstraint3D` is created as `New6Dof` with the expected limits. The Euler decomposition
  round-trips for targets inside the spans.
- Simple: `setJointMotorTarget` returns false, and a `POWERED` ragdoll behaves like `RAGDOLL` (extends
  `src/test/.../ecs/systems/PhysicsSystemRagdollTest.kt`).
- `RagdollSystem`: targets computed from a two-bone pose equal the relative rotation.

## 4. Vehicle — full drivetrain on every backend (decision 2 of 2026-11-07; 2 of 2026-11-10)

### 4.1 One field set (`VehicleComponent.kt`)

The model follows Jolt's wheeled-vehicle model, so Jolt maps 1:1. `RaycastVehicleSolver` implements the same model
for Bullet and Simple. **NEW** serialisable types live in `engine/ecs/components/VehicleComponent.kt` (or a **NEW**
`VehicleDrivetrain.kt` next to it):

| Group | Fields (units) |
|---|---|
| `WheelConfig` (extended) | `offset`, `radius`, `steerable`, `driven` (kept) + `width`, `inertia` (kg·m²), `angularDamping`, `maxBrakeTorque` (N·m), `maxHandBrakeTorque` (N·m), `suspensionMinLength` / `suspensionMaxLength` (m), `suspensionFrequency` (Hz), `suspensionDampingRatio`, `longitudinalFriction` / `lateralFriction` (curves: slip → friction coefficient, as point lists) |
| **NEW** `VehicleEngine` | `maxTorque` (N·m), `minRpm`, `maxRpm`, `torqueCurve` (normalised RPM → torque factor, point list), `inertia`, `angularDamping` |
| **NEW** `VehicleTransmission` | `mode` (`AUTO` / `MANUAL`), `gearRatios`, `reverseGearRatios`, `switchTime` (s), `clutchReleaseTime` (s), `switchLatency` (s), `shiftUpRpm`, `shiftDownRpm`, `clutchStrength` |
| **NEW** `VehicleDifferential` (list) | `leftWheel`, `rightWheel` (indices into `wheels`), `differentialRatio`, `leftRightSplit`, `limitedSlipRatio`, `engineTorqueRatio` |
| **NEW** `VehicleAntiRollBar` (list) | `leftWheel`, `rightWheel`, `stiffness` (N·m/rad) |
| Vehicle | `maxSteerAngle` (kept), `maxPitchRollAngle`; inputs (transient): `throttle`, `steering`, `brake` (kept) + `handBrake`, `shiftUp` / `shiftDown` (manual mode) |
| Read-back | `WheelState` (kept fields) + `angularVelocity`, `rotationAngle`, `longitudinalSlip`, `lateralSlip`; `VehicleComponent.engineRpm`, `currentGear` (transient, for HUD/audio) |

Defaults describe the current 4-wheel car (rear-wheel drive via one rear differential, front and rear anti-roll bars,
5 forward gears), so a new `VehicleComponent()` drives out of the box.

Fields removed: `maxEngineForce`, `maxBrakeForce`, `springStiffness`, `springDamping`, `maxSuspensionForce`,
`suspensionRestLength`, `tyreFriction`. They are dropped with **no migration** (approval answer 3): the serializer
uses `ignoreUnknownKeys = true`, so scenes saved with them load with the new defaults.

The `VehicleMode` from revision 2 is dropped: the backend decides. Jolt uses `VehicleConstraint`; Bullet and Simple
use the engine solver. There is no switch (approval answer 4): each project runs only on its own backend.

### 4.2 Jolt (**NEW** `engine/physics3d/jolt/JoltVehicle.kt`)

- `WheelConfig` maps to `WheelSettingsWv`. `VehicleEngine` maps to `VehicleEngineSettings`, and its torque curve to
  `LinearCurve`. `VehicleTransmission` maps to `VehicleTransmissionSettings`, `VehicleDifferential` to
  `VehicleDifferentialSettings`, and `VehicleAntiRollBar` to `VehicleAntiRollBar`.
- The collision tester is `VehicleCollisionTesterRay` on the chassis layer.
- Inputs go through `WheeledVehicleController.setDriverInput(throttle, steering, brake, handBrake)`. Manual shifts
  use the transmission's gear request.
- `JoltPhysics3D` calls `system.addConstraint` and `addStepListener`, lists all vehicle objects in
  `ownedNativeObjects()`, and removes a chassis's vehicle before its body.
- Read-back: `WheelState` and the RPM/gear are filled from `Wheel` and the controller after each step.

### 4.3 Bullet and Simple (`engine/physics3d/RaycastVehicleSolver.kt`, rewritten)

The solver keeps its contract: rays plus `applyImpulse` on the chassis, so it stays backend-agnostic. It implements
the same model as Jolt each fixed step:
1. **Suspension:** a ray per wheel between min and max length. Spring force comes from frequency and damping ratio
   (as Jolt: k = m·(2πf)², c = 2ζ·m·2πf with m = the chassis mass share), and the contact point/normal from the hit.
2. **Anti-roll bars:** the compression difference × `stiffness` is applied as opposite forces on the two wheels.
3. **Engine and gearbox:** engine RPM follows the driven wheels' angular velocity × gear ratio × differential ratio,
   clamped to [`minRpm`, `maxRpm`]. Torque = `torqueCurve(rpm)` × `maxTorque` × throttle. In auto mode it shifts at
   `shiftUpRpm` / `shiftDownRpm` with `switchTime` and `clutchReleaseTime`.
4. **Differentials:** torque is split by `engineTorqueRatio` and `leftRightSplit`, with a limited-slip cap.
5. **Wheels:** each wheel has angular velocity, integrated from drive, brake and tyre torque over `inertia`, with
   `angularDamping`.
6. **Tyres:** longitudinal slip from wheel surface speed vs ground speed, and lateral slip from slip angle. Friction
   comes from the two curves × suspension load, and the forces are applied at the contact (hub height, as today).

`WheelState` and RPM/gear are filled exactly like on Jolt. The implementation follows Jolt's published
`WheeledVehicleController` / `VehicleConstraint` behaviour, so the two agree within tolerance. Sub-stepping inside a
fixed step is not needed at 60 Hz for car-like inertias (risk §8).

### 4.4 Editor (decision 2 of 2026-11-07)

`VehicleComponent` is drawn by `Component.reflectedImgui` (`editor/imgui/components/Component.kt`), which shows plain
numeric fields only. The grouped Suspension / Engine / Transmission / Differentials / Anti-roll bars sections, list
editors and curve editors are slice PHY-19d (EDT).

### 4.5 Tests (unit, cross-backend)

- `JoltVehicle` mapping: every field reaches its Jolt setting (engine, transmission, differential, anti-roll bar,
  wheel).
- Solver unit tests: torque curve lookup; RPM from wheel speed and gear; auto shift points; differential split and
  limited slip; anti-roll force sign; tyre force vs slip from the curves.
- **Cross-backend scenarios**, each on Bullet (solver) and Jolt (native), compared within §8:
  - settle ride height and level
  - 0→50 km/h time on flat ground
  - top speed in 2nd gear
  - shift points (RPM at upshift)
  - braking distance from 50 km/h
  - steady-state body roll in a constant-radius turn with and without anti-roll bars
  - handbrake locks the rear wheels
- Simple runs the solver scenarios too (same code path as Bullet; checks that it works with ray-only queries).
- `RaycastVehicleTest` and `PhysicsBenchmarkTest` (vehicle scene) are updated to the new fields.
- A scene JSON with the old fields still loads (they are ignored) and gets the new defaults.

## 5. Character controller — `CharacterBody3D` (decision 3 of 2026-11-07; 5 and 6 of 2026-11-10)

### 5.1 API (backend-neutral)

- **NEW** `engine/ecs/components/CharacterBody3D.kt` (PHY-owned) implements `IPhysicsBody3D`, so
  `PlayerMotionSystem` drives it like `RigidBody3D`. It is used **instead of** `RigidBody3D` on an object.
  - Serialised (decision 3 of 2026-11-07): `radius = 0.3f`, `height = 1.2f`, `crouchHeight = 0.6f`, `mass = 70f`,
    `maxSlopeAngle = 50°`, `stepHeight = 0.35f`, `maxStrength = 100f`, `collisionLayer`, `collisionMask`.
  - Runtime: `rawCharacter: ICharacterHandle?`, `crouching`, and read-only ground state.
- **NEW** `engine/physics3d/CharacterHandle.kt`: `interface ICharacterHandle`. It has position/rotation and
  `linearVelocity`, `groundState: CharacterGround` (**NEW** enum `ON_GROUND`, `ON_STEEP_GROUND`, `NOT_SUPPORTED`,
  `IN_AIR`), `groundNormal(out)`, and `setCrouching(Boolean): Boolean`.
- **NEW** `engine/physics3d/CharacterDescription.kt` (next to `BodyDescription.kt`).
- `IPhysics3D.add(go)` accepts `CharacterBody3D`, so `PhysicsSystem`'s add/teleport/remove bookkeeping is reused.
  It goes through a `createCharacter(desc): ICharacterHandle? = null` hook.

### 5.2 Jolt (**NEW** `engine/physics3d/jolt/JoltCharacter.kt`)

- `CharacterVirtualSettings`: capsule (from `JoltShapes`), `setMaxSlopeAngle`, `setMass`, `setMaxStrength`.
- **Inner rigid body** (decision 5 of 2026-11-10): `setInnerBodyShape`, on the character's object layer and
  registered in `handles` like other bodies. It is visible to `raycast` / `raycastAll` / `shapeCast` (AI line of
  sight, other characters' `probeGround`), and it triggers `TriggerEntered` / `TriggerExited` through `JoltContacts`.
- `JoltPhysics3D.step` calls `extendedUpdate` for each character before `system.update`: stick to floor, walk stairs
  up to `stepHeight`, using `JoltLayers` filters and the world's `tempAllocator`.
- Crouch uses `setShape(crouch capsule)`. If standing up is blocked, the character stays crouched.
- A `CharacterContactListener` covers character-vs-trigger overlaps that the inner body misses (sensor-only bodies).
- Debug draw: `JoltDebugDraw` draws the capsule.

### 5.3 Bullet / Simple

`createCharacter` returns null. `PhysicsSystem` builds a dynamic capsule (angular factor 0, which is what characters
use today) and wraps it in **NEW** `engine/physics3d/RigidBodyCharacter.kt`, which implements `ICharacterHandle`.
Crouch rebuilds the capsule. Step-up and slope handling come from GPL's existing `GroundMotion`.

### 5.4 Gameplay (GPL, reviewed by skate-gameplay)

- `engine/ecs/systems/PlayerMotionSystem.kt`: query `PlayerController` with `RigidBody3D` **or** `CharacterBody3D`
  (through `IPhysicsBody3D`).
  - On a native character, the jump impulse becomes a velocity change (impulse / mass).
  - `applyGround` is skipped, because `extendedUpdate` already sticks to the floor and walks stairs.
  - `CharacterInput.crouch` sets `CharacterBody3D.crouching`.
- `PhysicsSystem.probeGround()` (PHY): on a native character, `ground.standing` and the normal come from
  `groundState` / `groundNormal`. `GroundDetector` still runs for foot IK, `groundTag` and `stepAhead`.
- **The skater keeps `RigidBody3D`** (decision 6 of 2026-11-10): board mount and net prediction
  (`game/net/SkaterPredictionStep.kt`).

### 5.5 Showcase walker (decision 6 of 2026-11-10)

There is no walker in the showcase today (§1), so this adds one. Adding a **NEW** object
"[Physics] Character controller" in `FeatureShowcase.kt` takes these pieces:
- the James model and bundled locomotion clips, through `PrefabsGenerator.spawnAnimatedCharacter`
- `PlayerController(LocomotionBehavior.ID)`, `CharacterBody3D` and `InputStateComponent`
- a small course next to it: a 0.3 m step, a 30° ramp, a 60° slope it cannot climb, a crouch tunnel, a pushable box
  and a trigger pad.

It gets input through a **"Take control" toggle only, with no autopilot** (approval answer 1). The toggle hands
WASD to the walker and switches to a follow camera; toggling again gives the free camera back.

### 5.6 Tests

- Jolt:
  - a character stays `ON_GROUND`
  - it climbs a 0.3 m step but not a 0.5 m wall
  - a 60° slope reports `ON_STEEP_GROUND` (max 50°)
  - it stays crouched under a 1 m ceiling
  - the inner body is hit by `raycast` and fires trigger enter/exit
  - pushing a 10 kg box moves it
- Bullet fallback: the capsule has angular factor 0, and `ICharacterHandle` velocity round-trips.
- GPL: `PlayerMotionSystem` drives a `CharacterBody3D` (walk speed reached, jump leaves the ground, crouch).
- `JoltLifecycleTest` includes a character, and nothing leaks.
- `CharacterBody3D` round-trips through `engine/assets/serialization/Serializer.kt`.
- `src/test/.../game/samples/FeatureShowcaseTest.kt`: the walker exists with `CharacterBody3D` and no `RigidBody3D`,
  and the course pieces are present.

## 6. Ownership of touched files

| File | Owner | Change |
|---|---|---|
| `engine/physics3d/IPhysics3D.kt`, `constraints/ConeTwistConstraint3D.kt`, **NEW** `constraints/JointMotor.kt`, **NEW** `CharacterHandle.kt`, **NEW** `CharacterDescription.kt`, **NEW** `RigidBodyCharacter.kt`, `RaycastVehicleSolver.kt` | PHY | Neutral API, engine drivetrain |
| `engine/physics3d/bullet/BulletPhysics3D.kt` | PHY | `New6Dof` joints with servos |
| `engine/physics3d/jolt/JoltPhysics3D.kt`, `JoltJoints.kt`, `JoltContacts.kt`, `JoltDebugDraw.kt`, **NEW** `JoltVehicle.kt`, **NEW** `JoltCharacter.kt` | PHY | Jolt implementation |
| `engine/ecs/systems/PhysicsSystem.kt`, `RagdollSystem.kt`, `engine/ecs/components/RagdollComponent.kt`, `RagdollState.kt`, `VehicleComponent.kt` (+ **NEW** `VehicleDrivetrain.kt`), **NEW** `CharacterBody3D.kt`, `engine/physics3d/RagdollBuilder.kt` | PHY | Engine glue |
| `engine/ecs/components/ComponentType.kt`, `Component.kt`, `engine/EngineExtensions.kt`, `engine/assets/serialization/Serializer.kt` | ECS / AST | Register `CharacterBody3D` |
| `engine/ecs/systems/PlayerMotionSystem.kt` | GPL | Drive `CharacterBody3D` |
| `editor/imgui/components/` | EDT | Vehicle and character inspector sections |
| `game/samples/FeatureShowcase.kt`, `src/test/.../game/samples/FeatureShowcaseTest.kt` | TLG (GPL reviews the walker) | Showcase walker, course, powered ragdoll, vehicle |
| `src/test/kotlin/com/pafoid/skate/architecture/` | QA | Guard: only `engine/physics3d/jolt/` may import `com.github.stephengold.joltjni` |

## 7. Slicing

Each slice is one task with its own tests and lands green on its own.

| Slice | Owner | Content | Deps |
|---|---|---|---|
| **PHY-19a** Joint motors | PHY | `JointMotor`, `ConeTwistConstraint3D.motor`, `setJointMotorTarget` / `supportsJointMotors`; Jolt `SwingTwistConstraint` motors; Bullet `ConeJoint` → `New6Dof` with servos; Simple unsupported; cross-backend motor test (§3.1–3.4) | — |
| **New PHY task** (ID from the lead) Ragdoll bind-pose frames | PHY | Joint frames and pivots from the skeleton bind pose, capsule sizes from bone lengths (decision 4 of 2026-11-10) | 19a; done right after it |
| **PHY-19b** Powered ragdoll | PHY (ANI reviews the pose maths) | `RagdollState.POWERED`, `motorStrength`, targets in `RagdollSystem`, default motors, Simple fallback (§3.5) | 19a, the bind-pose task |
| **Separate ANI+PHY task** (ID from the lead) Ragdoll blending | ANI + PHY | `BLENDING` with motors: animation↔physics cross-fade (decision 1 of 2026-11-10) | 19b |
| **PHY-19c** Vehicle drivetrain | PHY | New `VehicleComponent` fields and types, `JoltVehicle`, rewritten `RaycastVehicleSolver`, read-back, cross-backend tests (§4.1–4.3, 4.5) | — |
| **PHY-19d** Inspector sections | EDT | Vehicle groups with list and curve editors; `CharacterBody3D` capsule section (decisions 2 and 3 of 2026-11-07) | 19c, 19e |
| **PHY-19e** Character body | PHY (ECS/AST one-line registrations) | `CharacterBody3D`, `ICharacterHandle`, `CharacterDescription`, `JoltCharacter` with inner body, `RigidBodyCharacter`, triggers, debug draw, `probeGround` (§5.1–5.3) | — |
| **PHY-19f** Gameplay on the character body | GPL | `PlayerMotionSystem` drives `CharacterBody3D` (§5.4) | 19e |
| **PHY-19g** Showcase | TLG (GPL reviews the walker) | Walker on `CharacterBody3D` with its course (§5.5), a `POWERED` ragdoll, a vehicle using the full drivetrain | 19b, 19c, 19f |
| **QA guard** | QA | joltjni import guard | — |

Suggested order: 19a → bind-pose task → 19b; 19c in parallel; 19e → 19f → 19g; 19d once 19c and 19e land.

## 8. Tolerances and risks

Cross-backend tolerances (approval answer 5: start with these, tighten later):

| Measure | Tolerance |
|---|---|
| Joint reaches its motor target | ≤ 0.05 rad on each backend; backends within 0.05 rad of each other |
| Vehicle ride height at rest | ± 2 cm |
| Vehicle 0→50 km/h time, braking distance from 50 km/h | ± 10 % |
| Top speed in a gear, RPM at upshift | ± 5 % |
| Steady-state body roll in a turn | ± 1° |

Risks:
- **Matching Jolt's vehicle model in the solver** is the largest piece of work in this task. Jolt's controller has
  details (clutch model, friction-curve evaluation, wheel/ground velocity projection) that must be reproduced, not
  approximated. Mitigation: the per-component solver tests are written against Jolt's own numbers for single-wheel
  setups first, then the whole-car scenarios.
- **Bullet `New6Dof` limits differ from a cone**: XYZ Euler limits make a box-shaped swing range, not a cone. Limp
  ragdolls on Bullet will move slightly differently than today (approval answer 2).
- **Motor stability**: 3.5 kg forearms with stiff motors can jitter. Defaults are a 20 Hz, critically damped motor
  scaled by `motorStrength`. Bullet's servos are sensitive to the effective-inertia estimate.
- **Character and networking**: `CharacterVirtual` is not in the rigid-body state that net prediction replays. The
  skater stays on `RigidBody3D` (decision 6).
- **Platform**: only `jolt-jni-Windows64` is on the classpath (`NativeLibrarySpec.JOLT`). Elsewhere Jolt falls back
  to Bullet, which now supports every feature above, so the fallback loses fidelity, not features.

## 9. Decisions

Human decisions recorded on 2026-11-07:

1. **Jolt built-in Ragdoll**: No — stick with pose motors only (does not match the engine's existing API).
2. **Vehicle suspension in editor**: Yes — expose suspension parameters in the editor inspector.
3. **Character controller collision shapes**: Yes — configurable capsule radius and height.

Human decisions recorded on 2026-11-10 (answers to revision 2's open questions):

1. Add RagdollState.POWERED; animation<->physics blending (BLENDING with motors) is a separate ANI+PHY task.
2. Vehicles: ADD the full tuning NOW (engine RPM/torque curve, gearbox, anti-roll bars) in VehicleComponent, and make it work on ALL backends: Jolt maps it onto its native VehicleConstraint; Bullet and Simple get the same drivetrain model in the engine's RaycastVehicleSolver. One field set, same behaviour within tolerance across backends (tests compare them).
3. Joint motors on ALL backends: rebuild Bullet ragdoll joints as libbulletjme New6Dof with motors so POWERED works on Bullet; Simple has no joints -> limp fallback.
4. Ragdoll bind-pose frames: a separate PHY task right after the motor slice.
5. CharacterVirtual gets an inner rigid body (visible to raycasts, triggers, AI line of sight).
6. MOVE THE SHOWCASE WALKER to CharacterBody3D now (in PHY-19, with the gameplay side reviewed by skate-gameplay); the skater keeps RigidBody3D.

Human answers recorded on 2026-11-10 to revision 3's open questions (approval of revision 3):

1) showcase walker: 'Take control' only, no autopilot; 2) Bullet ragdolls on New6Dof always; 3) DROP the old vehicle fields, no migration; 4) no switch: each project uses only its own backend (Bullet projects Bullet only, same for Jolt and Simple); 5) start with the §8 tolerances, tighten later.

Implementation is PHY-23 (P3), later.

Already decided elsewhere and assumed here: Bullet stays the default backend, and per-project selection is PHY-17
slice f, which waits on EDT-59 (PHY-17 "Decisions after review" 3).

## 10. Open questions

None. Revision 3's five open questions were answered on 2026-11-10 (§9).
