package com.pafoid.skate.game.player

import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.events.Takeoff
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

    // Event-driven state
    private var jumpPressed = false
    private var movementDirection: Vector2f = Vector2f(0f, 0f)
    private var movementMagnitude: Float = 0f

    // Trick input state
    private var flipLeftHeld = false
    private var flipRightHeld = false

    private val stateManager: PlayerStateManager? by lazy { gameObject.getComponent<PlayerStateManager>() }
    private val rb: IPhysicsBody3D? by lazy { gameObject.getComponent<IPhysicsBody3D>() }

    private val camera: Camera? by lazy { sceneManager.currentScene?.camera }
    private val physics3d: IPhysics3D? by lazy { sceneManager.currentScene?.physics3d }
    private var eventSystem: EventSystem? = null

    // Exposed for PlayerStateManager to read player intent
    val desiredMoveDirection = Vector3f()
    private val desiredRotation = Quaternionf()

    override fun start() {
        rb ?: run { logger.logEngine("Could not find RigidBody for ${gameObject.name}", LogLevel.ERROR) }
        stateManager ?: run { logger.logEngine("Could not find StateManager for ${gameObject.name}", LogLevel.ERROR) }

        // Get event system and subscribe to events
        val scene = sceneManager.currentScene
        eventSystem = scene?.systemManager?.getSystem<EventSystem>()

        eventSystem?.subscribe<JumpPressed> { onJumpPressed(it) }
        eventSystem?.subscribe<Landing> { onLanding(it) }
        eventSystem?.subscribe<Takeoff> { onTakeoff(it) }
        eventSystem?.subscribe<MovementInput> { onMovementInput(it) }
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
        val body = rb ?: return
        val camera = camera ?: return
        val physics3d = physics3d ?: return
        val inputState = gameObject.getComponent<InputStateComponent>() ?: return

        // Update grounded state from physics (for backward compatibility with InputStateComponent)
        isGrounded = checkIfGrounded(physics3d)
        inputState.isGrounded = isGrounded

        // Use event-driven movement state (updated by MovementInput events)
        // Fall back to InputStateComponent polling if events not received
        val inputDirection = if (movementMagnitude > 0.15f) {
            movementDirection
        } else {
            inputState.moveDirection
        }

        val isSprinting =
            inputState.sprintPressed || movementMagnitude > 0.65f || inputDirection.lengthSquared() > 0.42f

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