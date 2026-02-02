package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.CameraPreset
import com.pafoid.skate.engine.utils.MImGui
import imgui.ImGui
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_KEY_HOME
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class EditorCamera(private val camera: Camera) : Component(), KoinComponent {
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()

    private val scrollSensitivity = 0.1f
    private var lerpTime = 0.0f
    private var reset = false
    private var isRotating: Boolean = false
    private val rotationSensitivity = 0.1f

    override fun editorUpdate(dt: Float) {
        handleRotation()
        handleZoom()
        handleReset(dt)
    }

    private fun handleReset(dt:Float) {
        if (keyListener.isKeyPressed(GLFW_KEY_HOME)) {
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
        if (!mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_MIDDLE, true) && isRotating) { // Middle mouse button released
            isRotating = false
        } else if (mouseListener.mouseButtonBeginPress(GLFW_MOUSE_BUTTON_MIDDLE) && mouseListener.isInsideViewport()) {
            isRotating = true
        }

        if (isRotating) {
            val dx = mouseListener.getDx()
            val dy = mouseListener.getDy()

            if (abs(dx) > 0.01f || abs(dy) > 0.01f) { // Only update if there's actual mouse movement
                camera.yaw += dx * rotationSensitivity
                camera.pitch += dy * rotationSensitivity

                // Clamp pitch to avoid flipping
                if (camera.pitch > 89f) camera.pitch = 89f
                if (camera.pitch < -89f) camera.pitch = -89f
            }
        }
    }

    private fun handleZoom() {
        val scroll = mouseListener.getScrollY()
        if (scroll != 0f && mouseListener.isInsideViewport()) {
            val addValue = abs(scroll * scrollSensitivity).toDouble().pow(1.0 / camera.zoom)
            camera.addZoom((addValue.toFloat() * -sign(scroll)))
        }
    }

    override fun imgui() {

    }
}