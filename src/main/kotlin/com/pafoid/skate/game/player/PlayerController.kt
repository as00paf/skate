package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.utils.Interpolator.lerpAngle
import org.joml.Matrix4f
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

    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f

    var walkSpeed = 2f

    private val stateManager: PlayerStateManager? by lazy { gameObject.getComponent<PlayerStateManager>() }
    private val rb: IPhysicsBody3D? by lazy { gameObject.getComponent<IPhysicsBody3D>() }
    private val camera: Camera? by lazy { sceneManager.currentScene?.camera }

    private var lastVelocity = Vector3f()

    var desiredMoveDirection = Vector3f()

    override fun start() {
        rb ?: run { logger.logEngine("Could not find RigidBody for ${gameObject.name}", LogLevel.ERROR) }
        stateManager ?: run { logger.logEngine("Could not find StateManager for ${gameObject.name}", LogLevel.ERROR) }
    }

    private val smoothedInput = Vector3f()
    private val rawInput = Vector3f()

    override fun update(dt: Float) {
        // Detect Input direction
        rawInput.set(inputProvider.getMovementVector(GLFW.GLFW_JOYSTICK_1))

        // Exponential smoothing
        val smoothing = 12f
        smoothedInput.lerp(rawInput, dt * smoothing)

        // Move if input is above threshold
        val threshold = 0.15f
        if (smoothedInput.length() > threshold) {
            // Calculate movement relative to camera (should be moved out of here and vectors should be reused)
            val viewInv = camera?.getInverseView() ?: Matrix4f()
            val camForward = Vector3f(0f, 0f, -1f)
            viewInv.transformDirection(camForward)
            camForward.y = 0f
            camForward.normalize()

            val camRight = Vector3f(1f, 0f, 0f)
            viewInv.transformDirection(camRight)
            camRight.y = 0f
            camRight.normalize()

            desiredMoveDirection.zero()
            camForward.mul(smoothedInput.z, desiredMoveDirection)
            val rightPart = Vector3f(camRight).mul(smoothedInput.x)
            desiredMoveDirection.add(rightPart)

            desiredMoveDirection.normalize()

            val body = rb ?: return
            val velocity = body.linearVelocity

            velocity.x = desiredMoveDirection.x * walkSpeed
            velocity.z = desiredMoveDirection.z * walkSpeed

            body.linearVelocity = velocity
            lastVelocity.set(velocity)

            // --- Rotation ---
            val targetYaw = atan2(desiredMoveDirection.x, desiredMoveDirection.z)
            val rotation = body.getRotation()
            val currentYaw = atan2(
                2f * (rotation.w * rotation.y + rotation.x * rotation.z),
                1f - 2f * (rotation.y * rotation.y + rotation.z * rotation.z)
            )
            val rotationSpeed = 10f
            val newYaw = lerpAngle(currentYaw, targetYaw, dt * rotationSpeed)

            val newRotation = Quaternionf().rotateY(newYaw)
            body.setRotation(newRotation)
        }
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
     * Handles jump input, applying a vertical impulse for an ollie.
     */
    fun handleJumping(isGrounded: Boolean) {
        var jump = inputProvider.keyBeginPress(GLFW.GLFW_KEY_SPACE)

        // Controller (Button A/Cross)
        inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)?.let { buttons ->
            if (buttons.size > GamepadConstants.BUTTON_A && buttons[GamepadConstants.BUTTON_A]) {
                jump = true
            }
        }

        if (jump && isGrounded) {
            rb?.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
        }
    }


    /**
     * Displays a debug window with information about the player's state, stance, and velocity.
     */
    override fun imgui() {

    }
}