package com.pafoid.skate.game.camera

import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import org.joml.Vector3f
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

        // Physics clipping
        //val finalPos = handleClipping(targetPos, desiredPos)
        //camera.position.set(finalPos)
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

}
