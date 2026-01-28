package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Vector3f
import kotlin.math.abs

class TrickDetector : Component() {

    private var accumulatedRotationX = 0f
    private var accumulatedRotationY = 0f
    private var accumulatedRotationZ = 0f

    private var lastGroundedState = true
    private var detectedTrick: String? = null

    private lateinit var rigidBody: RigidBody3D
    private lateinit var skateboardPhysics: SkateboardPhysics

    override fun start() {
        rigidBody = gameObject.getComponent(RigidBody3D::class.java)!!
        skateboardPhysics = gameObject.getComponent(SkateboardPhysics::class.java)!!
    }

    override fun update(dt: Float) {
        if (skateboardPhysics.isGrounded) {
            // Grounded, reset trick detection
            accumulatedRotationX = 0f
            accumulatedRotationY = 0f
            accumulatedRotationZ = 0f
            detectedTrick = null
        } else {
            // In air, accumulate rotation and detect trick
            val angularVelocity = rigidBody.angularVelocity
            accumulatedRotationX += Math.toDegrees(angularVelocity.x.toDouble()).toFloat() * dt
            accumulatedRotationY += Math.toDegrees(angularVelocity.y.toDouble()).toFloat() * dt
            accumulatedRotationZ += Math.toDegrees(angularVelocity.z.toDouble()).toFloat() * dt

            detectTrick()
        }
    }

    private fun detectTrick() {
        // Simple 360 flip detection for now (rotation around X axis)
        if (abs(accumulatedRotationX) >= 360f && abs(accumulatedRotationX) < 720f) {
            detectedTrick = "360 Flip"
        } else {
            detectedTrick = null
        }
    }

    fun getDetectedTrick(): String? {
        return detectedTrick
    }
}
