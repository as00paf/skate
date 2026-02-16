package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.utils.Interpolator
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW
import kotlin.math.atan2

class PlayerController : Component(), KoinComponent {
    private val inputProvider: IInputProvider by inject()
    private val sceneManager: SceneManager by inject()
    private val logger: LoggerService by inject()

    var jumpImpulse = 450.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f

    var walkSpeed = 2.5f
    var runSpeed = 7.5f
    var rotationSpeed = 10f
    val takeOffTime = 0.4f
    var jumpTimer = takeOffTime

    private val stateManager: PlayerStateManager? by lazy { gameObject.getComponent<PlayerStateManager>() }
    private val transform: Transform? by lazy { gameObject.getComponent<Transform>() }
    private val rb: IPhysicsBody3D? by lazy { gameObject.getComponent<IPhysicsBody3D>() }

    private val camera: Camera? by lazy { sceneManager.currentScene?.camera }
    private val physics3d: IPhysics3D? by lazy { sceneManager.currentScene?.physics3d }

    var lastSpeed = 1f
    var isGrounded = false
    var wasGrounded = false

    val desiredMoveDirection = Vector3f()
    private val desiredRotation = Quaternionf()

    override fun start() {
        rb ?: run { logger.logEngine("Could not find RigidBody for ${gameObject.name}", LogLevel.ERROR) }
        stateManager ?: run { logger.logEngine("Could not find StateManager for ${gameObject.name}", LogLevel.ERROR) }
    }

    private val smoothedInput = Vector3f()
    private val rawInput = Vector3f()
    private val smoothing = 12f
    private val threshold = 0.15f

    override fun update(dt: Float) {
        val body = rb ?: return
        val camera = camera ?: return
        val physics3d = physics3d ?: return

        // Detect Input direction
        rawInput.set(inputProvider.getMovementVector(GLFW.GLFW_JOYSTICK_1))
        smoothedInput.lerp(rawInput, dt * smoothing) // Exponential smoothing

        isGrounded = checkIfGrounded(physics3d)

        val isSprinting = rawInput.length() > 0.65f

        // Move if input is above threshold
        if (smoothedInput.length() > threshold) {
            val speed = getDesiredSpeed(isSprinting, dt)
            val motionData = MotionData(
                direction = getDesiredMoveDirection(camera.getForwardAndRight(), smoothedInput),
                speed = speed,
                targetYaw = atan2(desiredMoveDirection.x, desiredMoveDirection.z),
                rotationSpeed = dt * rotationSpeed,
                isGrounded = isGrounded,
                wasGrounded = wasGrounded
            )

            applyMotion(motionData, body)
        }

        handleJumping(isGrounded, dt)

        wasGrounded = isGrounded
    }

    private fun getDesiredSpeed(isSprintPressed: Boolean, dt: Float): Float {
        return if (isSprintPressed) {
            Interpolator.lerp(lastSpeed, runSpeed, dt * walkSpeed / 2)
        } else {
            Interpolator.lerp(lastSpeed, walkSpeed, dt * runSpeed)
        }
    }

    private fun getDesiredMoveDirection(camForwardAndRight: Pair<Vector3f, Vector3f>, input: Vector3f): Vector3f {
        desiredMoveDirection.zero()
        camForwardAndRight.first.mul(input.z, desiredMoveDirection)
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
     */
    var isJumping = false
    fun handleJumping(isGrounded: Boolean, dt: Float) {
        if (jumpTimer > 0) {
            jumpTimer -= dt
        }

        // Controller (Button A/Cross)
        val jumpPressed = inputProvider.buttonBeginPress(GLFW.GLFW_JOYSTICK_1, GamepadConstants.BUTTON_A)

        if (isGrounded && jumpPressed && !isJumping) {
            rb?.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
            isJumping = true
            jumpTimer = takeOffTime
        }

        // Land detection: If we are jumping, timer has expired, and we hit the ground
        if (isJumping && jumpTimer <= 0f && isGrounded) {
            isJumping = false
        }
    }

    private fun checkIfGrounded(physics3d: IPhysics3D): Boolean {
        val body = rb as? RigidBody3D ?: return false
        val originPosition = body.getWorldPosition()

        // Start the ray from the player's feet (below the collider)
        val feetY = originPosition.y + 0.15f
        val rayStart = Vector3f(originPosition.x, feetY, originPosition.z)

        // Ray goes down a small distance to detect ground
        val rayEnd = Vector3f(rayStart.x, rayStart.y - 0.3f, rayStart.z)

        // Exclude the player's own physics body from the raycast
        return physics3d.raycastClosest(rayStart, rayEnd, body) != null
    }


    /**
     * Displays a debug window with information about the player's state, stance, and velocity.
     */
    override fun imgui() {

    }
}