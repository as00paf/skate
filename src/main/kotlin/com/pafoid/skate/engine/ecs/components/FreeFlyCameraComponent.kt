package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.utils.CameraMotion
import kotlinx.serialization.Serializable
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Free-fly control for a camera while the scene runs (editor Play or the game), like the editor camera: W/S forward
 * and back along the view direction, A/D strafe, E/Q up and down (world), Shift for [fastMultiplier]; hold the right
 * mouse button and drag to look. Frame-rate independent ([CameraMotion]); `CameraControlSystem` reads the input.
 * Uses the camera rotation convention of [Transform.lookAt] (+X pitch looks down, +Y yaw turns right).
 */
@Serializable
class FreeFlyCameraComponent(
    /** World units per second. */
    var speed: Float = 8f,
    var fastMultiplier: Float = 4f,
    /** Degrees per mouse pixel (same as the editor camera's mouse look). */
    var lookSensitivity: Float = 0.1f,
) : Component() {

    companion object {
        const val MAX_PITCH = 89f

        /** Unit view direction for Euler [rotation] in degrees (what the camera manager renders towards). */
        fun forward(rotation: Vector3f): Vector3f {
            val p = Math.toRadians(rotation.x.toDouble())
            val y = Math.toRadians(rotation.y.toDouble())
            return Vector3f((cos(p) * sin(y)).toFloat(), (-sin(p)).toFloat(), (-cos(p) * cos(y)).toFloat())
        }

        /** Unit horizontal right vector for Euler [rotation] in degrees. */
        fun right(rotation: Vector3f): Vector3f {
            val y = Math.toRadians(rotation.y.toDouble())
            return Vector3f(cos(y).toFloat(), 0f, sin(y).toFloat())
        }
    }

    /**
     * Moves [transform] for one frame of [dt] seconds; [right], [forward] and [up] are -1..1 inputs.
     * Returns true if it moved.
     */
    fun fly(transform: Transform, right: Float, forward: Float, up: Float, fast: Boolean, dt: Float): Boolean {
        if (right == 0f && forward == 0f && up == 0f) return false
        val distance = CameraMotion.step(if (fast) speed * fastMultiplier else speed, dt)
        val move = Companion.forward(transform.rotation).mul(forward)
            .add(Companion.right(transform.rotation).mul(right))
            .add(0f, up, 0f)
        if (move.lengthSquared() > 1f) move.normalize()
        transform.translation.add(move.mul(distance))
        return true
    }

    /** Turns [transform] by a mouse drag of [dx], [dy] pixels (right turns right, down looks down). */
    fun look(transform: Transform, dx: Float, dy: Float): Boolean {
        if (dx == 0f && dy == 0f) return false
        transform.rotation.y += dx * lookSensitivity
        transform.rotation.x = (transform.rotation.x + dy * lookSensitivity).coerceIn(-MAX_PITCH, MAX_PITCH)
        return true
    }
}
