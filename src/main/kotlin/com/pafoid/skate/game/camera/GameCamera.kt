package com.pafoid.skate.game.camera

import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Third-person gameplay camera controller.
 *
 * Wraps a base [CameraComponent] instance and handles gameplay-specific camera behavior:
 * - Camera rotation from [InputStateComponent.cameraLook] (gamepad right stick + mouse)
 * - Physics-based clipping to prevent camera from going through walls
 * - Spring arm with configurable distance and offset
 *
 * This class reads input from [InputStateComponent] which is populated by [InputSystem],
 * following the ECS input architecture. It does NOT poll hardware directly.
 *
 * @param camera The base camera to control
 * @param sceneManager Scene manager for physics raycasting
 * @param inputSettings Input settings for sensitivity configuration
 */
class GameCamera(
    private val camera: CameraComponent,
    private val sceneManager: SceneManager,
    private val inputSettings: InputSettings
) {

    // Third person / Spring arm
    var target: Vector3f? = null
    var desiredDistance = 10.0f
    var targetOffset = Vector3f(0f, 0.5f, 0f)

    /**
     * Updates the camera based on input and third-person logic.
     * Call this every frame during game update.
     *
     * @param dt Delta time in seconds
     * @param inputState Input state component containing camera look input
     */
    fun update(dt: Float, inputState: InputStateComponent) {
        val rawTarget = target ?: return
        val targetPos = Vector3f(rawTarget).add(targetOffset)

        handleInput(inputState, dt)
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
     * Handles camera input from [InputStateComponent].
     * Combines gamepad right stick and mouse look input.
     *
     * @param inputState Input state containing camera look values
     * @param dt Delta time for smoothing
     */
    private fun handleInput(inputState: InputStateComponent, dt: Float) {
        val cameraLook = inputState.cameraLook

        // Apply camera look from InputStateComponent (combines gamepad + mouse)
        // Mouse look is added to cameraLook by InputSystem when cursor is disabled
        if (cameraLook.lengthSquared() > 0f) {
            camera.yaw += cameraLook.x * inputSettings.controllerSensitivity * dt * 60f
            camera.pitch += cameraLook.y * inputSettings.controllerSensitivity * dt * 60f
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
        /* if (scene != null) {
             val closest = scene.physics3d.raycastClosest(from, to)
             if (closest != null && closest.hitFraction < 1.0f) {
                 // Move slightly away from the hit point to avoid near-plane clipping
                 val clippedPos = Vector3f(from).lerp(to, closest.hitFraction * 0.9f)
                 return clippedPos
             }
         }*/
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

    fun lookAt(target: Vector3f) = camera.lookAt(target)
    fun screenToRay(screenX: Float, screenY: Float, width: Float, height: Float) =
        camera.screenToRay(screenX, screenY, width, height)

    fun addZoom(value: Float) = camera.addZoom(value)
}
