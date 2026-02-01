package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.GLFW_KEY_HOME
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class EditorCamera(private val camera: Camera) : Component() {


    private var isPanning = false
    private val dragSensitivity = 30f
    private val scrollSensitivity = 0.1f
    private var lerpTime = 0.0f
    private var reset = false

    override fun editorUpdate(dt: Float) {

        // Panning Logic
        if (MouseListener.mouseButtonBeginPress(GLFW_MOUSE_BUTTON_MIDDLE)) {
            isPanning = true
        } else if (!MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_MIDDLE)) {
            isPanning = false
        }

        if (isPanning) {
            val worldDeltaX = MouseListener.getWorldDx()
            val worldDeltaY = MouseListener.getWorldDy()

            // Translate camera position by the world delta scaled by sensitivity.
            camera.position.x -= worldDeltaX * dragSensitivity
            camera.position.y -= worldDeltaY * dragSensitivity
        }


        if (MouseListener.getScrollY() != 0f) {
            val addValue = abs(MouseListener.getScrollY() * scrollSensitivity).toDouble().pow(1.0 / camera.zoom)
            camera.addZoom((addValue.toFloat() * -sign(MouseListener.getScrollY())))
        }

        if (KeyListener.isKeyPressed(GLFW_KEY_HOME)) {
            reset = true
        }

        if (reset) {
            camera.position.lerp(org.joml.Vector3f(0f, 0f, 20f), lerpTime)
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
}