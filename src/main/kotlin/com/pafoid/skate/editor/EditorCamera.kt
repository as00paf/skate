package com.pafoid.skate.editor

import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.systems.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Editor free-fly camera system.
 *
 * This system provides editor camera navigation with 6DOF movement:
 * - WASD: Horizontal movement (forward/back/strafe)
 * - Space/Shift: Vertical movement (up/down)
 * - RMB + Mouse: Camera look rotation
 * - MMB: Orbit rotation
 * - Scroll: Camera zoom
 * - Home: Reset camera position
 *
 * This system reads input from [EditorInputStateComponent] which is populated by
 * [com.pafoid.skate.engine.ecs.systems.InputSystem]. It does NOT poll hardware directly.
 *
 * ## Usage
 *
 * ```kotlin
 * // In LevelEditorSceneInitializer
 * val editorInput = EditorInputStateComponent()
 * scene.cameraEntity.addComponent(editorInput)
 * scene.addSystem(EditorCamera(scene.camera, editorInput))
 * ```
 */
class EditorCamera(
    private val camera: Camera,
    val editorInput: EditorInputStateComponent
) : System(priority = ExecutionPriority.EARLY) {

    private val scrollSensitivity = 0.1f
    private val rotationSensitivity = 0.1f
    private val moveSpeed = 0.1f
    private var lerpTime = 0.0f
    private var reset = false
    private var isRotating: Boolean = false

    override fun update(dt: Float) {
        editorUpdate(dt)
    }

    override fun editorUpdate(dt: Float) {
        handleFreeFlyMovement()
        handleRotation()
        handleZoom()
        handleReset(dt)
    }

    /**
     * Handles free-fly camera movement (WASD + Space/Shift).
     * This is the editor's primary navigation mode.
     */
    private fun handleFreeFlyMovement() {
        // Mouse look (RMB) - only when inside viewport
        if (editorInput.mouseLook.lengthSquared() > 0f && editorInput.isInsideViewport) {
            val sensitivity = 0.1f
            val dx = editorInput.mouseLook.x
            val dy = editorInput.mouseLook.y

            if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                camera.yaw += dx * sensitivity
                camera.pitch += dy * sensitivity

                // Clamp pitch to avoid flipping
                if (camera.pitch > 89f) camera.pitch = 89f
                if (camera.pitch < -89f) camera.pitch = -89f
            }
        }

        // Calculate forward and right vectors based on yaw (horizontal movement only)
        val forward = Vector3f(
            sin(Math.toRadians(camera.yaw.toDouble())).toFloat(),
            0f,
            -cos(Math.toRadians(camera.yaw.toDouble())).toFloat()
        ).normalize()

        val right = Vector3f(
            cos(Math.toRadians(camera.yaw.toDouble())).toFloat(),
            0f,
            sin(Math.toRadians(camera.yaw.toDouble())).toFloat()
        ).normalize()

        // WASD horizontal movement
        val moveDir = editorInput.moveDirection
        if (moveDir.y > 0f) { // Forward (W)
            camera.position.add(Vector3f(forward).mul(moveSpeed))
        }
        if (moveDir.y < 0f) { // Backward (S)
            camera.position.sub(Vector3f(forward).mul(moveSpeed))
        }
        if (moveDir.x > 0f) { // Right (D)
            camera.position.add(Vector3f(right).mul(moveSpeed))
        }
        if (moveDir.x < 0f) { // Left (A)
            camera.position.sub(Vector3f(right).mul(moveSpeed))
        }

        // Vertical movement (Space/Shift)
        val verticalInput = editorInput.verticalMovement
        if (verticalInput > 0f) { // Up (Space)
            camera.position.y += moveSpeed
        }
        if (verticalInput < 0f) { // Down (Shift)
            camera.position.y -= moveSpeed
        }
    }

    private fun handleReset(dt: Float) {
        // Reset is triggered via EditorInputStateComponent.resetPressed
        // This method handles the actual reset animation
        if (editorInput.resetPressed) {
            reset = true
        }

        if (reset) {
            camera.position.lerp(Vector3f(0f, 0f, 20f), lerpTime)
            camera.zoom += ((1.0f - camera.zoom) * lerpTime)
            lerpTime += 0.1f * dt
            if (abs(camera.position.x) <= 0.1f && abs(camera.position.y) <= 0.1f) {
                camera.position.set(0f, 0f, 20f)
                camera.zoom = 1f
                reset = false
                lerpTime = 0f
            }
        }
    }

    /**
     * Handles orbit rotation (MMB).
     */
    private fun handleRotation() {
        // Start orbit on MMB press
        if (editorInput.orbitPressed && editorInput.isInsideViewport) {
            isRotating = true
        }

        // Stop orbit on MMB release
        if (!editorInput.orbitHeld && isRotating) {
            isRotating = false
        }

        // Apply rotation while orbiting
        if (isRotating && editorInput.mouseLook.lengthSquared() > 0f) {
            val dx = editorInput.mouseLook.x
            val dy = editorInput.mouseLook.y

            if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                camera.yaw += dx * rotationSensitivity
                camera.pitch += dy * rotationSensitivity

                // Clamp pitch to avoid flipping
                if (camera.pitch > 89f) camera.pitch = 89f
                if (camera.pitch < -89f) camera.pitch = -89f
            }
        }
    }

    /**
     * Handles camera zoom via scroll wheel.
     */
    private fun handleZoom() {
        val scroll = editorInput.mouseScroll
        if (scroll != 0f && editorInput.isInsideViewport) {
            val addValue = abs(scroll * scrollSensitivity).toDouble().pow(1.0 / camera.zoom)
            camera.addZoom((addValue.toFloat() * -sign(scroll)))
        }
    }

    override fun imgui() {

    }
}