package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.getAllComponents
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

class EditorCamera(
    private val engine: Engine,
    private val editorState: EditorInputState,
) {
    var camera = CameraComponent().also { it.name = "EditorCamera" }
    var transform = Transform(Vector3f(0f, 5f, 20f))

    private val scrollSensitivity = 0.01f
    private val rotationSensitivity = 0.01f
    private val moveSpeed = 0.01f
    private var lerpTime = 0.0f
    private var reset = false
    private var isRotating: Boolean = false

    fun init(scene: Scene) {
        val sceneCamera = scene.getAllComponents<CameraComponent>().firstOrNull { it.isDefault } ?: return
        camera.isOrthographic = sceneCamera.isOrthographic
        camera.fov = sceneCamera.fov
        camera.nearPlane = sceneCamera.nearPlane
        camera.farPlane = sceneCamera.farPlane
        camera.zoom = sceneCamera.zoom
    }

    fun update(dt: Float) {
        engine.systemManager.getSystem<TransformSystem>()?.updateTransform(transform)
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
                transform.rotation.y += dx * sensitivity
                transform.rotation.x += dy * sensitivity

                // Clamp pitch to avoid flipping
                if (transform.rotation.x > 89f) transform.rotation.x = 89f
                if (transform.rotation.x < -89f) transform.rotation.x = -89f
            }
        }

        val forward = camera.camForward
        val right = camera.camRight

        // WASD horizontal movement
        val moveDir = editorState.moveDirection
        if (moveDir.y > 0f) { // Forward (W)
            transform.translation.add(Vector3f(forward).mul(moveSpeed))
        }
        if (moveDir.y < 0f) { // Backward (S)
            transform.translation.sub(Vector3f(forward).mul(moveSpeed))
        }
        if (moveDir.x > 0f) { // Right (D)
            transform.translation.add(Vector3f(right).mul(moveSpeed))
        }
        if (moveDir.x < 0f) { // Left (A)
            transform.translation.sub(Vector3f(right).mul(moveSpeed))
        }

        // Vertical movement (Space/Shift)
        val verticalInput = editorState.verticalMovement
        if (verticalInput > 0f) { // Up (Space)
            transform.translation.y += moveSpeed
        }
        if (verticalInput < 0f) { // Down (Shift)
            transform.translation.y -= moveSpeed
        }
    }

    private fun handleReset(dt: Float) {
        if (editorState.resetPressed) {
            reset = true
        }

        if (reset) {
            transform.translation.lerp(Vector3f(0f, 0f, 20f), lerpTime)
            transform.rotation.lerp(Vector3f(0f, 0f, 0f), lerpTime)
            camera.zoom += ((1.0f - camera.zoom) * lerpTime)
            lerpTime += 0.1f * dt
            if (abs(transform.translation.x) <= 0.1f && abs(transform.translation.y) <= 0.1f) {
                transform.translation.set(0f, 0f, 20f)
                transform.rotation.set(0f, 0f, 0f)
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
                transform.rotation.y += dx * rotationSensitivity
                transform.rotation.x += dy * rotationSensitivity

                if (transform.rotation.x > 89f) transform.rotation.x = 89f
                if (transform.rotation.x < -89f) transform.rotation.x = -89f
            }
        }
    }

    private fun handleZoom() {
        val scroll = editorState.mouseScroll
        if (scroll != 0f && editorState.isInsideViewport) {
            val addValue = abs(scroll * scrollSensitivity).toDouble().pow(1.0 / camera.zoom)
            val zoomValue = max(camera.zoom + (addValue.toFloat() * -sign(scroll)), 0.1f)
            camera.zoom = zoomValue
        }
    }
}