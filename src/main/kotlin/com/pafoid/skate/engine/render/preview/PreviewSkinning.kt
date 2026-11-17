package com.pafoid.skate.engine.render.preview

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f

/**
 * Skin matrices for a pose given as bone-local matrices (what the Animation Editor's `ClipEvaluator.locals` returns),
 * for the offscreen preview (REN-33). Pure, and it never writes the [Skeleton]'s bones: the same skeleton is shared
 * with the scene's skater, whose runtime pose must not be disturbed by an editor preview.
 */
object PreviewSkinning {

    /**
     * Fills [out] (one matrix per bone, at least `skeleton.boneCount`) with `world(bone) * inverseBind(bone)`, where
     * `world` composes [locals] along [parents] (`-1` for a root). [world] is scratch space of the same size.
     * Bones the skeleton doesn't have get identity.
     */
    fun palette(skeleton: Skeleton, parents: IntArray, locals: Array<Matrix4f>, world: Array<Matrix4f>, out: Array<Matrix4f>) {
        val count = minOf(skeleton.boneCount, parents.size, locals.size, world.size, out.size)
        val done = BooleanArray(count)
        fun worldOf(i: Int): Matrix4f {
            if (done[i]) return world[i]
            val p = parents[i]
            if (p in 0 until count && p != i) world[i].set(worldOf(p)).mul(locals[i]) else world[i].set(locals[i])
            done[i] = true
            return world[i]
        }
        for (i in 0 until count) {
            val bone = skeleton.bones[i]
            if (bone == null) out[i].identity() else worldOf(i).mul(bone.inverseBindMatrix, out[i])
        }
        for (i in count until out.size) out[i].identity()
    }
}
