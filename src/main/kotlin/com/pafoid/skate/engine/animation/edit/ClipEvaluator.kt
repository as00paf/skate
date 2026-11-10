package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/** Bone hierarchy as flat arrays (parent indices, bind locals, names), read once from a [Skeleton]. */
class ClipRig(skeleton: Skeleton) {
    val boneCount = skeleton.boneCount
    val names = Array(boneCount) { "" }
    val parents = IntArray(boneCount) { -1 }
    val bindLocals = Array(boneCount) { Matrix4f() }

    init {
        fun walk(bone: Bone, parent: Int) {
            if (bone.index in 0 until boneCount) {
                names[bone.index] = bone.name
                parents[bone.index] = parent
                bindLocals[bone.index].set(bone.bindLocalTransform)
            }
            bone.children.forEach { walk(it, bone.index) }
        }
        walk(skeleton.rootBone, -1)
    }

    fun indexOf(name: String): Int = names.indexOf(name).also { require(it >= 0) { "Bone '$name' is not in the rig" } }
}

/**
 * Pure clip evaluation on a [ClipRig], matching the runtime `Animation.update` composition:
 * `local = bindLocal * TRS(channels)`, with identity for missing channels.
 */
class ClipEvaluator(clip: SkClip, private val rig: ClipRig) {
    private val samplers: Map<Int, Map<AnimationPath, AnimationSampler>> = clip.tracks
        .filter { it.keys.isNotEmpty() && it.bone in rig.names }
        .groupBy { rig.names.indexOf(it.bone) }
        .mapValues { (_, tracks) -> tracks.associate { it.path to SkAnimFormat.toSampler(it) } }

    fun locals(time: Float): Array<Matrix4f> {
        val pos = Vector3f(); val rot = Quaternionf(); val scale = Vector3f()
        return Array(rig.boneCount) { i ->
            val bySampler = samplers[i] ?: return@Array Matrix4f(rig.bindLocals[i])
            pos.zero(); rot.identity(); scale.set(1f)
            bySampler[AnimationPath.TRANSLATION]?.sampleVector3f(time, pos)
            bySampler[AnimationPath.ROTATION]?.sampleQuaternionf(time, rot)
            bySampler[AnimationPath.SCALE]?.sampleVector3f(time, scale)
            Matrix4f(rig.bindLocals[i]).mul(Matrix4f().translationRotateScale(pos, rot, scale))
        }
    }

    /** Channel-space rotation (`bindLocal⁻¹ * local`) of bone [index] for a solved [local] matrix. */
    fun channelRotation(index: Int, local: Matrix4f): Quaternionf =
        Matrix4f(rig.bindLocals[index]).invert().mul(local).getUnnormalizedRotation(Quaternionf()).normalize()
}
