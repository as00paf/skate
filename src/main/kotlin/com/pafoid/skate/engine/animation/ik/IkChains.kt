package com.pafoid.skate.engine.animation.ik

import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.components.IkChainDef
import com.pafoid.skate.engine.ecs.components.IkChainKind
import com.pafoid.skate.engine.ecs.components.IkSetup
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/** A chain's bones on one rig, or why it cannot be set up there. */
sealed interface ResolvedChain {
    data class Bones(val names: List<String>) : ResolvedChain
    data class Problem(val reason: String) : ResolvedChain
}

/** Finds the bones of [IkChainDef]s on a skeleton (ANI-28 Q2: humanoid slots, with a bone-name override). Pure. */
object IkChains {
    /** Bones the kind needs: LIMB exactly 3, LOOK at least 1. */
    fun bonesNeeded(kind: IkChainKind) = if (kind == IkChainKind.LIMB) 3 else 1

    fun resolve(def: IkChainDef, skeleton: Skeleton): ResolvedChain {
        val names = if (def.boneNames.isNotEmpty()) def.boneNames else {
            if (def.slots.isEmpty()) return ResolvedChain.Problem("'${def.name}' has no bones")
            val all = (0 until skeleton.boneCount).mapNotNull { skeleton.bones[it]?.name }
            def.slots.map { slot -> all.firstOrNull { HumanoidBone.fromName(it) == slot } ?: return ResolvedChain.Problem("'${def.name}': no ${slot.name} bone on this rig") }
        }
        val needed = bonesNeeded(def.kind)
        if (def.kind == IkChainKind.LIMB && names.size != needed || names.size < needed) {
            return ResolvedChain.Problem("'${def.name}': a ${def.kind.name} chain needs ${if (def.kind == IkChainKind.LIMB) "3" else "1 or more"} bones, has ${names.size}")
        }
        names.firstOrNull { skeleton.getBoneByName(it) == null }?.let { return ResolvedChain.Problem("'${def.name}': no bone named '$it'") }
        if (def.kind == IkChainKind.LIMB) {
            val parents = IkPass.parentsOf(skeleton)
            val (root, mid, end) = names.map { skeleton.getBoneByName(it)!!.index }
            if (parents[mid] != root || parents[end] != mid) return ResolvedChain.Problem("'${def.name}': ${names.joinToString(" > ")} is not a parent-child chain")
        }
        return ResolvedChain.Bones(names)
    }
}

/**
 * Keeps the constraints of one character's [IkSetup] in its [IkRig] (ANI-28 slice b): builds them when the chains or
 * the rig change (leaving constraints added by code alone), then every frame picks each chain's goal or target, and
 * ramps its weight. Pure: the caller resolves target names to world matrices.
 */
class IkSetupBinder {
    private class Bound(val def: IkChainDef, val limb: TwoBoneIkConstraint?, val look: LookAtConstraint?) {
        var weight = 0f
        var hasTarget = false
    }

    private var builtFor: Pair<List<IkChainDef>, Skeleton>? = null
    private val bound = ArrayList<Bound>()

    /**
     * Updates [rig] from [setup] on [skeleton]. [targets] gives the world matrix of an object by name (null when there
     * is none). Returns the chains' problems (also stored in [IkSetup.problems]).
     */
    fun update(setup: IkSetup, skeleton: Skeleton, rig: IkRig, dt: Float, targets: (String) -> Matrix4f?): List<String> {
        if (builtFor?.first != setup.chains || builtFor?.second !== skeleton) rebuild(setup, skeleton, rig)
        val position = Vector3f()
        for (b in bound) {
            val def = b.def
            val goal = def.goal.takeIf { it.isNotBlank() }?.let { setup.goals[it] }
            val target = if (goal == null) def.target.takeIf { it.isNotBlank() }?.let(targets) else null
            val rotation: Quaternionf? = when {
                goal != null -> goal.rotation
                target != null -> target.getUnnormalizedRotation(Quaternionf()).normalize()
                else -> null
            }
            when {
                goal != null -> position.set(goal.position)
                target != null -> target.getTranslation(position)
            }
            val found = goal != null || target != null
            // Without a target the chain keeps its last one while it blends out, so the limb does not jump.
            if (found) {
                b.hasTarget = true
                b.limb?.let { c ->
                    c.target.set(position)
                    c.endRotation = if (def.alignRotation) rotation else null
                    c.pole = def.pole.takeIf { it.isNotBlank() }?.let(targets)?.getTranslation(Vector3f())
                }
                b.look?.target?.set(position)
            }
            val wanted = if (setup.enabled && def.enabled && found && b.hasTarget) def.weight * (goal?.weight ?: 1f) else 0f
            b.weight = ramp(b.weight, wanted.coerceIn(0f, 1f), dt, def.rampSeconds)
            b.limb?.weight = b.weight
            b.look?.weight = b.weight
        }
        return setup.problems
    }

    /** Removes this binder's constraints from [rig] (the setup was removed). */
    fun detach(rig: IkRig) {
        bound.forEach { b -> b.limb?.let(rig.twoBone::remove); b.look?.let(rig.lookAt::remove) }
        bound.clear()
        builtFor = null
    }

    private fun rebuild(setup: IkSetup, skeleton: Skeleton, rig: IkRig) {
        // Carry the ramped weights over by chain name, so editing one chain does not pop the others.
        val previous = bound.associate { it.def.name to it.weight }
        detach(rig)
        val problems = ArrayList<String>()
        for (def in setup.chains.toList()) {
            when (val r = IkChains.resolve(def, skeleton)) {
                is ResolvedChain.Problem -> problems += r.reason
                is ResolvedChain.Bones -> bound += when (def.kind) {
                    IkChainKind.LIMB -> Bound(def, rig.addTwoBone(TwoBoneIkConstraint(r.names[0], r.names[1], r.names[2], space = IkSpace.WORLD)), null)
                    IkChainKind.LOOK -> Bound(def, null, rig.addLookAt(LookAtConstraint(r.names.last(), space = IkSpace.WORLD, axis = Vector3f(def.axis), maxAngleDegrees = def.maxAngleDegrees)))
                }.also { it.weight = previous[def.name] ?: 0f }
            }
        }
        setup.problems = problems
        builtFor = setup.chains.toList() to skeleton
    }

    companion object {
        /** Moves [current] toward [wanted] at a rate that crosses 0..1 in [seconds]. */
        fun ramp(current: Float, wanted: Float, dt: Float, seconds: Float): Float {
            if (seconds <= 0f) return wanted
            val step = dt / seconds
            return if (wanted > current) minOf(wanted, current + step) else maxOf(wanted, current - step)
        }
    }
}
