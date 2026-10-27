package com.pafoid.skate.engine.gameplay.ground

import kotlinx.serialization.Serializable
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

/** Ground, slope, step and snapping tuning shared by every character behavior. Lives in the controller config file. */
@Serializable
data class GroundConfig(
    /** Distance from the body origin down to the feet. */
    val footOffset: Float = 0f,
    /** Rays start this far above the feet so slightly sunken feet still find the ground. */
    val probeStartHeight: Float = 0.1f,
    /** Feet within this distance of the ground count as grounded. */
    val groundedDistance: Float = 0.05f,
    /** How far below the feet ground is searched for snapping and slope sampling. */
    val snapDistance: Float = 0.3f,
    /** Horizontal radius of the three extra rays used to estimate the surface normal. */
    val sampleRadius: Float = 0.15f,
    /** Steeper surfaces are not walkable: the character is treated as airborne on them. */
    val maxSlopeDeg: Float = 50f,
    val stepHeight: Float = 0.3f,
    /** How far ahead (m) obstacles are checked for steps. */
    val stepProbeDistance: Float = 0.4f,
    /** Vertical speed cap (m/s) for snapping down and stepping up. */
    val maxSnapSpeed: Float = 6f,
    val snapToGround: Boolean = true,
    val stepUp: Boolean = true,
)

data class GroundInfo(
    /** A surface was found within the search range below the feet. */
    val found: Boolean = false,
    /** Feet-to-surface distance; negative when the feet are below the surface. */
    val distance: Float = Float.MAX_VALUE,
    val normal: Vector3f = Vector3f(0f, 1f, 0f),
    val slopeDeg: Float = 0f,
    val grounded: Boolean = false,
    val walkable: Boolean = true,
) {
    /** True when the character may stand/walk here. */
    val standing: Boolean get() = grounded && walkable
}

/** Casts a ray and returns the hit fraction in 0..1 along it, or null when nothing was hit. */
fun interface RayCaster {
    fun cast(from: Vector3f, to: Vector3f): Float?
}

/** Pure ground/slope/step math; no physics world needed. */
object GroundMath {

    /** Upward unit normal of the plane through three points, or null if they are degenerate. */
    fun normalFromPoints(a: Vector3f, b: Vector3f, c: Vector3f): Vector3f? {
        val n = Vector3f(b).sub(a).cross(Vector3f(c).sub(a))
        val len = n.length()
        if (len.isNaN() || len < 1e-6f) return null
        n.div(len)
        if (n.y < 0f) n.negate()
        return n
    }

    fun slopeDegrees(normal: Vector3f): Float =
        Math.toDegrees(acos(normal.y.coerceIn(-1f, 1f)).toDouble()).toFloat()

    /** Vertical velocity that keeps horizontal motion ([vx], [vz]) parallel to a surface with [normal]. */
    fun verticalOnSlope(vx: Float, vz: Float, normal: Vector3f): Float {
        if (normal.y < 1e-3f) return 0f
        return -(normal.x * vx + normal.z * vz) / normal.y
    }

    /** Downward velocity that closes [gap] meters within one step of [dt], capped at [maxSpeed]. */
    fun snapVelocity(gap: Float, dt: Float, maxSpeed: Float): Float {
        if (gap <= 0f || dt <= 0f) return 0f
        return -min(gap / dt, maxSpeed)
    }

    /** Upward velocity that climbs [height] meters within one step of [dt], capped at [maxSpeed]. */
    fun stepVelocity(height: Float, dt: Float, maxSpeed: Float): Float {
        if (height <= 0f || dt <= 0f) return 0f
        return min(height / dt, maxSpeed)
    }

    fun horizontalLength(x: Float, z: Float) = sqrt(x * x + z * z)

    fun isWalkable(slopeDeg: Float, maxSlopeDeg: Float) = abs(slopeDeg) <= maxSlopeDeg
}

/** Probes the world below a character's feet. Only uses [RayCaster], so it is testable without physics. */
class GroundDetector(private val config: GroundConfig, private val caster: RayCaster) {

