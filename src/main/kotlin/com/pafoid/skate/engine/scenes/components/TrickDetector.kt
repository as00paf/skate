package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.physics3d.components.RigidBody3D
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

        var baseTrick: String? = null

        // Kickflip (360 around X, positive)
        if (abs(accumulatedRotationX) >= 360f && accumulatedRotationX > 0f && abs(accumulatedRotationX) < 720f) {
            baseTrick = "Kickflip"
        }
        // Heelflip (360 around X, negative)
        else if (abs(accumulatedRotationX) >= 360f && accumulatedRotationX < 0f && abs(accumulatedRotationX) < 720f) {
            baseTrick = "Heelflip"
        }
        // 360 Pop Shuvit (360 around Y)
        else if (abs(accumulatedRotationY) >= 360f && abs(accumulatedRotationY) < 540f) {
            baseTrick = "360 Shove-it"
        }
        // Pop Shuvit (180 around Y)
        else if (abs(accumulatedRotationY) >= 180f && abs(accumulatedRotationY) < 360f) {
            baseTrick = "Shove-it"
        }
        else {
            baseTrick = "Ollie"
        }

        // Apply stance prefix
        val controller = gameObject.getComponent(PlayerController::class.java)
        if (controller != null) {
            val stance = controller.currentStance
            detectedTrick = if (stance == com.pafoid.skate.skateboard.SkateStance.REGULAR) {
                baseTrick
            } else {
                "${stance.name.lowercase().replaceFirstChar { it.uppercase() }} $baseTrick"
            }
        } else {
            detectedTrick = baseTrick
        }
    }

    fun getDetectedTrick(): String? {
        return detectedTrick
    }
}
