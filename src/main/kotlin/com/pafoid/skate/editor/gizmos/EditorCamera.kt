package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.settings.EditorCameraSettings
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
    /** Move speed and look sensitivity from the editor settings (EDT-57). */
    private val settings: () -> EditorCameraSettings = { EditorCameraSettings() },
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
        handleFreeFlyMovement(dt)
        handleRotation()
        handleZoom()
        handleReset(dt)
        // After moving (EDT-44): CameraManager builds the next view from worldMatrix, so it must hold this frame's
        // position, or the view trails the camera by a frame while the editor visuals use the current one.
        engine.systemManager.getSystem<TransformSystem>()?.updateTransform(transform)
    }

    private fun handleFreeFlyMovement(dt: Float) {
        // Mouse look (RMB) - only when inside viewport
        if (editorState.mouseLook.lengthSquared() > 0f && editorState.isInsideViewport) {
            val sensitivity = LOOK_SENSITIVITY * settings().lookSensitivity
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
     * the move speed setting ([EditorCameraSettings.moveSpeed], [EditorCameraMotion.FLY_SPEED] by default) for one
     * frame of [dt] seconds.
     */
    fun flyStep(right: Float, forward: Float, up: Float, dt: Float) {
        val distance = EditorCameraMotion.step(settings().moveSpeed, dt)
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
                transform.rotation.y += dx * rotationSensitivity * settings().lookSensitivity
                transform.rotation.x += dy * rotationSensitivity * settings().lookSensitivity

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
        /** Mouse-look degrees per pixel at a look sensitivity of 1. */
        private const val LOOK_SENSITIVITY = 0.1f
        private val RESET_TRANSLATION = Vector3f(0f, 0f, 20f)
        private val RESET_ROTATION = Vector3f(0f, 0f, 0f)
        private const val RESET_ZOOM = 1f
    }
}
