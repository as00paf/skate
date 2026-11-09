# PHY-19 — Jolt-native ragdolls, vehicles and character controller (design note)

Status: **proposal, awaiting human approval**. No code has been written.

## Goal

Add optional Jolt-native implementations of `Ragdoll`, `VehicleConstraint` and `CharacterVirtual` to the `IPhysics3D` abstraction layer. These are higher-level physics features that Bullet provides via its own APIs (btKinematicCharacterController, btRaycastVehicle, btRagdoll). The goal is feature parity with the Bullet backend so projects can use Jolt as a drop-in replacement (PHY-17).

## Decision summary

| Topic | Decision |
|---|---|
| Implementation approach | Add Jolt-specific classes in `skate-physics-jolt` that implement the same interfaces as the Bullet backends (`IRagdoll`, `IVehicle`, `ICharacterController`). |
| Ragdoll mode | **Pose motors** (Jolt's `MotionConstraint`) — drive joint angles via target positions. Not Jolt's built-in `Ragdoll` class (too opinionated; doesn't match the engine's existing ragdoll API). |
| Vehicle mode | **Jolt `VehicleConstraint`** with raycast wheel simulation. Mirrors Bullet's btRaycastVehicle interface: suspension, steering, braking, engine torque per wheel. |
| Character controller | **Jolt `CharacterVirtual`** — swept-sphere character with step-up, slope-climbing, and collision filtering. Integrates with GPL-24's gameplay systems (movement, jumping, crouching). |
| Backend selection | Per-project setting in EDT-59; defaults to Bullet for now. Jolt backend must pass the full physics test suite before becoming default. |

## 1. Ragdoll — pose motors

### 1.1 Engine API (existing, from PHY-17)

```kotlin
interface IRagdoll {
    fun setJointTarget(jointIndex: Int, targetAngle: Float)
    fun getWorldTransform(bodyIndex: Int): Transform
    fun applyImpulse(bodyIndex: Int, impulse: Vec3, localPoint: Vec3)
    fun isAwake(bodyIndex: Int): Boolean
}
```

### 1.2 Jolt implementation

Jolt's ragdoll consists of rigid bodies connected by constraints (hinges, sliders, etc.). We implement `IRagdoll` by:

1. **Creating bodies and constraints**: on ragdoll creation, build Jolt `Body` objects for each bone and `Constraint` objects for each joint. Store the mapping from engine joint indices to Jolt constraint handles.
2. **Pose motors**: use Jolt's `MotionConstraint` (or `HingeConstraint` with motor) to drive each joint to a target angle. The motor applies torque proportional to the error between current and target angle.
3. **World transforms**: query each body's position/rotation via `Body::GetPosition()` / `Body::GetRotation()`.
4. **Impulses**: apply via `Body::AddImpulse()` with the local point offset.

```kotlin
class JoltRagdoll : IRagdoll {
    private val bodies: Array<Jolt.Body>
    private val constraints: Array<Jolt.Constraint>
    private val motorTargets: FloatArray  // target angle per joint

    override fun setJointTarget(jointIndex: Int, targetAngle: Float) {
        motorTargets[jointIndex] = targetAngle
        // Update constraint motor parameters each tick
    }

    override fun getWorldTransform(bodyIndex: Int): Transform {
        val pos = bodies[bodyIndex].position
        val rot = bodies[bodyIndex].rotation
        return Transform(pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, rot.w)
    }

    override fun applyImpulse(bodyIndex: Int, impulse: Vec3, localPoint: Vec3) {
        bodies[bodyIndex].addImpulse(
            joltVec3(impulse.x, impulse.y, impulse.z),
            joltVec3(localPoint.x, localPoint.y, localPoint.z)
        )
    }

    override fun isAwake(bodyIndex: Int): Boolean {
        return bodies[bodyIndex].isSleeping.not()
    }
}
```

### 1.3 Test plan for ragdolls

1. Create a ragdoll with 8 bones (spine, arms, legs); set joint targets and verify the body follows the pose.
2. Apply an impulse to one body; verify adjacent bodies react via constraints (no separation).
3. Put all bodies to sleep; verify `isAwake` returns false for all.

## 2. Vehicle — Jolt `VehicleConstraint`

### 2.1 Engine API (existing, from PHY-17)

```kotlin
interface IVehicle {
    fun setSteering(angle: Float)
    fun setEngineTorque(torque: Float)
    fun setBrake(brake: Float)       // 0 = no brake, 1 = full brake per wheel
    fun getWheelTransform(index: Int): Transform
    fun isOnGround(): Boolean
}
```

### 2.2 Jolt implementation

Jolt's `VehicleConstraint` handles suspension, steering and driving via raycast wheels. We map the engine API to Jolt's vehicle API:

1. **Wheel setup**: on creation, add each wheel with suspension stiffness, damping, compression ratio, and tire friction. Wheel radius and axle direction are configurable per-project.
2. **Steering**: `setSteering()` maps to Jolt's `SetSteeringValue()` (range: -maxSteerAngle to +maxSteerAngle).
3. **Engine torque**: `setEngineTorque()` applies torque to driven wheels via `SetEngineForce()`.
4. **Braking**: `setBrake()` applies brake force per wheel via `SetBrake()`.
5. **Wheel transforms**: query each wheel's contact point and suspension travel; compute the world transform from the chassis position + offset.

```kotlin
class JoltVehicle : IVehicle {
    private val vehicle: Jolt.VehicleConstraint
    private var currentSteering = 0f
    private var currentTorque = 0f
    private var currentBrake = 0f

    override fun setSteering(angle: Float) {
        currentSteering = angle
        vehicle.setSteeringValue(joltClamp(angle, -maxSteerAngle, maxSteerAngle))
    }

    override fun setEngineTorque(torque: Float) {
        currentTorque = torque
        // Apply to driven wheels
        for (wheelIndex in drivenWheels) {
            vehicle.setEngineForce(wheelIndex, joltVec3(0f, torque, 0f))
        }
    }

    override fun setBrake(brake: Float) {
        currentBrake = brake
        for (wheelIndex in 0 until numWheels) {
            vehicle.setBrake(wheelIndex, brake * maxBrakeForce)
        }
    }

    override fun getWheelTransform(index: Int): Transform {
        val contactPoint = vehicle.getWheelContactPoint(index)
        val suspensionTravel = vehicle.getWheelSuspensionLength(index)
        // Compute world transform from chassis + offset
        return computeWheelWorldTransform(index, contactPoint, suspensionTravel)
    }

    override fun isOnGround(): Boolean {
        for (i in 0 until numWheels) {
            if (!vehicle.isWheelInAir(i)) return true
        }
        return false
    }
}
```

### 2.3 Test plan for vehicles

1. Create a raycast vehicle on a flat plane; apply engine torque and verify forward acceleration.
2. Apply steering at speed; verify the vehicle turns with expected radius.
3. Apply brakes; verify stopping distance matches expected physics (compare with Bullet backend).
4. Drive over a ramp; verify suspension compression and wheel contact detection.

## 3. Character controller — Jolt `CharacterVirtual`

### 3.1 Engine API (existing, from PHY-17)

```kotlin
interface ICharacterController {
    fun setMovement(movement: Vec3)     // desired velocity (not impulse)
    fun jump()
    fun setCrouching(crouching: Boolean)
    fun getWorldPosition(): Vec3
    fun isOnGround(): Boolean
    fun startSlopeWalk(slopeLimitDegrees: Float): Boolean
}
```

### 3.2 Jolt implementation

Jolt's `CharacterVirtual` is a swept-sphere character controller with built-in step-up, slope-climbing and collision filtering. We map the engine API to Jolt's character API:

1. **Movement**: `setMovement()` sets the desired velocity; `CharacterVirtual::Update()` handles collision resolution each tick.
2. **Jumping**: apply an upward impulse when `jump()` is called and the character is on ground.
3. **Crouching**: change the character's collision shape radius/height via `SetCollisionFilter` or by swapping to a smaller capsule shape.
4. **Slope walking**: configure `CharacterVirtual`'s slope limit; Jolt handles slope climbing automatically within the limit.

```kotlin
class JoltCharacterController : ICharacterController {
    private val character: Jolt.CharacterVirtual
    private var desiredMovement = Vec3.Zero
    private var crouching = false

    override fun setMovement(movement: Vec3) {
        desiredMovement = movement
    }

    override fun jump() {
        if (isOnGround()) {
            character.addImpulse(joltVec3(0f, jumpForce, 0f))
        }
    }

    override fun setCrouching(crouching: Boolean) {
        this.crouching = crouching
        val radius = if (crouching) crouchRadius else standRadius
        character.setCollisionShape(createCapsuleShape(radius, height))
    }

    override fun getWorldPosition(): Vec3 {
        val pos = character.position
        return Vec3(pos.x, pos.y, pos.z)
    }

    override fun isOnGround(): Boolean {
        return character.isInContact && character.contactNormal.y > slopeLimitCos
    }

    override fun startSlopeWalk(slopeLimitDegrees: Float): Boolean {
        val cos = Math.cos(Math.toRadians(slopeLimitDegrees))
        // Jolt's CharacterVirtual already has a slope limit; just update it
        character.setStepHeight(stepHeight)
        return true
    }

    // Called each tick from the physics system
    fun update(dt: Float) {
        val joltMovement = joltVec3(desiredMovement.x, desiredMovement.y, desiredMovement.z)
        character.update(joltMovement, dt)
    }
}
```

### 3.3 GPL-24 integration

The character controller will be used by GPL-24's gameplay systems:
- **Player movement**: WASD input → `setMovement()` with horizontal velocity.
- **Jumping**: space bar → `jump()`.
- **Crouching**: crouch key → `setCrouching(true/false)`.
- **Slope handling**: automatic via Jolt's slope limit (no manual slope walking needed for most cases).

## 4. Backend selection and fallback

### 4.1 Per-project setting

EDT-59's project settings include a `physicsBackend` field:

```kotlin
enum class PhysicsBackend { BULLET, SIMPLE, JOLT }
```

When the editor loads a project with `Jolt`, it initializes the Jolt backend (`Physics3DFactory.create(PhysicsBackend.JOLT)`). The factory returns Jolt-specific implementations of `IRagdoll`, `IVehicle` and `ICharacterController`.

### 4.2 Fallback

If Jolt natives are not available (e.g., unsupported platform), fall back to Bullet:
- Check native availability at project load time.
- If Jolt is selected but unavailable, log a warning and switch to Bullet.
- Show an editor notification: "Jolt physics not available on this platform; falling back to Bullet."

## 5. Test plan

1. **Ragdoll pose**: create a ragdoll, set all joint targets to known angles, verify the final world transforms match expected poses within tolerance (0.01 m for position, 0.01 rad for rotation).
2. **Vehicle acceleration**: on a flat plane with no friction, apply constant engine torque; verify acceleration matches `F = ma` (torque / wheel radius / mass).
3. **Character jump**: from rest, call `jump()` with known force; verify peak height matches `h = v² / (2g)`.
4. **Slope climbing**: drive a vehicle up a 30° ramp; verify it stays on the surface without slipping (within tolerance).
5. **Backend parity**: run all ragdoll/vehicle/character tests on both Bullet and Jolt backends; results must match within physics tolerance (1% for position, 2% for velocity).

## 6. Risks

- **Jolt `CharacterVirtual` maturity**: it is newer than the ragdoll and vehicle APIs. Test thoroughly with edge cases (steep slopes, fast movement, multiple simultaneous collisions).
- **Pose motor stability**: driving joints via motors can cause instability at high target angles or low damping. Add configurable motor strength/damping parameters per joint.
- **Vehicle raycast performance**: raycasting each wheel every tick is O(n_wheels × n_collisions). For vehicles with many wheels (>8), consider caching collision results.
- **Platform support**: Jolt natives must be built for all target platforms (Windows, Linux, macOS ARM/x86_64). Verify native builds before enabling Jolt as a project option.

## 7. Decisions

Human decisions recorded on 2026-11-07:

1. **Jolt built-in Ragdoll**: No — stick with pose motors only (does not match the engine's existing API).
2. **Vehicle suspension in editor**: Yes — expose suspension parameters in the editor inspector.
3. **Character controller collision shapes**: Yes — configurable capsule radius and height.

## 8. Open questions

_No remaining open questions._
