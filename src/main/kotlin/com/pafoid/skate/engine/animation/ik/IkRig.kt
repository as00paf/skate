package com.pafoid.skate.engine.animation.ik

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/** Space a constraint target is expressed in. */
enum class IkSpace { WORLD, MODEL }

/** Keeps a chain's end bone (foot/hand) on [target]. Gameplay updates the target each frame; animation solves it. */
class TwoBoneIkConstraint(
    val rootBone: String,
    val midBone: String,
    val endBone: String,
    val target: Vector3f = Vector3f(),
    /** Optional bend hint (knee/elbow direction), in [space]. */
    var pole: Vector3f? = null,
    var weight: Float = 0f,
    var space: IkSpace = IkSpace.WORLD,
    /** Optional orientation for the end bone (e.g. foot flat on the board), in [space]. */
    var endRotation: Quaternionf? = null,
    var endRotationWeight: Float = 1f,
)

/** Turns [bone] so its local [axis] faces [target]. */
class LookAtConstraint(
    val bone: String,
    val target: Vector3f = Vector3f(),
    var weight: Float = 0f,
    var space: IkSpace = IkSpace.WORLD,
    val axis: Vector3f = Vector3f(0f, 0f, 1f),
    var maxAngleDegrees: Float = 70f,
)

/** Runtime IK setup for one character. Not serialized; gameplay code registers constraints at spawn. */
class IkRig {
    val twoBone = mutableListOf<TwoBoneIkConstraint>()
    val lookAt = mutableListOf<LookAtConstraint>()

    /** Pre-IK local pose, kept so a non-re-evaluated pose is not corrected twice. */
    internal var basePose: Array<Matrix4f>? = null

    val isActive: Boolean
        get() = twoBone.any { it.weight > 0f } || lookAt.any { it.weight > 0f }

    fun addTwoBone(constraint: TwoBoneIkConstraint) = constraint.also { twoBone += it }
    fun addLookAt(constraint: LookAtConstraint) = constraint.also { lookAt += it }
}
