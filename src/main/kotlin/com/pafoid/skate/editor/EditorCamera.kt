package com.pafoid.skate.editor

import com.pafoid.skate.engine.ecs.components.EditorInputState
import com.pafoid.skate.engine.ecs.systems.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

class EditorCamera(
    private val camera: Camera,
    private val mouseListener: MouseListener,
    private val inputProvider: IInputProvider,
    private val editorState: EditorInputState,
) : System(priority = ExecutionPriority.EARLY) {

    private val scrollSensitivity = 0.1f
    private val rotationSensitivity = 0.1f
    private val moveSpeed = 0.01f
    private var lerpTime = 0.0f
    private var reset = false
    private var isRotating: Boolean = false

    override fun update(dt: Float) {
        pollEditorKeyboardInput()
        pollEditorMouseInput()

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
        // Reset is triggered via EditorInputStateComponent.resetPressed
        // This method handles the actual reset animation
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

    /**
     * Handles orbit rotation (MMB).
     */
    private fun handleRotation() {
        // Start orbit on MMB press
        if (editorState.orbitPressed && editorState.isInsideViewport) {
            isRotating = true
        }

        // Stop orbit on MMB release
        if (!editorState.orbitHeld && isRotating) {
            isRotating = false
        }

        // Apply rotation while orbiting
        if (isRotating && editorState.mouseLook.lengthSquared() > 0f) {
            val dx = editorState.mouseLook.x
            val dy = editorState.mouseLook.y

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
        val scroll = editorState.mouseScroll
        if (scroll != 0f && editorState.isInsideViewport) {
            val addValue = abs(scroll * scrollSensitivity).toDouble().pow(1.0 / camera.zoom)
            camera.addZoom((addValue.toFloat() * -sign(scroll)))
        }
    }

    private fun pollEditorKeyboardInput() {
        if (!editorState.isFocused) return

        val moveInput = Vector2f()

        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_W)) moveInput.y += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_S)) moveInput.y -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_A)) moveInput.x -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_D)) moveInput.x += 1f

        if (moveInput.lengthSquared() > 1f) {
            moveInput.normalize()
        }

        editorState.moveDirection.set(moveInput)

        var verticalInput = 0f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_SPACE)) verticalInput += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) verticalInput -= 1f

        editorState.verticalMovement = verticalInput

        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_HOME)) {
            editorState.resetPressed = true
        }
    }

    private fun pollEditorMouseInput() {
        editorState.isInsideViewport = mouseListener.isInsideViewport()

        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()

        if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && editorState.isInsideViewport) {
            editorState.mouseLook.set(dx, dy)
        } else if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true) && editorState.isInsideViewport
        ) {
            editorState.mouseLook.set(dx, dy)
        } else {
            editorState.mouseLook.set(0f, 0f)
        }

        editorState.orbitPressed =
            mouseListener.mouseButtonBeginPress(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && editorState.isInsideViewport
        editorState.orbitHeld =
            mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true) && editorState.isInsideViewport

        if (editorState.isInsideViewport) {
            editorState.mouseScroll = mouseListener.getScrollY()
        }
    }
}