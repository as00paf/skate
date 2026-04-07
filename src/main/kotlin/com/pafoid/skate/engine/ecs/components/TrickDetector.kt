package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.events.Takeoff
import com.pafoid.skate.engine.events.TrickCompleted
import com.pafoid.skate.engine.events.TrickDetected
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import com.pafoid.skate.game.skateboard.Stance
import com.pafoid.skate.game.trick.TrickManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

/**
 * Component responsible for detecting tricks based on rotation during airborne state.
 *
 * Subscribes to physics events instead of polling:
 * - [Takeoff] - Start trick detection (reset rotation accumulators)
 * - [Landing] - Complete trick detection (publish TrickCompleted event)
 *
 * ## Usage
 *
 * ```kotlin
 * val trickDetector = gameObject.addComponent(TrickDetector())
 *
 * // Subscribe to trick events
 * eventSystem.subscribe<TrickDetected> { event ->
 *     println("Trick detected: ${event.trickName}")
 * }
 * eventSystem.subscribe<TrickCompleted> { event ->
 *     println("Trick completed: ${event.trickName}, Score: ${event.score}")
 * }
 * ```
 */
@Serializable
class TrickDetector : Component(), KoinComponent {

    private val trickManager: TrickManager by inject()
    private val sceneManager: SceneManager by inject()

    var accumulatedRotationX = 0f
    var accumulatedRotationY = 0f
    var accumulatedRotationZ = 0f

    private var isInAir = false
    private var detectedTrick: String? = null
    private var trickInProgress = false

    private var physicsComponent: PhysicsComponent? = null
    private var skateboardPhysics: SkateboardPhysics? = null
    @Transient
    private var eventSystem: EventSystem? = null

    override fun start() {
        physicsComponent = gameObject.getComponent<PhysicsComponent>()
        skateboardPhysics = gameObject.getComponent<SkateboardPhysics>()

        // Get event system for subscribing to events
        val scene = sceneManager.currentScene
        eventSystem = scene?.systemManager?.getSystem<EventSystem>()

        // Subscribe to physics events
        eventSystem?.subscribe<Takeoff> { onTakeoff(it) }
        eventSystem?.subscribe<Landing> { onLanding(it) }
    }

    /**
     * Called when the skateboard takes off.
     * Resets rotation accumulators and starts trick detection.
     */
    private fun onTakeoff(event: Takeoff) {
        isInAir = true
        trickInProgress = true

        // Reset rotation accumulators
        accumulatedRotationX = 0f
        accumulatedRotationY = 0f
        accumulatedRotationZ = 0f
        detectedTrick = null
    }

    /**
     * Called when the skateboard lands.
     * Publishes TrickCompleted event if a trick was detected.
     */
    private fun onLanding(event: Landing) {
        if (trickInProgress) {
            // Trick completed - publish event
            val trickName = detectedTrick ?: "Ollie"
            val score = calculateTrickScore()
            val style = calculateStyleMultiplier(event.impactForce)

            eventSystem?.publish(TrickCompleted(trickName, score, style))
        }

        // Reset state
        isInAir = false
        trickInProgress = false
        accumulatedRotationX = 0f
        accumulatedRotationY = 0f
        accumulatedRotationZ = 0f
        detectedTrick = null
    }

    override fun update(dt: Float) {
        if (!isInAir || !trickInProgress) return

        val physicsComponent = physicsComponent ?: return

        // Accumulate rotation while in air
        val angularVelocity = physicsComponent.angularVelocity
        accumulatedRotationX += Math.toDegrees(angularVelocity.x.toDouble()).toFloat() * dt
        accumulatedRotationY += Math.toDegrees(angularVelocity.y.toDouble()).toFloat() * dt
        accumulatedRotationZ += Math.toDegrees(angularVelocity.z.toDouble()).toFloat() * dt

        // Detect trick in progress
        detectTrick()
    }

    /**
     * Analyzes the accumulated rotations around the skateboard's local axes to identify a trick.
     * It checks for specific rotation thresholds for tricks like Kickflips, Heelflips, and Shove-its.
     * The identified trick name is then combined with a stance prefix (e.g., "Fakie", "Switch")
     * based on the PlayerStateManager's current stance.
     * 
     * Publishes [TrickDetected] event when a trick is identified mid-air.
     */
    internal fun detectTrick() {
        val previousTrick = detectedTrick
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

        // Publish TrickDetected event if trick changed
        if (detectedTrick != previousTrick) {
            detectedTrick?.let { trickName ->
                val rotation = Vector3f(accumulatedRotationX, accumulatedRotationY, accumulatedRotationZ)
                eventSystem?.publish(TrickDetected(trickName, rotation))
            }
        }
    }

    /**
     * Calculates trick score based on rotation magnitude.
     */
    private fun calculateTrickScore(): Int {
        val totalRotation = abs(accumulatedRotationX) + abs(accumulatedRotationY) + abs(accumulatedRotationZ)
        return (totalRotation / 360f * 100).toInt()
    }

    /**
     * Calculates style multiplier based on landing quality.
     * Softer landings (lower impact) = higher style.
     */
    private fun calculateStyleMultiplier(impactForce: Float): Float {
        // Style is higher for softer landings (impact < 50 = perfect, > 200 = sloppy)
        return (1.0f - (impactForce / 200f)).coerceIn(0.5f, 1.0f)
    }

    fun getDetectedTrick(): String? {
        return detectedTrick
    }
}
