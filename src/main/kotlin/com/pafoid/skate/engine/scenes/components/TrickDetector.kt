package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.TrickManager
import com.pafoid.skate.skateboard.Stance
import org.koin.core.component.inject
import kotlin.math.abs

class TrickDetector : Component() {

    private val trickManager: TrickManager by inject()

    var accumulatedRotationX = 0f
    var accumulatedRotationY = 0f
    var accumulatedRotationZ = 0f

    private var lastGroundedState = true
    private var detectedTrick: String? = null

    private var rigidBody: RigidBody3D? = null
    private var skateboardPhysics: SkateboardPhysics? = null

    override fun start() {
        rigidBody = gameObject.getComponent<RigidBody3D>()
        skateboardPhysics = gameObject.getComponent<SkateboardPhysics>()
    }

    override fun update(dt: Float) {
        val rigidBody = rigidBody ?: return
        val skateboardPhysics = skateboardPhysics ?: return

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

        var baseTrickKey: String? = null

        // Kickflip (360 around X, positive)
        val absRX = abs(accumulatedRotationX)
        val absRY = abs(accumulatedRotationY)
        if (absRX >= 360f && accumulatedRotationX > 0f && absRX < 720f) {
            baseTrickKey = "trick.kickflip"
        }
        // Heelflip (360 around X, negative)
        else if (absRX >= 360f && accumulatedRotationX < 0f && absRX < 720f) {
            baseTrickKey = "trick.heelflip"
        }
        // 360 Pop Shuvit (360 around Y)
        else if (absRY in 360f..<540f) {
            baseTrickKey = "trick.360shoveit"
        }
        // Pop Shuvit (180 around Y)
        else if (absRY in 180f..<360f) {
            baseTrickKey = "trick.shoveit"
        }
        else {
            baseTrickKey = "trick.ollie"
        }

        val baseTrickName = trickManager.getTrickName(baseTrickKey)

        // Apply stance prefix
        val controller = gameObject.getComponent(PlayerController::class.java)
        if (controller != null) {
            val stance = controller.currentStance
            detectedTrick = if (stance == Stance.REGULAR) {
                baseTrickName
            } else {
                val stanceName = trickManager.getTrickName("stance.${stance.name.lowercase()}")
                "$stanceName $baseTrickName"
            }
        } else {
            detectedTrick = baseTrickName
        }
    }

    fun getDetectedTrick(): String? {
        return detectedTrick
    }
}
