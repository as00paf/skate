package com.pafoid.skate.game.camera

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_X
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_Y
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Third-person gameplay camera controller.
 *
 * Wraps a base [Camera] instance and handles gameplay-specific camera behavior:
 * - Gamepad right stick rotation for third-person view
 * - Mouse rotation when cursor is disabled (gameplay mode)
 * - Physics-based clipping to prevent camera from going through walls
 * - Spring arm with configurable distance and offset
 *
 * This class is responsible for updating the wrapped camera's position and orientation
 * based on player input and scene geometry.
 *
 * @param camera The base camera to control
 * @param inputProvider Input provider for gamepad and mouse
 * @param sceneManager Scene manager for physics raycasting
 */
class GameCamera(
    private val camera: Camera,
    private val inputProvider: IInputProvider,
    private val sceneManager: SceneManager
) {
    private val mouseListener: MouseListener = MouseListener()

    // Third person / Spring arm
    var target: Vector3f? = null
    var desiredDistance = 10.0f
    var targetOffset = Vector3f(0f, 0.5f, 0f)

    // Camera rotation
    private var sensitivity = 0.1f
    private var controllerSensitivity = 2.0f

    /**
     * Updates the camera based on input and third-person logic.
     * Call this every frame during game update.
     *
     * @param dt Delta time in seconds
     */
    fun update(dt: Float) {
        val rawTarget = target ?: return
        val targetPos = Vector3f(rawTarget).add(targetOffset)

        handleInput()
        clampPitch()

        // Calculate camera position
        val horizontalDist = desiredDistance * cos(Math.toRadians(camera.pitch.toDouble())).toFloat()
        val verticalDist = desiredDistance * sin(Math.toRadians(camera.pitch.toDouble())).toFloat()

        val offsetX = horizontalDist * sin(Math.toRadians(camera.yaw.toDouble())).toFloat()
        val offsetZ = horizontalDist * cos(Math.toRadians(camera.yaw.toDouble())).toFloat()

        val desiredPos = Vector3f(targetPos.x - offsetX, targetPos.y + verticalDist, targetPos.z + offsetZ)

        // Physics clipping
        val finalPos = handleClipping(targetPos, desiredPos)
        camera.position.set(finalPos)
    }

    /**
     * Handles gamepad and mouse input for camera rotation.
     */
    private fun handleInput() {
        // Mouse Rotation (when cursor is disabled - gameplay mode)
        if (inputProvider.isCursorDisabled()) {
            camera.yaw += mouseListener.getDx() * sensitivity
            camera.pitch += mouseListener.getDy() * sensitivity
        }

        // Right Stick Rotation (Gamepad)
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_RIGHT_Y) {
                val rsX = axes[AXIS_RIGHT_X]
                val rsY = axes[AXIS_RIGHT_Y]

                if (abs(rsX) > 0.1f) camera.yaw += rsX * controllerSensitivity
                if (abs(rsY) > 0.1f) camera.pitch += rsY * controllerSensitivity
            }
        }
    }

    /**
     * Clamps pitch to prevent camera flipping.
     */
    private fun clampPitch() {
        if (camera.pitch > 89f) camera.pitch = 89f
        if (camera.pitch < -89f) camera.pitch = -89f
    }

    /**
     * Handles physics-based clipping to prevent camera from going through walls.
     *
     * @param from Start position (target position)
     * @param to Desired end position (camera position)
     * @return Final camera position (clipped if collision detected)
     */
    private fun handleClipping(from: Vector3f, to: Vector3f): Vector3f {
        val scene = sceneManager.currentScene
        if (scene != null) {
            val closest = scene.physics3d.raycastClosest(from, to)
            if (closest != null && closest.hitFraction < 1.0f) {
                // Move slightly away from the hit point to avoid near-plane clipping
                val clippedPos = Vector3f(from).lerp(to, closest.hitFraction * 0.9f)
                return clippedPos
            }
        }
        return to
    }

    // Delegate common camera operations to the wrapped camera

    val position: Vector3f get() = camera.position
    var pitch: Float
        get() = camera.pitch
        set(value) {
            camera.pitch = value
        }
    var yaw: Float
        get() = camera.yaw
        set(value) {
            camera.yaw = value
        }
    var roll: Float
        get() = camera.roll
        set(value) {
            camera.roll = value
        }
    var fov: Float
        get() = camera.fov
        set(value) {
            camera.fov = value
        }
    var zoom: Float
        get() = camera.zoom
        set(value) {
            camera.zoom = value
        }
    var viewportWidth: Int
        get() = camera.viewportWidth
        set(value) {
            camera.viewportWidth = value
        }
    var viewportHeight: Int
        get() = camera.viewportHeight
        set(value) {
            camera.viewportHeight = value
        }

    fun createProjectionMatrix() = camera.createProjectionMatrix()
    fun createViewMatrix() = camera.createViewMatrix()
    fun getInverseView() = camera.getInverseView()
    fun getInverseProjection() = camera.getInverseProjection()
    fun lookAt(target: Vector3f) = camera.lookAt(target)
    fun screenToRay(screenX: Float, screenY: Float, width: Float, height: Float) =
        camera.screenToRay(screenX, screenY, width, height)

    fun getForwardAndRight() = camera.getForwardAndRight()
    fun addZoom(value: Float) = camera.addZoom(value)
}
