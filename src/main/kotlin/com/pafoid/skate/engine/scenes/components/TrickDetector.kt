package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Vector3f
import kotlin.math.abs

class TrickDetector : Component() {

    var accumulatedRotationX = 0f
    var accumulatedRotationY = 0f
    var accumulatedRotationZ = 0f

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

    internal fun detectTrick() {
        // Reset detected trick each frame before re-evaluation
        detectedTrick = null

        // Kickflip (360 around X, positive)
        if (abs(accumulatedRotationX) >= 360f && accumulatedRotationX > 0f && abs(accumulatedRotationX) < 720f) {
            detectedTrick = "Kickflip"
            return
        }

        // Heelflip (360 around X, negative)
        if (abs(accumulatedRotationX) >= 360f && accumulatedRotationX < 0f && abs(accumulatedRotationX) < 720f) {
            detectedTrick = "Heelflip"
            return
        }

        // Pop Shuvit (180 around Y)
        if (abs(accumulatedRotationY) >= 180f && abs(accumulatedRotationY) < 360f) {
            detectedTrick = "Pop Shuvit"
            return
        }

        // 360 Pop Shuvit (360 around Y)
        if (abs(accumulatedRotationY) >= 360f && abs(accumulatedRotationY) < 540f) { // Limit for a single 360
            detectedTrick = "360 Pop Shuvit"
            return
        }

        // TODO: Add detection for other tricks involving Z rotation (e.g., Varial)
        // TODO: Add detection for multi-axis tricks (e.g., Varial Kickflip)

    }

    fun getDetectedTrick(): String? {
        return detectedTrick
    }
}
