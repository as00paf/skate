package com.pafoid.skate.game.trick

import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.player.PlayerStateManager
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import com.pafoid.skate.game.skateboard.Stance
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

class TrickDetector : Component(), KoinComponent {

    private val trickManager: TrickManager by inject()

    var accumulatedRotationX = 0f
    var accumulatedRotationY = 0f
    var accumulatedRotationZ = 0f

    private var lastGroundedState = true
    private var detectedTrick: String? = null

    private var rigidBody: RigidBody3D? = null
    private var physicsComponent: PhysicsComponent? = null
    private var skateboardPhysics: SkateboardPhysics? = null

    override fun start() {
        rigidBody = gameObject.getComponent<RigidBody3D>()
        physicsComponent = gameObject.getComponent<PhysicsComponent>()
        skateboardPhysics = gameObject.getComponent<SkateboardPhysics>()
    }

    override fun update(dt: Float) {
        val physicsComponent = physicsComponent ?: return
        val skateboardPhysics = skateboardPhysics ?: return

        if (skateboardPhysics.isGrounded) {
            // Grounded, reset trick detection
            accumulatedRotationX = 0f
            accumulatedRotationY = 0f
            accumulatedRotationZ = 0f
            detectedTrick = null
        } else {
            // In air, accumulate rotation and detect trick
            // Read angular velocity from PhysicsComponent instead of directly from rigidBody
            val angularVelocity = physicsComponent.angularVelocity
            accumulatedRotationX += Math.toDegrees(angularVelocity.x.toDouble()).toFloat() * dt
            accumulatedRotationY += Math.toDegrees(angularVelocity.y.toDouble()).toFloat() * dt
            accumulatedRotationZ += Math.toDegrees(angularVelocity.z.toDouble()).toFloat() * dt

            detectTrick()
        }
    }

    /**
     * Analyzes the accumulated rotations around the skateboard's local axes to identify a trick.
     * It checks for specific rotation thresholds for tricks like Kickflips, Heelflips, and Shove-its.
     * The identified trick name is then combined with a stance prefix (e.g., "Fakie", "Switch")
     * based on the PlayerController's current state. The result is stored in the `detectedTrick` property.
     */
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
        val stateManager = gameObject.getComponent<PlayerStateManager>()
        if (stateManager != null) {
            val stance = stateManager.currentStance
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
