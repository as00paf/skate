package com.pafoid.skate.engine.gameplay.ik

import com.pafoid.skate.engine.animation.ik.IkRig
import com.pafoid.skate.engine.animation.ik.IkSpace
import com.pafoid.skate.engine.animation.ik.TwoBoneIkConstraint
import com.pafoid.skate.engine.physics3d.PhysicsHit
import kotlinx.serialization.Serializable
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/** One leg: its three bones and the side offset (meters, +left of the heading) of the ground probe. */
@Serializable
data class LegIk(
    val rootBone: String,
    val midBone: String,
    val endBone: String,
    val lateral: Float,
)

/** Tuning for planting feet on the ground; defaults match the Mixamo rig of the bundled character. */
@Serializable
data class FootIkConfig(
    val legs: List<LegIk> = listOf(
        LegIk("mixamorig9_LeftUpLeg", "mixamorig9_LeftLeg", "mixamorig9_LeftFoot", 0.1f),
        LegIk("mixamorig9_RightUpLeg", "mixamorig9_RightLeg", "mixamorig9_RightFoot", -0.1f),
    ),
    /** Height of the foot bone above the sole, so the target keeps the sole on the surface. */
    val ankleHeight: Float = 0.08f,
    /** How far above/below the feet the ground probe starts/ends. */
    val probeUp: Float = 0.4f,
    val probeDown: Float = 0.6f,
    /** Largest ground height difference from the body's own feet that a foot is still moved to. */
    val maxReach: Float = 0.4f,
    /** Seconds to blend the IK weight in or out. */
    val rampSeconds: Float = 0.15f,
)

/** IK target of one foot; [valid] is false when no usable ground was found under it. */
class FootTarget(
    val position: Vector3f = Vector3f(),
    var valid: Boolean = false,
)

object FootPlacement {

    /**
     * Computes where [leg]'s foot should rest. The probe starts beside the body at [feet] (the body's own foot
     * level), offset along the side axis of [yaw], and the target is the surface hit lifted by the ankle height.
     */
    fun compute(
        feet: Vector3f,
        yaw: Float,
        leg: LegIk,
        config: FootIkConfig,
        into: FootTarget = FootTarget(),
        raycast: (from: Vector3f, to: Vector3f) -> PhysicsHit?,
    ): FootTarget {
        val x = feet.x + cos(yaw) * leg.lateral
        val z = feet.z - sin(yaw) * leg.lateral
        val hit = raycast(Vector3f(x, feet.y + config.probeUp, z), Vector3f(x, feet.y - config.probeDown, z))
        if (hit == null || kotlin.math.abs(hit.point.y - feet.y) > config.maxReach) {
            into.valid = false
            return into
        }
        into.position.set(hit.point).add(Vector3f(hit.normal).mul(config.ankleHeight))
        into.valid = true
        return into
    }

    /** Moves [current] toward [target] at a rate that crosses 0..1 in [rampSeconds]. */
    fun weightStep(current: Float, target: Float, dt: Float, rampSeconds: Float): Float {
        if (rampSeconds <= 0f) return target
        val step = dt / rampSeconds
        return if (target > current) minOf(target, current + step) else maxOf(target, current - step)
    }
}

/** Owns the two-bone constraints of a character's legs and feeds them the latest [FootTarget]s. */
class FootIkBinder(private val config: FootIkConfig) {

    private val constraints = ArrayList<TwoBoneIkConstraint>()

    /** Registers the leg constraints on [rig] once, then updates targets and weights. */
    fun update(rig: IkRig, targets: List<FootTarget>, enabled: Boolean, dt: Float) {
        if (constraints.isEmpty()) {
            config.legs.forEach { leg ->
                constraints += rig.addTwoBone(
                    TwoBoneIkConstraint(leg.rootBone, leg.midBone, leg.endBone, space = IkSpace.WORLD)
                )
            }
        }
        constraints.forEachIndexed { i, c ->
            val target = targets.getOrNull(i)
            val active = enabled && target != null && target.valid
            if (active) c.target.set(target!!.position)
            c.weight = FootPlacement.weightStep(c.weight, if (active) 1f else 0f, dt, config.rampSeconds)
        }
    }
}
