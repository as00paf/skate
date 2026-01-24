package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.KeyListener
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
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            rotation += steerSpeed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            rotation -= steerSpeed
        }
        
        rb.angularVelocity = rotation
    }

    private fun handlePushing(dt: Float) {
        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            val angle = Math.toRadians(gameObject.transform.rotation.z.toDouble())
            val force = Vector2f(Math.cos(angle).toFloat(), Math.sin(angle).toFloat()).mul(pushForce)
            rb.addVelocity(force)
        }
    }

    private fun handleJumping() {
        if (KeyListener.keyBeginPress(GLFW_KEY_SPACE)) {
            rb.addImpulse(Vector2f(0f, jumpImpulse))
        }
    }
}