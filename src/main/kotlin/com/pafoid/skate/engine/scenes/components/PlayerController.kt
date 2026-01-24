package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.physics2d.components.RigidBody2D
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.*

class PlayerController : Component() {
    var pushForce = 5.0f
    var steerSpeed = 2.0f
    var jumpImpulse = 10.0f
    
    @Transient private lateinit var rb: RigidBody2D

    override fun start() {
        rb = gameObject.getComponent<RigidBody2D>() ?: throw IllegalStateException("PlayerController requires RigidBody2D")
    }

    override fun update(dt: Float) {
        handleSteering(dt)
        handlePushing(dt)
        handleJumping()
    }

    private fun handleSteering(dt: Float) {
        var rotation = 0f
        
        // Keyboard
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            rotation += steerSpeed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            rotation -= steerSpeed
        }
        
        // Controller (Joystick 0)
        JoystickListener.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > 0) {
                val stickX = axes[0]
                if (Math.abs(stickX) > 0.1f) {
                    rotation -= stickX * steerSpeed
                }
            }
        }
        
        rb.angularVelocity = rotation
    }

    private fun handlePushing(dt: Float) {
        var multiplier = 0f
        
        // Keyboard
        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            multiplier = 1f
        }
        
        // Controller
        JoystickListener.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > 1) {
                val stickY = -axes[1] // Inverted stick Y
                if (stickY > 0.1f) {
                    multiplier = Math.max(multiplier, stickY)
                }
            }
        }

        if (multiplier > 0f) {
            val angle = Math.toRadians(gameObject.transform.rotation.z.toDouble())
            val force = Vector2f(Math.cos(angle).toFloat(), Math.sin(angle).toFloat()).mul(pushForce * multiplier)
            rb.addVelocity(force)
        }
    }

    private fun handleJumping() {
        var jump = KeyListener.keyBeginPress(GLFW_KEY_SPACE)
        
        // Controller
        JoystickListener.getButtons(GLFW_JOYSTICK_1)?.let { buttons ->
            if (buttons.size > 0 && buttons[0]) { // Button 0 is usually A/Cross
                jump = true
            }
        }

        if (jump) {
            rb.addImpulse(Vector2f(0f, jumpImpulse))
        }
    }
}