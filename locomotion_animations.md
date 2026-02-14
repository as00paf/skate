# TASK: Integrate additional locomotion animations into physics-driven character

## Context

Engine:

- Kotlin
- LWJGL
- Assimp
- Bullet Physics
- Root motion DISABLED
- Animations are in-place
- RigidBody controls movement

Goal:
Integrate the following animations into the locomotion system (PlayerController, PlayerStateManager and Animator):

- Walk
- Run
- StrafeLeft
- StrafeRight
- WalkStrafeLeft
- WalkStrafeRight
- Turn90Left
- Turn90Right
- Turn180Left
- Turn180Right
- JumpStart
- Falling
- Land

Physics drives movement. Animations reflect state only.

---

# STEP 1 — Extend Player State Model (DONE)

Update PlayerState class with the new states and rename it to LocomotionState:

IDLE,
WALK,
RUN,
STRAFE,
JUMP,
FALLING,
LANDING,
TURN_90_L,
TURN_90_R,
TURN_180_L,
TURN_180_R,
FALLING,
FALLING_IDLE,

---

# STEP 2 — Derive Movement Data From Bullet (KIND OF DONE)

Inside PlayerController update:

val velocity = rigidBody.linearVelocity
val speed = velocity.length()

val forward = getForwardVector()
val right = getRightVector()

val forwardSpeed = velocity.dot(forward)
val rightSpeed = velocity.dot(right)
val verticalSpeed = velocity.y

Create a data class for MotionData:

data class MotionData(
val speed: Float,
val forwardSpeed: Float,
val rightSpeed: Float,
val verticalSpeed: Float,
val isGrounded: Boolean,
val wasGrounded: Boolean
...
)

Create PlayerMotionAnalyzer.kt to analyze RigidBody's data :
fun analyze(rb: RigidBody, forward: Vector3f, right: Vector3f, isGrounded: Boolean): MotionData {
val velocity = rb.linearVelocity
return MotionData(
speed = velocity.length(),
forwardSpeed = velocity.dot(forward),
rightSpeed = velocity.dot(right),
verticalSpeed = velocity.y,
isGrounded = isGrounded
)
}

---

# STEP 3 — Running Logic (DONE)

val isRunning = input.sprintPressed (Left Trigger)

if (isGrounded) {
if (speed < 0.1f) {
state = LocomotionState.IDLE
} else if (isRunning) {
state = LocomotionState.RUN
} else {
state = LocomotionState.WALK
}
}

---

# STEP 4 — Strafe Detection

If lateral movement dominates:

if (abs(rightSpeed) > abs(forwardSpeed) && abs(rightSpeed) > 0.1f) {
state = LocomotionState.STRAFE
}

---

# STEP 5 — Jump / Fall

if (!isGrounded && verticalSpeed > 0f) {
state = LocomotionState.JUMP_START
}

if (!isGrounded && verticalSpeed < 0f) {
state = LocomotionState.FALLING
}

if (wasGrounded == false && isGrounded == true) {
state = LocomotionState.LANDING
}

---

# STEP 6 — Turn 180 Trigger

Only trigger when nearly idle and direction flips.

val inputDir = getInputDirection()
val facingDir = getForwardVector()

val dot = inputDir.dot(facingDir)

if (speed < 0.2f && dot < -0.8f) {
if (input.turnAxis > 0f)
state = LocomotionState.TURN_180_R
else
state = LocomotionState.TURN_180_L
}

---

# STEP 7 — Turn 90 Trigger

If near idle and sharp lateral input:

if (speed < 0.2f && abs(input.turnAxis) > 0.8f) {
if (input.turnAxis > 0f)
state = LocomotionState.TURN_90_R
else
state = LocomotionState.TURN_90_L
}

---

# STEP 8 — Animator Mapping

Inside PlayerAnimator:

fun update(state: LocomotionState) {

    when (state) {

        IDLE -> play("Idle")

        WALK -> play("Walk")

        RUN -> play("Run")

        STRAFE -> {
            if (rightSpeed > 0)
                play("StrafeRight")
            else
                play("StrafeLeft")
        }

        JUMP -> play("Jump")
        FALLING -> play("Falling")
        LANDING -> play("Land")

        TURN_90_L -> play("Turn90Left")
        TURN_90_R -> play("Turn90Right")
        TURN_180_L -> play("Turn180Left")
        TURN_180_R -> play("Turn180Right")
        ...
    }

}

---

# STEP 10 — Disable Torque During Turn Animations

When entering TURN state:

- Disable player torque input
- Rotate rigidBody manually over animation duration
- Re-enable torque on animation end

Example:

if (state == TURN_180_L) {
rigidBody.angularVelocity = Vector3f(0f, rotationSpeed, 0f)
}

On animation complete:
rigidBody.angularVelocity = Vector3f(0f)

---

# STEP 11 — Animation Speed Sync

For WALK and RUN:

animationPlayer.speed = speed / desiredMoveSpeed

Clamp between 0.5 and 1.5

---

# IMPORTANT RULES

- NEVER move transform manually
- NEVER rotate transform manually
- ONLY affect rigidBody
- Animations are visual only
- Physics is authoritative
- IPhysicsBody3D might be missing some methods to access the Bullet's library raw body properties, let the user know and
  add them if necessary

---

# END TASK