    fun probe(feet: Vector3f): GroundInfo {
        val center = castDown(feet.x, feet.y, feet.z) ?: return GroundInfo()

        val r = config.sampleRadius
        var normal = Vector3f(0f, 1f, 0f)
        if (r > 0f) {
            val offsets = listOf(0f to r, -r * 0.866f to -r * 0.5f, r * 0.866f to -r * 0.5f)
            val points = offsets.map { (dx, dz) ->
                castDown(feet.x + dx, feet.y, feet.z + dz)?.let { Vector3f(feet.x + dx, -it, feet.z + dz) }
            }
            if (points.all { it != null }) {
                GroundMath.normalFromPoints(points[0]!!, points[1]!!, points[2]!!)?.let { normal = it }
            }
        }

        val slope = GroundMath.slopeDegrees(normal)
        return GroundInfo(
            found = true,
            distance = center,
            normal = normal,
            slopeDeg = slope,
            grounded = center <= config.groundedDistance,
            walkable = GroundMath.isWalkable(slope, config.maxSlopeDeg),
        )
    }

    /** Height of a step directly ahead along (dirX, dirZ) that the feet can climb, or null if none. */
    fun findStep(feet: Vector3f, dirX: Float, dirZ: Float): Float? {
        val len = GroundMath.horizontalLength(dirX, dirZ)
        if (len < 1e-4f) return null
        val d = config.stepProbeDistance
        val fx = dirX / len * d
        val fz = dirZ / len * d

        if (cast(feet.x, feet.y + LOW_RAY, feet.z, fx, 0f, fz) == null) return null // nothing in the way
        if (cast(feet.x, feet.y + config.stepHeight, feet.z, fx, 0f, fz) != null) return null // a wall, too tall

        val topX = feet.x + fx
        val topZ = feet.z + fz
        val fraction = caster.cast(
            Vector3f(topX, feet.y + config.stepHeight, topZ),
            Vector3f(topX, feet.y, topZ),
        ) ?: return null
        val height = config.stepHeight * (1f - fraction)
        return if (height > MIN_STEP) height else null
    }

    private fun cast(x: Float, y: Float, z: Float, dx: Float, dy: Float, dz: Float): Float? =
        caster.cast(Vector3f(x, y, z), Vector3f(x + dx, y + dy, z + dz))

    /** Feet-to-surface distance for a vertical ray at (x, z), null when it hits nothing. */
    private fun castDown(x: Float, feetY: Float, z: Float): Float? {
        val total = config.probeStartHeight + config.snapDistance
        val fraction = caster.cast(
            Vector3f(x, feetY + config.probeStartHeight, z),
            Vector3f(x, feetY - config.snapDistance, z),
        ) ?: return null
        return fraction * total - config.probeStartHeight
    }

    private companion object {
        const val LOW_RAY = 0.05f
        const val MIN_STEP = 0.01f
    }
}

/** What to do with vertical velocity this frame so the character follows ground, steps and snaps. */
object GroundMotion {

    private const val RISING = 0.1f

    /**
     * Returns the vertical velocity to set, or null to leave physics alone (airborne, jumping or no ground).
     * ([horizontalX], [horizontalZ]) is the horizontal velocity the character moves with.
     */
    fun verticalVelocity(
        config: GroundConfig,
        ground: GroundInfo,
        step: Float?,
        wasGrounded: Boolean,
        currentVy: Float,
        horizontalX: Float,
        horizontalZ: Float,
        jumping: Boolean,
        dt: Float,
    ): Float? {
        if (jumping || currentVy > RISING) return null

        if (config.stepUp && step != null && ground.standing) {
            return GroundMath.stepVelocity(step, dt, config.maxSnapSpeed)
        }
        if (ground.standing) {
            val moving = GroundMath.horizontalLength(horizontalX, horizontalZ) > 1e-3f
            return if (moving) GroundMath.verticalOnSlope(horizontalX, horizontalZ, ground.normal) else null
        }
        if (config.snapToGround && wasGrounded && ground.found && ground.walkable && ground.distance > 0f) {
            return GroundMath.snapVelocity(ground.distance, dt, config.maxSnapSpeed)
        }
        return null
    }
}
