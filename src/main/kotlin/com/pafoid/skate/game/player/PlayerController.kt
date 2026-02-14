package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW

class PlayerController : Component(), KoinComponent {
    private val inputProvider: IInputProvider by inject()
    private val sceneManager: SceneManager by inject()
    private val logger: LoggerService by inject()

    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f

    val walkSpeed = 2f

    private val stateManager: PlayerStateManager? by lazy { gameObject.getComponent<PlayerStateManager>() }
    private val rb: IPhysicsBody3D? by lazy { gameObject.getComponent<IPhysicsBody3D>() }

    private var lastVelocity = Vector3f()

    var desiredMoveDirection = Vector3f()

    override fun start() {
        rb ?: run { logger.logEngine("Could not find RigidBody for ${gameObject.name}", LogLevel.ERROR) }
        stateManager ?: run { logger.logEngine("Could not find StateManager for ${gameObject.name}", LogLevel.ERROR) }
    }

    override fun update(dt: Float) {
        val currentState = stateManager?.currentState ?: return
        if (currentState == PlayerState.WALKING || currentState == PlayerState.IDLE) {
            val scene = sceneManager.currentScene ?: return
            val camera = scene.camera

            // Detect Input direction
            val moveInput = inputProvider.getMovementVector(GLFW.GLFW_JOYSTICK_1)

            // Move if input is above threshold
            val threshold = 0.5f
            if (moveInput.length() > threshold) {
                // Calculate movement relative to camera
                val viewInv = camera.getInverseView()
                val camForward = Vector3f(0f, 0f, -1f)
                viewInv.transformDirection(camForward)
                camForward.y = 0f
                camForward.normalize()

                val camRight = Vector3f(1f, 0f, 0f)
                viewInv.transformDirection(camRight)
                camRight.y = 0f
                camRight.normalize()

                desiredMoveDirection.zero()
                camForward.mul(moveInput.z, desiredMoveDirection)
                val rightPart = Vector3f(camRight).mul(moveInput.x)
                desiredMoveDirection.add(rightPart)

                // Apply movement to rigid body
                val force = desiredMoveDirection.mul(walkSpeed)
                rb?.applyImpulse(force)
            }

            val vel = rb?.linearVelocity
            if (vel != null) {
                lastVelocity.set(vel.x, vel.y, vel.z)
            }
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