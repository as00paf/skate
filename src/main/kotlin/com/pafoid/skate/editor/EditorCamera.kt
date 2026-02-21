package com.pafoid.skate.editor

import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_D
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_W
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

class EditorCamera(
    private val camera: Camera,
    keyListener: KeyListener,
    mouseListener: MouseListener
) : System(priority = -50) {  // Early system - input processing

    private val keyListener: KeyListener = keyListener
    private val mouseListener: MouseListener = mouseListener

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
     * Handles free-fly camera movement (WASD + mouse look).
     * This is the editor's primary navigation mode when not using third-person camera.
     */
    private fun handleFreeFlyMovement() {
        // Mouse rotation (when cursor is disabled)
        if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && mouseListener.isInsideViewport()) {
            val sensitivity = 0.1f
            val dx = mouseListener.getDx()
            val dy = mouseListener.getDy()

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

        // WASD movement
        if (keyListener.isKeyPressed(GLFW_KEY_W)) {
            camera.position.add(Vector3f(forward).mul(moveSpeed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_S)) {
            camera.position.sub(Vector3f(forward).mul(moveSpeed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_D)) {
            camera.position.add(Vector3f(right).mul(moveSpeed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_A)) {
            camera.position.sub(Vector3f(right).mul(moveSpeed))
        }
        // Vertical movement
        if (keyListener.isKeyPressed(GLFW_KEY_SPACE)) {
            camera.position.y += moveSpeed
        }
        if (keyListener.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            camera.position.y -= moveSpeed
        }
    }

    private fun handleReset(dt: Float) {
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_HOME)) {
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
        if (!mouseListener.isMouseButtonDown(
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                true
            ) && isRotating
        ) { // Middle mouse button released
            isRotating = false
        } else if (mouseListener.mouseButtonBeginPress(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && mouseListener.isInsideViewport()) {
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