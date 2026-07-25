package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class EditorCamera(
    val camera: Camera,
    private val editorState: EditorInputState,
) {
    private val scrollSensitivity = 0.01f
    private val rotationSensitivity = 0.01f
    private val moveSpeed = 0.01f
    private var lerpTime = 0.0f
    private var reset = false
    private var isRotating: Boolean = false

    fun init(scene: Scene) {
        camera.position.set(scene.camera.position)
        camera.yaw = scene.camera.yaw
        camera.pitch = scene.camera.pitch
        camera.roll = scene.camera.roll
        camera.isOrthographic = scene.camera.isOrthographic
    }

    fun update(dt: Float) {
        handleFreeFlyMovement()
        handleRotation()
        handleZoom()
        handleReset(dt)
    }

    private fun handleFreeFlyMovement() {
        // Mouse look (RMB) - only when inside viewport
        if (editorState.mouseLook.lengthSquared() > 0f && editorState.isInsideViewport) {
            val sensitivity = 0.1f
            val dx = editorState.mouseLook.x
            val dy = editorState.mouseLook.y

            if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                camera.yaw += dx * sensitivity
                camera.pitch += dy * sensitivity

                // Clamp pitch to avoid flipping
                if (camera.pitch > 89f) camera.pitch = 89f
                if (camera.pitch < -89f) camera.pitch = -89f
            }
        }

        val forward = camera.camForward
        val right = camera.camRight

        // WASD horizontal movement
        val moveDir = editorState.moveDirection
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
        val verticalInput = editorState.verticalMovement
        if (verticalInput > 0f) { // Up (Space)
            camera.position.y += moveSpeed
        }
        if (verticalInput < 0f) { // Down (Shift)
            camera.position.y -= moveSpeed
        }
    }

    private fun handleReset(dt: Float) {
        if (editorState.resetPressed) {
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

    private fun handleRotation() {
        if (editorState.orbitPressed && editorState.isInsideViewport) {
            isRotating = true
        }

        if (!editorState.orbitHeld && isRotating) {
            isRotating = false
        }

        if (isRotating && editorState.mouseLook.lengthSquared() > 0f) {
            val dx = editorState.mouseLook.x
            val dy = editorState.mouseLook.y

            if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                camera.yaw += dx * rotationSensitivity
                camera.pitch += dy * rotationSensitivity

                if (camera.pitch > 89f) camera.pitch = 89f
                if (camera.pitch < -89f) camera.pitch = -89f
            }
        }
    }

    private fun handleZoom() {
        val scroll = editorState.mouseScroll
        if (scroll != 0f && editorState.isInsideViewport) {
            val addValue = abs(scroll * scrollSensitivity).toDouble().pow(1.0 / camera.zoom)
            camera.addZoom((addValue.toFloat() * -sign(scroll)))
        }
    }
}