package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.events.Takeoff
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.Interpolator
import com.pafoid.skate.game.player.MotionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.atan2

/**
 * Component responsible for applying gameplay physics to the player entity.
 *
 * This component reads gameplay input state from [InputStateComponent] and applies
 * appropriate physics forces (movement, jumping, ground snapping). It does not poll
 * raw hardware inputs directly - that is the responsibility of [com.pafoid.skate.engine.ecs.systems.InputSystem].
 *
 * Subscribes to events for state changes:
 * - [JumpPressed] - to trigger jumping
 * - [Landing] - to handle landing state
 * - [Takeoff] - to handle takeoff state
 * - [MovementInput] - to update movement direction
 *
 * ## Responsibilities
 *
 * - Read movement direction from [InputStateComponent] and apply velocity
 * - Handle jumping based on [JumpPressed] events
 * - Read trick inputs from [InputStateComponent] (flip, kickflip, heelflip, grab, manual)
 * - Apply ground snapping to keep player model aligned with terrain
 * - Manage speed interpolation between walk and run states
 *
 * ## Configuration
 *
 * All thresholds and physics values are configurable via inline fields:
 * - movementThreshold - Minimum input magnitude for movement
 * - sprintThreshold - Input magnitude for auto-sprint
 * - jumpImpulse - Jump force
 * - walkSpeed - Walking speed
 * - runSpeed - Running speed
 * - rotationSpeed - Character rotation speed
 * - takeOffTime - Jump charge time
 */
@Serializable
class PlayerController : Component(), KoinComponent {

    private val logger: LoggerService by inject()
    private val eventSystem: EventSystem by inject()

    // Physics values - can be overridden or loaded from InputSettings
    var jumpImpulse = 300.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f
    var walkSpeed = 2.5f
    var runSpeed = 7.5f
    var rotationSpeed = 10f
    var takeOffTime = 0.9f

    // State
    var jumpTimer = 0f
    var lastSpeed = 1f
    var isGrounded = false
    var wasGrounded = false
    var isJumping = false

    // Event-driven state
    private var jumpPressed = false
    @Transient private var movementDirection: Vector2f = Vector2f(0f, 0f)
    private var movementMagnitude: Float = 0f

    // Trick input state
    private var flipLeftHeld = false
    private var flipRightHeld = false

    @Transient private val stateManager: PlayerStateManager? by lazy { gameObject.getComponent<PlayerStateManager>() }

    // Exposed for PlayerStateManager to read player intent
    @Transient val desiredMoveDirection = Vector3f()
    @Transient
    val desiredRotation = Quaternionf()

    @Transient
    var motionData = MotionData()

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        stateManager ?: run { logger.log("Could not find StateManager for ${gameObject.name}", LogLevel.ERROR) }

        eventSystem.subscribe<JumpPressed> { onJumpPressed(it) }
        eventSystem.subscribe<Landing> { onLanding(it) }
        eventSystem.subscribe<Takeoff> { onTakeoff(it) }
        eventSystem.subscribe<MovementInput> { onMovementInput(it) }
    }

    /**
     * Called when jump button is pressed.
     */
    private fun onJumpPressed(event: JumpPressed) {
        jumpPressed = true
    }

    /**
     * Called when landing on the ground.
     */
    private fun onLanding(event: Landing) {
        isGrounded = true
        isJumping = false
    }

    /**
     * Called when taking off from the ground.
     */
    private fun onTakeoff(event: Takeoff) {
        isGrounded = false
        isJumping = true
    }

    /**
     * Called when movement input changes.
     */
    private fun onMovementInput(event: MovementInput) {
        movementDirection.set(event.direction)
        movementMagnitude = event.magnitude
    }

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
            logger.log("Kickflip input detected", LogLevel.INFO)
        }
        if (heelflipPressed) {
            logger.log("Heelflip input detected", LogLevel.INFO)
        }
        if (grabPressed) {
            logger.log("Grab input detected", LogLevel.INFO)
        }
        if (manualPressed) {
            logger.log("Manual input detected", LogLevel.INFO)
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