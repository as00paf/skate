package com.pafoid.skate.engine.events

import org.joml.Vector3f

/**
 * Base class for all trick events.
 *
 * Trick events are published by [com.pafoid.skate.game.trick.TrickDetector]
 * when tricks are detected, completed, or cancelled.
 */
sealed class TrickEvent(eventName: String) : GameEvent(eventName)

/**
 * Published when a trick is detected in mid-air.
 *
 * @property trickName Name of the detected trick (e.g., "Kickflip", "360 Shove-it")
 * @property rotation Total rotation vector (X, Y, Z in degrees)
 */
data class TrickDetected(
    val trickName: String,
    val rotation: Vector3f
) : TrickEvent("trick.detected")

/**
 * Published when a trick is successfully completed (landed).
 *
 * @property trickName Name of the completed trick
 * @property score Trick score (based on rotation, style, etc.)
 * @property style Style multiplier (0.0-1.0, based on landing quality)
 */
data class TrickCompleted(
    val trickName: String,
    val score: Int,
    val style: Float
) : TrickEvent("trick.completed")

/**
 * Published when a trick is cancelled (failed to land).
 *
 * @property reason Reason for cancellation (e.g., "Didn't rotate enough", "Landed upside down")
 */
data class TrickCancelled(val reason: String) : TrickEvent("trick.cancelled")
