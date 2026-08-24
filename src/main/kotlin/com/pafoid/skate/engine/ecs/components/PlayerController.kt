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

    // Exposed for PlayerStateManager to read player intent
    @Transient val desiredMoveDirection = Vector3f()
    @Transient
    val desiredRotation = Quaternionf()

    @Transient
    var motionData = MotionData()


    //TODO: move
    override fun update(dt: Float) {
        val inputState = gameObject?.getComponent<InputStateComponent>() ?: return

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

        wasGrounded = isGrounded
    }

    private fun getDesiredSpeed(isSprintPressed: Boolean, dt: Float): Float {
        return if (isSprintPressed) {
            Interpolator.lerp(lastSpeed, runSpeed, dt * walkSpeed / 2)
        } else {
            Interpolator.lerp(lastSpeed, walkSpeed, dt * runSpeed)
        }
    }

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
        }
    }

}