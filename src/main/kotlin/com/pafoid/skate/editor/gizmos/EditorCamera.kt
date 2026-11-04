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
        handleFreeFlyMovement(dt)
        handleRotation()
        handleZoom()
        handleReset(dt)
    }

    private fun handleFreeFlyMovement(dt: Float) {
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

        // WASD + up/down, in world units per second (frame-rate independent)
        flyStep(editorState.moveDirection.x, editorState.moveDirection.y, editorState.verticalMovement, dt)
    }

    /**
     * Moves the camera along its right ([right] in -1..1), forward ([forward]) and world-up ([up]) axes at
     * [EditorCameraMotion.FLY_SPEED] for one frame of [dt] seconds.
     */
    fun flyStep(right: Float, forward: Float, up: Float, dt: Float) {
        val distance = EditorCameraMotion.step(EditorCameraMotion.FLY_SPEED, dt)
        if (distance == 0f) return
        if (forward != 0f) transform.translation.add(Vector3f(camera.camForward).mul(forward * distance))
        if (right != 0f) transform.translation.add(Vector3f(camera.camRight).mul(right * distance))
        if (up != 0f) transform.translation.y += up * distance
    }

    private fun handleReset(dt: Float) {
        if (editorState.resetPressed) {
            editorState.resetPressed = false
            reset = true
        }
        if (reset) reset = !resetStep(dt)
    }

    /** Advances the reset-view transition by [dt] seconds; returns true once the camera has arrived. */
    fun resetStep(dt: Float): Boolean {
        val t = EditorCameraMotion.smoothing(EditorCameraMotion.RESET_SHARPNESS, dt)
        transform.translation.lerp(RESET_TRANSLATION, t)
        transform.rotation.lerp(RESET_ROTATION, t)
        camera.zoom += (RESET_ZOOM - camera.zoom) * t
        val arrived = transform.translation.distance(RESET_TRANSLATION) <= EditorCameraMotion.RESET_EPSILON &&
            transform.rotation.distance(RESET_ROTATION) <= EditorCameraMotion.RESET_EPSILON &&
            abs(camera.zoom - RESET_ZOOM) <= EditorCameraMotion.RESET_EPSILON
        if (arrived) {
            transform.translation.set(RESET_TRANSLATION)
            transform.rotation.set(RESET_ROTATION)
            camera.zoom = RESET_ZOOM
        }
        return arrived
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

    companion object {
        private val RESET_TRANSLATION = Vector3f(0f, 0f, 20f)
        private val RESET_ROTATION = Vector3f(0f, 0f, 0f)
        private const val RESET_ZOOM = 1f
    }
}
