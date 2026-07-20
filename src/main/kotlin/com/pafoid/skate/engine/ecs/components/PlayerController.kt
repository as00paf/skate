package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.Interpolator
import com.pafoid.skate.game.player.MotionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.atan2

@Serializable
class PlayerController : Component() {

    // Physics values - can be overridden or loaded from InputSettings
    var jumpImpulse = 300.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f
    var walkSpeed = 2.5f
    var runSpeed = 7.5f
    var rotationSpeed = 10f
    var takeOffTime = 0.9f

    // State
    @Transient
    var jumpTimer = 0f
    @Transient
    var lastSpeed = 1f
    @Transient
    var isGrounded = false
    @Transient
    var wasGrounded = false
    @Transient
    var isJumping = false

    // Event-driven state
    @Transient
    var jumpPressed = false
    @Transient
    var movementDirection: Vector2f = Vector2f(0f, 0f)
    @Transient
    var movementMagnitude: Float = 0f

    // Trick input state
    //TODO: move
    @Transient
    private var flipLeftHeld = false
    @Transient
    private var flipRightHeld = false

    // Exposed for PlayerStateManager to read player intent
    @Transient val desiredMoveDirection = Vector3f()
    @Transient
    val desiredRotation = Quaternionf()

    @Transient
    var motionData = MotionData()

    override fun update(dt: Float) {
        val inputState = gameObject.getComponent<InputStateComponent>() ?: return

        // PRIORITIZE event-driven movement state; fallback to InputStateComponent polling
        val useEvents = movementMagnitude > 0.15f
        val inputDirection = if (useEvents) {
            movementDirection
        } else {
            inputState.moveDirection
        }

        val isSprinting =
            inputState.sprintPressed || (useEvents && movementMagnitude > 0.65f) || (!useEvents && inputState.moveDirection.lengthSquared() > 0.42f)

        // Move if input is above threshold
        if (inputDirection.lengthSquared() > 0.0225f) { // 0.15^2
            val speed = getDesiredSpeed(isSprinting, dt)
            motionData = MotionData(
                inputDirection = inputDirection,
                speed = speed,
                targetYaw = atan2(desiredMoveDirection.x, desiredMoveDirection.z),
                rotationSpeed = dt * rotationSpeed,
                isGrounded = isGrounded,
                wasGrounded = wasGrounded
            )
        }

        // Use event-driven jump state
        handleJumping(inputState, dt, jumpPressed)
        jumpPressed = false // Reset after processing
        
        handleTrickInputs(inputState, dt)

        wasGrounded = isGrounded
    }

    /**
     * Handles trick input detection and state tracking.
     *
     * @param inputState Current input state
     * @param dt Delta time
     */
    // TODO: move
    private fun handleTrickInputs(inputState: InputStateComponent, dt: Float) {
        // Track flip input state for combination detection
        val flipLeftPressed = inputState.flipLeftPressed
        val flipRightPressed = inputState.flipRightPressed
        val kickflipPressed = inputState.kickflipPressed
        val heelflipPressed = inputState.heelflipPressed
        val grabPressed = inputState.grabPressed
        val manualPressed = inputState.manualPressed

        // Detect flip direction (hold left/right while pressing flip)
        if (flipLeftPressed) flipLeftHeld = true
        if (flipRightPressed) flipRightHeld = true

        // Reset flip hold when flip button released
        if (!inputState.flipLeftPressed && !inputState.flipRightPressed) {
            flipLeftHeld = false
            flipRightHeld = false
        }

        // Trick combination detection (to be implemented in TrickDetector)
        // Examples:
        // - Kickflip: kickflipPressed + flipLeftHeld
        // - Heelflip: heelflipPressed + flipRightHeld
        // - Pop Shove-it: flipLeftPressed + flipRightPressed (both directions)
        // - Grab: grabPressed (while airborne)
        // - Manual: manualPressed (while on ground)

        // Log trick inputs for debugging
        if (kickflipPressed) {
            //logger.log("Kickflip input detected", LogLevel.INFO)
        }
        if (heelflipPressed) {
            //logger.log("Heelflip input detected", LogLevel.INFO)
        }
        if (grabPressed) {
            //logger.log("Grab input detected", LogLevel.INFO)
        }
        if (manualPressed) {
            //logger.log("Manual input detected", LogLevel.INFO)
        }
    }

    private fun getDesiredSpeed(isSprintPressed: Boolean, dt: Float): Float {
        return if (isSprintPressed) {
            Interpolator.lerp(lastSpeed, runSpeed, dt * walkSpeed / 2)
        } else {
            Interpolator.lerp(lastSpeed, walkSpeed, dt * runSpeed)
        }
    }

    /**
     * Gets the current horizontal speed from PhysicsComponent.
     * Falls back to lastSpeed if component not available.
     *
     * @return Current speed in m/s
     */
    private fun getCurrentSpeed(): Float {
        return gameObject.getComponent<PhysicsComponent>()?.speed ?: lastSpeed
    }

    /**
     * Handles jump input, applying a vertical impulse.
     *
     * @param inputState The input state component containing jump button state
     * @param dt Delta time since last frame
     * @param jumpPressedFromEvent Jump pressed flag from JumpPressed event (optional)
     */
    fun handleJumping(inputState: InputStateComponent, dt: Float, jumpPressedFromEvent: Boolean = false) {
        if (jumpTimer > 0) {
            jumpTimer -= dt
        }

        // Use jumpPressed from event if available, otherwise fall back to InputStateComponent polling
        val jumpPressed = jumpPressedFromEvent || inputState.jumpPressed

        if (isJumping && !wasGrounded && isGrounded) { // Land
            isJumping = false
            //logger.log("LANDED!")
        }

        if (!isJumping && isGrounded && jumpPressed) { // Crouch
            isJumping = true
            jumpTimer = takeOffTime

            //logger.log("JUMP TIMER STARTED!")
        }
    }

}