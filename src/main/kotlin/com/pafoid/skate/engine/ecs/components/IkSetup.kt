package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Quaternionf
import org.joml.Vector3f

/** What a chain of an [IkSetup] solves (ANI-28 §4.1). Slice c adds HIPS and CHAIN, and spreads LOOK over several bones. */
@Serializable
enum class IkChainKind {
    /** Two-bone limb (upper, lower, end: a leg or an arm) reaching a target, bent toward an optional pole. */
    LIMB,
    /** Turns a bone (the head) toward a target, within [IkChainDef.maxAngleDegrees]. */
    LOOK,
}

/**
 * One chain of an [IkSetup]. Bones come from humanoid [slots] (any humanoid rig), or from [boneNames] for other rigs
 * (same order). The chain reaches the gameplay [goal] when one is set, else the object named [target] (searched in the
 * character's hierarchy first, then the scene), usually an [IkTarget].
 */
@Serializable
data class IkChainDef(
    val name: String,
    val kind: IkChainKind = IkChainKind.LIMB,
    /** LIMB: upper, lower, end (e.g. LEFT_UPPER_LEG, LEFT_LOWER_LEG, LEFT_FOOT). LOOK: the bone that turns (HEAD). */
    val slots: List<HumanoidBone> = emptyList(),
    /** Bone names that replace [slots] (same order) on rigs without humanoid names; empty uses [slots]. */
    val boneNames: List<String> = emptyList(),
    /** Name of the target object. */
    val target: String = "",
    /** Name of the object the knee or elbow bends toward (LIMB); empty bends naturally. */
    val pole: String = "",
    /** Named goal gameplay sets ([IkSetup.setGoal]); it wins over [target] while set. */
    val goal: String = "",
    val weight: Float = 1f,
    /** Seconds to blend the chain in or out when its target appears, disappears or its weight changes. */
    val rampSeconds: Float = 0.15f,
    /** LIMB: also turn the end bone (foot, hand) to the target's rotation. */
    val alignRotation: Boolean = false,
    /** LOOK: largest turn, in degrees. */
    val maxAngleDegrees: Float = 70f,
    /** LOOK: the bone's local axis that points at the target. */
    @Contextual val axis: Vector3f = Vector3f(0f, 0f, 1f),
    val enabled: Boolean = true,
)

/** A target set from code for one frame or more ([IkSetup.setGoal]). */
class IkGoal(val position: Vector3f = Vector3f(), var rotation: Quaternionf? = null, var weight: Float = 1f)

/**
 * The IK of a character, as data (ANI-28 §4.1): its chains, saved with the scene and edited in the inspector.
 * `IkSetupSystem` turns them into the [Animator]'s IK constraints every frame (none while [enabled] is off).
 * Gameplay sets named goals
 * (board sockets, rail grabs, the landing point) without knowing the chains.
 */
@Serializable
data class IkSetup(
    val chains: MutableList<IkChainDef> = mutableListOf(),
) : Component() {
    /** Goals set by gameplay this session, by name (not saved). */
    @Transient
    val goals = HashMap<String, IkGoal>()

    /** Chains that could not be set up on this character's rig (missing bones), for the inspector. Not saved. */
    @Transient
    var problems: List<String> = emptyList()

    /** Sets (or moves) the goal [name]; chains whose `goal` is [name] reach it with [weight]. */
    fun setGoal(name: String, position: Vector3f, rotation: Quaternionf? = null, weight: Float = 1f) {
        val goal = goals.getOrPut(name) { IkGoal() }
        goal.position.set(position)
        goal.rotation = rotation?.let { Quaternionf(it) }
        goal.weight = weight
    }

    /** Removes the goal [name]: its chains go back to their target object, or blend out. */
    fun clearGoal(name: String) { goals.remove(name) }
}
