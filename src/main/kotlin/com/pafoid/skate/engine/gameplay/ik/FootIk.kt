package com.pafoid.skate.engine.gameplay.ik

import com.pafoid.skate.engine.animation.ik.IkRig
import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.animation.ik.IkSpace
import com.pafoid.skate.engine.animation.ik.TwoBoneIkConstraint
import com.pafoid.skate.engine.physics3d.PhysicsHit
import kotlinx.serialization.Serializable
import org.joml.Vector3f
import java.util.logging.Logger
import kotlin.math.cos
import kotlin.math.sin

/**
 * One leg: its three bones and the side offset (meters, +left of the heading) of the ground probe. A bone is a
 * [HumanoidBone] slot name (`LEFT_UPPER_LEG`), resolved per rig, or a raw bone name (see [FootIkLegs.resolve]).
 */
@Serializable
data class LegIk(
    val rootBone: String,
    val midBone: String,
    val endBone: String,
    val lateral: Float,
)

/** Tuning for planting feet on the ground; the default legs are humanoid slots, so they fit any humanoid rig. */
@Serializable
data class FootIkConfig(
    /** Off by default until the player rewrite exposes foot IK in the inspector (ANI-34). */
    val enabled: Boolean = false,
    val legs: List<LegIk> = listOf(
        LegIk(HumanoidBone.LEFT_UPPER_LEG.name, HumanoidBone.LEFT_LOWER_LEG.name, HumanoidBone.LEFT_FOOT.name, 0.1f),
        LegIk(HumanoidBone.RIGHT_UPPER_LEG.name, HumanoidBone.RIGHT_LOWER_LEG.name, HumanoidBone.RIGHT_FOOT.name, -0.1f),
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

/** Binds [LegIk] bone names to a rig (GPL-38, ANI-28 F1). */
object FootIkLegs {

    /**
     * The rig's bone for [name]: the bone with that exact name; else, for a [HumanoidBone] slot name or a name that
     * maps to a slot (`mixamorig9_LeftUpLeg` from older configs, whose prefix the importer strips), the first rig bone
     * in that slot. Null when the rig has none.
     */
    fun boneName(skeleton: Skeleton, name: String): String? {
        skeleton.getBoneByName(name)?.let { return it.name }
        val slot = HumanoidBone.entries.firstOrNull { it.name == name } ?: HumanoidBone.fromName(name) ?: return null
        return skeleton.getAllBones().firstOrNull { HumanoidBone.fromName(it.name) == slot }?.name
    }

    /** [leg] with its three bones resolved on [skeleton], or null when any is missing. */
    fun resolve(leg: LegIk, skeleton: Skeleton): LegIk? {
        val root = boneName(skeleton, leg.rootBone) ?: return null
        val mid = boneName(skeleton, leg.midBone) ?: return null
        val end = boneName(skeleton, leg.endBone) ?: return null
        return leg.copy(rootBone = root, midBone = mid, endBone = end)
    }
}

/**
 * Owns the two-bone constraints of a character's legs and feeds them the latest [FootTarget]s. The legs are bound
 * to the rig on the first update with a skeleton; a leg whose bones the rig lacks gets no constraint and is
 * reported once (it used to be skipped silently by `IkPass`, ANI-28 F1).
 */
class FootIkBinder(private val config: FootIkConfig) {

    /** One per [FootIkConfig.legs] entry, null for a leg that did not resolve; empty until bound. */
    private val constraints = ArrayList<TwoBoneIkConstraint?>()
    private var bound = false

    /** Leg bones as found on the rig, null for unresolved legs; empty until bound. */
    val resolvedLegs = ArrayList<LegIk?>()

    /** Registers the leg constraints on [rig] once [skeleton] is known, then updates targets and weights. */
    fun update(rig: IkRig, targets: List<FootTarget>, enabled: Boolean, dt: Float, skeleton: Skeleton?) {
        if (!bound) {
            if (skeleton == null) return
            bind(rig, skeleton)
        }
        constraints.forEachIndexed { i, c ->
            if (c == null) return@forEachIndexed
            val target = targets.getOrNull(i)
            val active = enabled && target != null && target.valid
            if (active) c.target.set(target!!.position)
            c.weight = FootPlacement.weightStep(c.weight, if (active) 1f else 0f, dt, config.rampSeconds)
        }
    }

    private fun bind(rig: IkRig, skeleton: Skeleton) {
        bound = true
        config.legs.forEach { leg ->
            val resolved = FootIkLegs.resolve(leg, skeleton)
            resolvedLegs += resolved
            if (resolved == null) {
                logger.warning("Foot IK: leg ${leg.rootBone} > ${leg.midBone} > ${leg.endBone} not found in the rig; that foot is not planted")
            }
            constraints += resolved?.let {
                rig.addTwoBone(TwoBoneIkConstraint(it.rootBone, it.midBone, it.endBone, space = IkSpace.WORLD))
            }
        }
    }

    private companion object {
        val logger: Logger = Logger.getLogger("FootIk")
    }
}
