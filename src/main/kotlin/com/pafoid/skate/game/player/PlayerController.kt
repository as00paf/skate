package com.pafoid.skate.game.player

import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.utils.Interpolator
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
 * ## Responsibilities
 *
 * - Read movement direction from [InputStateComponent] and apply velocity
 * - Handle jumping based on [InputStateComponent.jumpPressed]
 * - Read trick inputs from [InputStateComponent] (flip, kickflip, heelflip, grab, manual)
 * - Apply ground snapping to keep player model aligned with terrain
 * - Manage speed interpolation between walk and run states
 *
 * ## Configuration
 *
 * All thresholds and physics values are configurable via [InputSettings]:
 * - [InputSettings.movementThreshold] - Minimum input magnitude for movement
 * - [InputSettings.sprintThreshold] - Input magnitude for auto-sprint
 * - [InputSettings.jumpImpulse] - Jump force
 * - [InputSettings.walkSpeed] - Walking speed
 * - [InputSettings.runSpeed] - Running speed
 * - [InputSettings.rotationSpeed] - Character rotation speed
 * - [InputSettings.takeOffTime] - Jump charge time
 */
class PlayerController : Component(), KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val logger: LoggerService by inject()

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

    // Trick input state
    private var flipLeftHeld = false
    private var flipRightHeld = false

    private val stateManager: PlayerStateManager? by lazy { gameObject.getComponent<PlayerStateManager>() }
    private val rb: IPhysicsBody3D? by lazy { gameObject.getComponent<IPhysicsBody3D>() }

    private val camera: Camera? by lazy { sceneManager.currentScene?.camera }
    private val physics3d: IPhysics3D? by lazy { sceneManager.currentScene?.physics3d }

    // Exposed for PlayerStateManager to read player intent
    val desiredMoveDirection = Vector3f()
    private val desiredRotation = Quaternionf()

    override fun start() {
        rb ?: run { logger.logEngine("Could not find RigidBody for ${gameObject.name}", LogLevel.ERROR) }
        stateManager ?: run { logger.logEngine("Could not find StateManager for ${gameObject.name}", LogLevel.ERROR) }
    }

    override fun update(dt: Float) {
        val body = rb ?: return
        val camera = camera ?: return
        val physics3d = physics3d ?: return
        val inputState = gameObject.getComponent<InputStateComponent>() ?: return

        isGrounded = checkIfGrounded(physics3d)
        inputState.isGrounded = isGrounded

        // Get input direction from InputStateComponent
        val inputDirection = inputState.moveDirection
        val isSprinting = inputState.sprintPressed || inputDirection.lengthSquared() > 0.42f // 0.65^2

        // Move if input is above threshold
        if (inputDirection.lengthSquared() > 0.0225f) { // 0.15^2
            val speed = getDesiredSpeed(isSprinting, dt)
            val motionData = MotionData(
                direction = getDesiredMoveDirection(camera.getForwardAndRight(), inputDirection),
                speed = speed,
                targetYaw = atan2(desiredMoveDirection.x, desiredMoveDirection.z),
                rotationSpeed = dt * rotationSpeed,
                isGrounded = isGrounded,
                wasGrounded = wasGrounded
            )

            applyMotion(motionData, body)
        }

        handleJumping(inputState, dt)
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
            logger.logEngine("Kickflip input detected", LogLevel.INFO)
        }
        if (heelflipPressed) {
            logger.logEngine("Heelflip input detected", LogLevel.INFO)
        }
        if (grabPressed) {
            logger.logEngine("Grab input detected", LogLevel.INFO)
        }
        if (manualPressed) {
            logger.logEngine("Manual input detected", LogLevel.INFO)
        }
    }

    private fun getDesiredSpeed(isSprintPressed: Boolean, dt: Float): Float {
        return if (isSprintPressed) {
            Interpolator.lerp(lastSpeed, runSpeed, dt * walkSpeed / 2)
        } else {
            Interpolator.lerp(lastSpeed, walkSpeed, dt * runSpeed)
        }
    }

    private fun getDesiredMoveDirection(camForwardAndRight: Pair<Vector3f, Vector3f>, input: Vector2f): Vector3f {
        desiredMoveDirection.zero()
        camForwardAndRight.first.mul(input.y, desiredMoveDirection)
        val rightPart = Vector3f(camForwardAndRight.second).mul(input.x)
        desiredMoveDirection.add(rightPart)

        return desiredMoveDirection.normalize()
    }

    private fun applyMotion(data: MotionData, body: IPhysicsBody3D) {
        val velocity = body.linearVelocity

        velocity.x = data.direction.x * data.speed
        velocity.z = data.direction.z * data.speed

        body.linearVelocity = velocity
        lastSpeed = data.speed

        val rotation = body.getRotation()
        val currentYaw = atan2(
            2f * (rotation.w * rotation.y + rotation.x * rotation.z),
            1f - 2f * (rotation.y * rotation.y + rotation.z * rotation.z)
        )

        val newYaw = Interpolator.lerpAngle(currentYaw, data.targetYaw, data.rotationSpeed)

        desiredRotation.set(Quaternionf().rotateY(newYaw))
        body.setRotation(desiredRotation)
    }

    /**
     * Gets the current horizontal speed from PhysicsComponent.
     * Falls back to lastSpeed if component not available.
     *
     * @return Current speed in m/s
     */
    private fun getCurrentSpeed(): Float {
        return gameObject.getComponent<com.pafoid.skate.engine.ecs.components.PhysicsComponent>()?.speed ?: lastSpeed
    }

    /**
     * Raycasts downwards to snap the skater model to the ground while in the 'WALKING' state.
     * Prevents floating or clipping through terrain.
     */
    fun handleGroundSnapping() {
        val scene = sceneManager.currentScene ?: return
        val target = gameObject
        val pos = target.getComponent<Transform>()?.translation ?: return

        val rayStart = Vector3f(pos.x, pos.y + 1f, pos.z)
        val rayEnd = Vector3f(pos.x, pos.y - 2f, pos.z)

        val closest = scene.physics3d.raycastClosest(rayStart, rayEnd)
        if (closest != null) {
            val hitY = rayStart.y + (rayEnd.y - rayStart.y) * closest.hitFraction
            pos.y = hitY
        }
    }

    /**
     * Handles jump input, applying a vertical impulse.
     *
     * @param inputState The input state component containing jump button state
     * @param dt Delta time since last frame
     */
    fun handleJumping(inputState: InputStateComponent, dt: Float) {
        if (jumpTimer > 0) {
            jumpTimer -= dt
        }

        // Use jumpPressed from InputStateComponent (one-frame pulse)
        val jumpPressed = inputState.jumpPressed

        if (isJumping && !wasGrounded && isGrounded) { // Land
            isJumping = false
            //logger.logEngine("LANDED!")
        }

        if (!isJumping && isGrounded && jumpPressed) { // Crouch
            isJumping = true
            jumpTimer = takeOffTime

            //logger.logEngine("JUMP TIMER STARTED!")
        }

        if (isJumping && isGrounded && jumpTimer <= 0f) { // Jump
            //logger.logEngine("JUMP TIMER FINISHED!")
            rb?.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
            jumpTimer = takeOffTime
        }
    }

    private fun checkIfGrounded(physics3d: IPhysics3D): Boolean {
        val body = rb as? RigidBody3D ?: return false
        val originPosition = body.getWorldPosition()

        // Start the ray from the player's feet (below the collider)
        val feetY = originPosition.y
        val rayStart = Vector3f(originPosition.x, feetY, originPosition.z)

        // Ray goes down a small distance to detect ground
        val rayLength = 0.05f
        val rayEnd = Vector3f(rayStart.x, rayStart.y - rayLength, rayStart.z)

        // Exclude the player's own physics body from the raycast
        return physics3d.raycastClosest(rayStart, rayEnd, body) != null
    }


    /**
     * Displays a debug window with information about the player's state, stance, and velocity.
     */
    override fun imgui() {

    }
}